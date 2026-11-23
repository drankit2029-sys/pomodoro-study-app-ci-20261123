package com.studyapp.pomodoro.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.studyapp.pomodoro.Phase
import com.studyapp.pomodoro.PomodoroNotifications
import com.studyapp.pomodoro.PomodoroTimerEngine
import com.studyapp.pomodoro.PomodoroUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PomodoroTimerService : Service() {

    private val engine by lazy { PomodoroTimerEngine.getInstance(this) }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var stateJob: Job? = null
    private var foregroundStarted = false
    private var startCommandHandled = false
    private var handlingCommand = false
    private var lastSignature: NotificationSignature? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundFailed = false

    override fun onCreate() {
        super.onCreate()
        PomodoroNotifications.createChannels(this)
        // Satisfy the startForegroundService() contract immediately. A very short session
        // (or a test that completes a phase instantly) can stop the service before
        // onStartCommand is delivered; without this, Android throws
        // ForegroundServiceDidNotStartInTimeException and kills the process.
        promoteToForeground(engine.state.value)
        stateJob = serviceScope.launch {
            engine.state.collect { state ->
                if (!startCommandHandled || handlingCommand) return@collect
                // Drop stale emissions so a queued running sample cannot overwrite a
                // newer paused notification after the service has been stopped.
                if (state != engine.state.value) return@collect
                syncForegroundState(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handlingCommand = true
        try {
            when (intent?.action) {
                ACTION_TOGGLE -> engine.toggle(shouldStartService = false)
                ACTION_RESET -> engine.reset(shouldStopService = false)
                ACTION_START_NEXT -> engine.startNext(shouldStartService = false)
                ACTION_KEEP_FLOWING -> engine.startFlowOvertime(shouldStartService = false)
                ACTION_FINISH_FLOW -> engine.finishFlow(shouldStartService = false)
                ACTION_STOP_ALARM -> {
                    engine.stopAlarm()
                    PomodoroNotifications.cancelCompletion(this)
                }
                ACTION_START, null -> engine.ensureTicker()
            }

            val state = engine.state.value
            val startedAsForeground =
                intent?.getBooleanExtra(EXTRA_STARTED_AS_FOREGROUND, false) == true
            // Always satisfy Android's startForegroundService() contract on every foreground
            // start command before syncForegroundState potentially calls stopForeground/stopSelf.
            if (startedAsForeground && !state.isRunning) {
                ensureForeground(state)
            }
            startCommandHandled = true
            syncForegroundState(state, force = true)
        } finally {
            handlingCommand = false
        }
        return START_STICKY
    }

    private fun syncForegroundState(state: PomodoroUiState, force: Boolean = false) {
        if (foregroundFailed) return
        if (state.isRunning) {
            val signature = NotificationSignature.from(state)
            if (force || !foregroundStarted || signature != lastSignature) {
                lastSignature = signature
                updateForeground(state)
            }
            return
        }

        if (foregroundStarted) {
            stopForeground(
                if (state.isMidSession) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE,
            )
            foregroundStarted = false
        }
        releaseWakeLock()

        val signature = NotificationSignature.from(state)
        if (state.isMidSession) {
            if (force || signature != lastSignature) {
                lastSignature = signature
                PomodoroNotifications.showPausedOngoing(this, state)
            }
        } else {
            lastSignature = null
            PomodoroNotifications.cancelOngoing(this)
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stateJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        if (foregroundStarted) {
            val paused = engine.state.value.isMidSession
            stopForeground(
                if (paused) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE,
            )
            foregroundStarted = false
        }
        super.onDestroy()
    }

    private fun ensureForeground(state: PomodoroUiState) {
        if (!promoteToForeground(state)) {
            // A service started with startForegroundService() that never promotes is killed
            // with ForegroundServiceDidNotStartInTimeException. Stop cleanly instead and let
            // the engine keep the timer alive in-process; posting a "paused" copy here would
            // also mislabel a running session.
            foregroundFailed = true
            Log.e(LOG_TAG, "startForeground was rejected; stopping the service")
            stopSelf()
            return
        }
        lastSignature = NotificationSignature.from(state)
        updateWakeLock(state)
    }

    private fun updateForeground(state: PomodoroUiState) {
        if (!promoteToForeground(state)) {
            foregroundFailed = true
            Log.e(LOG_TAG, "Could not update the foreground notification; stopping the service")
            stopSelf()
            return
        }
        updateWakeLock(state)
    }

    private fun promoteToForeground(state: PomodoroUiState): Boolean {
        val notification = PomodoroNotifications.buildOngoing(this, state)
        val promoted = runCatching {
            ServiceCompat.startForeground(
                this,
                PomodoroNotifications.ONGOING_NOTIFICATION_ID,
                notification,
                foregroundServiceType(),
            )
            true
        }.getOrDefault(false)
        if (promoted) foregroundStarted = true
        return promoted
    }

    private fun updateWakeLock(state: PomodoroUiState) {
        if (state.isRunning) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    private fun foregroundServiceType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Pomodoro::Timer",
        ).apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wakeLock = null
    }

    private data class NotificationSignature(
        val phase: Phase,
        val isRunning: Boolean,
        val isFlowOvertime: Boolean,
        val displaySeconds: Int,
        val totalSeconds: Int,
        val adaptiveNextBreakMinutes: Int,
        val hasCompletion: Boolean,
    ) {
        companion object {
            fun from(state: PomodoroUiState): NotificationSignature = NotificationSignature(
                phase = state.phase,
                isRunning = state.isRunning,
                isFlowOvertime = state.isFlowOvertime,
                displaySeconds = if (state.isFlowOvertime) {
                    state.flowOvertimeSeconds
                } else {
                    state.remaining
                },
                totalSeconds = state.totalSeconds,
                adaptiveNextBreakMinutes = state.adaptiveNextBreakMinutes,
                hasCompletion = state.completion != null,
            )
        }
    }

    companion object {
        private const val LOG_TAG = "PomodoroTimerService"

        const val ACTION_START = "com.studyapp.pomodoro.action.START"
        const val ACTION_TOGGLE = "com.studyapp.pomodoro.action.TOGGLE"
        const val ACTION_RESET = "com.studyapp.pomodoro.action.RESET"
        const val ACTION_START_NEXT = "com.studyapp.pomodoro.action.START_NEXT"
        const val ACTION_KEEP_FLOWING = "com.studyapp.pomodoro.action.KEEP_FLOWING"
        const val ACTION_FINISH_FLOW = "com.studyapp.pomodoro.action.FINISH_FLOW"
        const val ACTION_STOP_ALARM = "com.studyapp.pomodoro.action.STOP_ALARM"

        const val EXTRA_STARTED_AS_FOREGROUND =
            "com.studyapp.pomodoro.extra.STARTED_AS_FOREGROUND"

        private const val MAX_WAKE_LOCK_MILLIS = 2L * 60L * 60L * 1000L
        private const val STOP_GRACE_MILLIS = 3_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, PomodoroTimerService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_STARTED_AS_FOREGROUND, true),
            )
        }

        fun stop(context: Context) {
            // A startForegroundService() call is asynchronous. If a session finishes or is
            // reset immediately after starting, stopping the service in the same main-thread
            // turn can cancel the start before onStartCommand runs, and Android then throws
            // ForegroundServiceDidNotStartInTimeException. Give the service a short grace
            // window to promote itself, then stop it.
            val appContext = context.applicationContext
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    runCatching {
                        appContext.stopService(
                            Intent(appContext, PomodoroTimerService::class.java),
                        )
                    }
                },
                STOP_GRACE_MILLIS,
            )
        }
    }
}
