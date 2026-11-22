package com.studyapp.pomodoro.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.studyapp.pomodoro.AmbientSoundscape
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Plays built-in procedural ambient focus soundscapes via [AudioTrack] or user-selected
 * custom audio / inbuilt device tone tracks via [MediaPlayer].
 *
 * When Music is toggled ON ([isFocusRunning] / continuous mode), audio loops continuously
 * without ever expiring after the preview window. The 4-second preview window is used only
 * when the user explicitly taps the PREVIEW button while Music is toggled OFF.
 */
object AmbientSoundPlayer {

    private const val SAMPLE_RATE = 22_050
    private const val BUFFER_SAMPLES = 2_048
    private const val DEFAULT_PREVIEW_DURATION_MS = 4_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var previewStopRunnable: Runnable? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var activeSoundscape: AmbientSoundscape = AmbientSoundscape.OFF

    @Volatile
    private var activeCustomUri: String = ""

    @Volatile
    private var activeVolume: Float = 0.65f

    @Volatile
    private var focusLoopActive: Boolean = false

    @Volatile
    private var previewUntilElapsed: Long = 0L

    @Volatile
    private var sessionGeneration: Long = 0L

    @Volatile
    private var workerThread: Thread? = null

    @Volatile
    private var currentTrack: AudioTrack? = null

    private var customMediaPlayer: MediaPlayer? = null
    private var playingCustomUri: String = ""

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus: Boolean = false

    /**
     * Ambient audio is background media: it must duck/stop for calls and for other apps that
     * take audio focus, otherwise it talks over the user's music and keeps playing during a
     * phone call.
     */
    private fun ensureAudioFocusLocked(): Boolean {
        if (hasAudioFocus) return true
        val ctx = appContext ?: return false
        val audioManager = ctx.getSystemService(AudioManager::class.java) ?: return false
        val request = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                ) {
                    stop()
                }
            }
            .build()
            .also { audioFocusRequest = it }
        val granted = runCatching {
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }.getOrDefault(false)
        hasAudioFocus = granted
        return granted
    }

    private fun abandonAudioFocusLocked() {
        if (!hasAudioFocus) return
        hasAudioFocus = false
        val ctx = appContext ?: return
        val audioManager = ctx.getSystemService(AudioManager::class.java) ?: return
        audioFocusRequest?.let { request ->
            runCatching { audioManager.abandonAudioFocusRequest(request) }
        }
    }

    @Synchronized
    fun syncPlayback(
        soundscape: AmbientSoundscape,
        volume: Float,
        isFocusRunning: Boolean,
        isAlarmRinging: Boolean = false,
        context: Context? = null,
        customUri: String = "",
    ) {
        if (context != null) {
            appContext = context.applicationContext
        }
        activeSoundscape = soundscape
        activeCustomUri = customUri
        activeVolume = volume.coerceIn(0f, 1f)
        focusLoopActive = isFocusRunning && !isAlarmRinging && soundscape != AmbientSoundscape.OFF

        if (isAlarmRinging || soundscape == AmbientSoundscape.OFF) {
            previewUntilElapsed = 0L
            stop()
            return
        }

        if (focusLoopActive) {
            // Clear any preview cutoff timer when continuous playback is active
            previewUntilElapsed = 0L
            cancelCustomPreviewStopLocked()
        }

        val now = SystemClock.elapsedRealtime()
        val shouldBePlaying = focusLoopActive || now < previewUntilElapsed
        if (!shouldBePlaying) {
            stop()
            return
        }
        if (!ensureAudioFocusLocked()) {
            stop()
            return
        }

        if (soundscape == AmbientSoundscape.CUSTOM && customUri.isNotBlank()) {
            stopSynthesisWorkerLocked()
            if (ensureCustomMediaPlayerLocked(customUri)) {
                if (!focusLoopActive && now < previewUntilElapsed) {
                    scheduleCustomPreviewStopLocked(previewUntilElapsed - now)
                } else {
                    cancelCustomPreviewStopLocked()
                }
                return
            }
            activeSoundscape = AmbientSoundscape.SOFT_RAIN
        } else {
            stopCustomMediaPlayerLocked()
        }

        ensureWorkerRunningLocked()
    }

    /**
     * Plays a short live audition preview ([durationMs], default 4 seconds) when the user
     * taps PREVIEW while Music is off. If continuous playback ([focusLoopActive]) is already
     * active, keeps continuous playback running without setting a cutoff timer.
     */
    @Synchronized
    fun playPreview(
        soundscape: AmbientSoundscape,
        volume: Float,
        durationMs: Long = DEFAULT_PREVIEW_DURATION_MS,
        context: Context? = null,
        customUri: String = "",
    ) {
        if (context != null) {
            appContext = context.applicationContext
        }
        activeSoundscape = soundscape
        activeCustomUri = customUri
        activeVolume = volume.coerceIn(0f, 1f)
        if (soundscape == AmbientSoundscape.OFF) {
            previewUntilElapsed = 0L
            stop()
            return
        }
        val safeDuration = durationMs.coerceAtLeast(1_000L)
        if (!ensureAudioFocusLocked()) {
            stop()
            return
        }
        if (!focusLoopActive) {
            previewUntilElapsed = SystemClock.elapsedRealtime() + safeDuration
        }

        if (soundscape == AmbientSoundscape.CUSTOM && customUri.isNotBlank()) {
            stopSynthesisWorkerLocked()
            if (ensureCustomMediaPlayerLocked(customUri)) {
                if (!focusLoopActive) {
                    scheduleCustomPreviewStopLocked(safeDuration)
                } else {
                    cancelCustomPreviewStopLocked()
                }
                return
            }
            activeSoundscape = AmbientSoundscape.SOFT_RAIN
        } else {
            stopCustomMediaPlayerLocked()
        }

        ensureWorkerRunningLocked()
    }

    @Synchronized
    private fun scheduleCustomPreviewStopLocked(delayMs: Long) {
        cancelCustomPreviewStopLocked()
        val runnable = Runnable {
            synchronized(this@AmbientSoundPlayer) {
                val now = SystemClock.elapsedRealtime()
                if (!focusLoopActive && now >= previewUntilElapsed) {
                    stopCustomMediaPlayerLocked()
                }
            }
        }
        previewStopRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs.coerceAtLeast(100L))
    }

    @Synchronized
    private fun cancelCustomPreviewStopLocked() {
        previewStopRunnable?.let(mainHandler::removeCallbacks)
        previewStopRunnable = null
    }

    @Synchronized
    private fun ensureCustomMediaPlayerLocked(uriString: String): Boolean {
        val ctx = appContext ?: return false
        val existing = customMediaPlayer
        if (existing != null && playingCustomUri == uriString) {
            return runCatching {
                existing.setVolume(activeVolume, activeVolume)
                if (!existing.isPlaying) {
                    existing.start()
                }
                true
            }.getOrElse {
                stopCustomMediaPlayerLocked()
                false
            }
        }

        stopCustomMediaPlayerLocked()
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val player = MediaPlayer()
        return runCatching {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            player.setDataSource(ctx, uri)
            player.isLooping = true
            player.setVolume(activeVolume, activeVolume)
            player.setOnErrorListener { _, _, _ ->
                synchronized(this@AmbientSoundPlayer) {
                    stopCustomMediaPlayerLocked()
                }
                true
            }
            player.prepare()
            player.start()
            customMediaPlayer = player
            playingCustomUri = uriString
            true
        }.getOrElse {
            runCatching { player.release() }
            false
        }
    }

    @Synchronized
    private fun stopCustomMediaPlayerLocked() {
        cancelCustomPreviewStopLocked()
        playingCustomUri = ""
        customMediaPlayer?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
            }
            runCatching { mp.release() }
        }
        customMediaPlayer = null
    }

    @Synchronized
    private fun stopSynthesisWorkerLocked() {
        sessionGeneration += 1L
        workerThread?.interrupt()
        workerThread = null
        currentTrack = null
    }

    @Synchronized
    private fun ensureWorkerRunningLocked() {
        runCatching { currentTrack?.setVolume(activeVolume) }
        val existingThread = workerThread
        if (existingThread != null && existingThread.isAlive && !existingThread.isInterrupted) {
            return
        }
        val gen = ++sessionGeneration
        val thread = Thread({ runSynthesisLoop(gen) }, "PomodoroAmbientAudio-$gen").apply {
            isDaemon = true
        }
        workerThread = thread
        thread.start()
    }

    @Synchronized
    fun stop() {
        focusLoopActive = false
        previewUntilElapsed = 0L
        stopCustomMediaPlayerLocked()
        stopSynthesisWorkerLocked()
        abandonAudioFocusLocked()
    }

    private fun runSynthesisLoop(myGeneration: Long) {
        val minBufBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(BUFFER_SAMPLES * 2)

        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: return

        currentTrack = track
        runCatching {
            track.setVolume(activeVolume)
            track.play()
        }.onFailure {
            if (currentTrack === track) currentTrack = null
            runCatching { track.release() }
            return
        }

        val buffer = ShortArray(BUFFER_SAMPLES)
        val rng = Random(System.nanoTime())
        var brownState = 0.0
        var lowPassA = 0.0
        var lowPassB = 0.0
        var lowPassC = 0.0
        var pinkB0 = 0.0
        var pinkB1 = 0.0
        var pinkB2 = 0.0
        var crackleEnv = 0.0
        var rumbleEnv = 0.0
        var phase = 0.0
        var phaseB = 0.0
        var phaseC = 0.0

        try {
            while (
                sessionGeneration == myGeneration &&
                !Thread.currentThread().isInterrupted
            ) {
                val now = SystemClock.elapsedRealtime()
                val keepPlaying = focusLoopActive || now < previewUntilElapsed
                val mode = activeSoundscape
                if (!keepPlaying || mode == AmbientSoundscape.OFF || mode == AmbientSoundscape.CUSTOM) {
                    break
                }

                val vol = activeVolume.toDouble()
                for (i in 0 until BUFFER_SAMPLES) {
                    val white = rng.nextDouble(-1.0, 1.0)
                    val sample: Double = when (mode) {
                        AmbientSoundscape.OFF,
                        AmbientSoundscape.CUSTOM,
                        -> 0.0

                        AmbientSoundscape.SOFT_RAIN -> {
                            lowPassA = 0.86 * lowPassA + 0.14 * white
                            val droplet = if (rng.nextDouble() < 0.0025) rng.nextDouble(0.25, 0.55) else 0.0
                            crackleEnv = 0.94 * crackleEnv + droplet
                            (lowPassA * 0.38 + white * crackleEnv * 0.25)
                        }

                        AmbientSoundscape.OCEAN_WAVES -> {
                            // Slow coastal wave swell (0.085 Hz = ~11.7s cycle) + receding surf wash
                            phase += (2.0 * PI * 0.085) / SAMPLE_RATE
                            phaseB += (2.0 * PI * 0.031) / SAMPLE_RATE
                            val waveSwell = 0.52 + 0.48 * sin(phase + 0.35 * sin(phaseB))
                            val cutoff = 0.03 + 0.11 * waveSwell
                            lowPassA += cutoff * (white - lowPassA)
                            lowPassB = 0.93 * lowPassB + 0.07 * lowPassA
                            (lowPassB * (0.35 + 0.55 * waveSwell) + lowPassA * 0.18 * waveSwell) * 0.72
                        }

                        AmbientSoundscape.MOUNTAIN_STREAM -> {
                            // Babbling brook: multi-band water ripple modulation
                            phase += (2.0 * PI * 3.4) / SAMPLE_RATE
                            phaseB += (2.0 * PI * 5.9) / SAMPLE_RATE
                            lowPassA = 0.78 * lowPassA + 0.22 * white
                            lowPassB = 0.94 * lowPassB + 0.06 * lowPassA
                            val midWater = lowPassA - lowPassB
                            val ripple = 0.62 + 0.22 * sin(phase) + 0.16 * sin(phaseB)
                            val bubble = if (rng.nextDouble() < 0.0032) rng.nextDouble(0.2, 0.45) else 0.0
                            crackleEnv = 0.96 * crackleEnv + bubble
                            (lowPassB * 0.32 + midWater * ripple * 0.48 + sin(phase * 18.0) * crackleEnv * 0.15) * 0.68
                        }

                        AmbientSoundscape.FOREST_WIND -> {
                            val sweep = 0.91 + 0.07 * sin(phase)
                            phase += (2.0 * PI * 0.11) / SAMPLE_RATE
                            lowPassA = sweep * lowPassA + (1.0 - sweep) * white
                            lowPassB = 0.90 * lowPassB + 0.10 * lowPassA
                            lowPassB * 0.58
                        }

                        AmbientSoundscape.FIREPLACE -> {
                            brownState = (brownState + 0.02 * white).coerceIn(-1.0, 1.0) * 0.99
                            if (rng.nextDouble() < 0.0018) {
                                crackleEnv = rng.nextDouble(0.4, 0.9)
                            }
                            crackleEnv *= 0.965
                            (brownState * 0.42 + white * crackleEnv * 0.35)
                        }

                        AmbientSoundscape.LIBRARY -> {
                            lowPassA = 0.96 * lowPassA + 0.04 * white
                            lowPassB = 0.80 * lowPassB + 0.20 * lowPassA
                            val lfo = 0.82 + 0.18 * sin(phase)
                            phase += (2.0 * PI * 0.18) / SAMPLE_RATE
                            lowPassB * lfo * 0.50
                        }

                        AmbientSoundscape.NIGHT_MEADOW -> {
                            // Warm evening breeze + gentle rhythmic cricket chorus
                            lowPassA = 0.95 * lowPassA + 0.05 * white
                            phase += (2.0 * PI * 3_850.0) / SAMPLE_RATE
                            phaseB += (2.0 * PI * 2.4) / SAMPLE_RATE
                            phaseC += (2.0 * PI * 0.35) / SAMPLE_RATE
                            val pulseGate = (sin(phaseB) * 0.5 + 0.5) * (sin(phaseC) * 0.4 + 0.6)
                            val cricket = if (pulseGate > 0.55) {
                                sin(phase) * (pulseGate - 0.55) * 0.22
                            } else {
                                0.0
                            }
                            (lowPassA * 0.44 + cricket) * 0.62
                        }

                        AmbientSoundscape.DISTANT_THUNDER -> {
                            // Steady rain bed + deep low-frequency rolling thunder swells
                            lowPassA = 0.87 * lowPassA + 0.13 * white
                            if (rng.nextDouble() < 0.00018) {
                                rumbleEnv = rng.nextDouble(0.65, 1.0)
                            }
                            rumbleEnv *= 0.99972
                            brownState = (brownState + 0.03 * white).coerceIn(-1.0, 1.0) * 0.994
                            lowPassC = 0.985 * lowPassC + 0.015 * brownState
                            (lowPassA * 0.32 + lowPassC * (0.28 + rumbleEnv * 1.15)) * 0.64
                        }

                        AmbientSoundscape.SINGING_BOWL -> {
                            // Warm harmonic meditation drone (136.1 Hz fundamental + fifth + octave)
                            phase += (2.0 * PI * 136.1) / SAMPLE_RATE
                            phaseB += (2.0 * PI * 204.15) / SAMPLE_RATE
                            phaseC += (2.0 * PI * 0.14) / SAMPLE_RATE
                            val shimmer = 0.78 + 0.22 * sin(phaseC)
                            val drone = sin(phase) * 0.48 +
                                sin(phaseB) * 0.24 * shimmer +
                                sin(phase * 2.0) * 0.12 * (1.0 - 0.3 * sin(phaseC))
                            lowPassA = 0.97 * lowPassA + 0.03 * white
                            (drone * 0.45 + lowPassA * 0.18) * 0.58
                        }

                        AmbientSoundscape.PINK_NOISE -> {
                            // Paul Kellet 3-stage pink noise filter approximation
                            pinkB0 = 0.99765 * pinkB0 + white * 0.0990460
                            pinkB1 = 0.96300 * pinkB1 + white * 0.2965164
                            pinkB2 = 0.57000 * pinkB2 + white * 1.0526913
                            val pink = (pinkB0 + pinkB1 + pinkB2 + white * 0.1848) * 0.11
                            lowPassA = 0.85 * lowPassA + 0.15 * pink
                            lowPassA.coerceIn(-1.0, 1.0) * 0.56
                        }

                        AmbientSoundscape.BROWN_NOISE -> {
                            brownState = (brownState + 0.025 * white).coerceIn(-1.0, 1.0) * 0.992
                            lowPassA = 0.92 * lowPassA + 0.08 * brownState
                            lowPassA * 0.65
                        }
                    }
                    val scaled = (sample * vol * 14_000.0).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buffer[i] = scaled.toShort()
                }
                val written = track.write(buffer, 0, BUFFER_SAMPLES)
                if (written < 0) break
            }
        } catch (_: Throwable) {
            // Clean exit on stop/interrupt
        } finally {
            if (currentTrack === track) {
                currentTrack = null
            }
            runCatching {
                track.pause()
                track.flush()
                track.stop()
                track.release()
            }
        }
    }
}
