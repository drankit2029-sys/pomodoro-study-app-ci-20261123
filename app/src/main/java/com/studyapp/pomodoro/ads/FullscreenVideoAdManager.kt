package com.studyapp.pomodoro.ads

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.studyapp.pomodoro.BuildConfig
import com.studyapp.pomodoro.PomodoroAlarmPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "FullscreenVideoAdMgr"
private const val INTERSTITIAL_LOAD_TIMEOUT_MILLIS = 10_000L

// Google recommends not showing a cached interstitial long after it was loaded; a stale
// creative can fail to render and counts as a wasted request.
private const val INTERSTITIAL_MAX_AGE_MILLIS = 60 * 60 * 1000L

class InterstitialAdException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Policy-compliant preloader and presenter for the post-Focus break Interstitial Ad:
 * - **Auto-preloads before Focus ends**: [preloadIfNeeded] is called when a Focus session
 *   starts, when it enters the final 60 seconds before completion, and when the completion
 *   dialog appears, ensuring the ad is ready in memory to play immediately on "Start break".
 * - **No fallbacks**: Uses strictly [BuildConfig.ADMOB_INTERSTITIAL_VIDEO_UNIT_ID], never
 *   switches to secondary fallback units, never shows a simulated fallback dialog, and never
 *   pops up a delayed ad asynchronously after the user has already started their break.
 * - **Clean error throwing & recovery**: Throws [InterstitialAdException] whenever loading or
 *   showing fails, catches and handles it cleanly by not showing the ad, and resets state so
 *   the next Focus session attempts to load and show the ad again.
 */
object FullscreenVideoAdManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val loadMutex = Mutex()

    @Volatile
    private var interstitialAd: InterstitialAd? = null

    @Volatile
    private var loadedAtElapsed: Long = 0L

    @Volatile
    private var isLoading: Boolean = false

    @Volatile
    var isShowingAd: Boolean = false
        private set

    val isAdLoaded: Boolean
        get() = interstitialAd != null

    // Kept always false for binary/source compatibility; no fallback dialog is ever shown.
    private val _isFallbackVideoAdShowing = MutableStateFlow(false)
    val isFallbackVideoAdShowing: StateFlow<Boolean> = _isFallbackVideoAdShowing.asStateFlow()

    /**
     * Triggers an asynchronous preload if no interstitial is currently cached or loading.
     * Safe to call when a Focus session starts and as the session nears completion.
     */
    fun preload(context: Context) {
        val appContext = context.applicationContext
        if (interstitialAd != null || isLoading) return
        scope.launch {
            preloadInternal(appContext)
        }
    }

    fun clear() {
        interstitialAd = null
        loadedAtElapsed = 0L
        isLoading = false
        isShowingAd = false
        _isFallbackVideoAdShowing.value = false
    }

    fun preloadIfNeeded(context: Context) {
        if (interstitialAd == null && !isLoading) {
            preload(context)
        }
    }

    internal suspend fun preloadInternal(appContext: Context): InterstitialAd? {
        return loadMutex.withLock {
            interstitialAd?.let { return@withLock it }
            isLoading = true
            try {
                val loaded = loadInterstitialOrThrow(appContext)
                interstitialAd = loaded
                loadedAtElapsed = SystemClock.elapsedRealtime()
                loaded
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(
                    TAG,
                    "Interstitial ad preload failed cleanly; will retry on next attempt",
                    error,
                )
                interstitialAd = null
                null
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun loadInterstitialOrThrow(appContext: Context): InterstitialAd {
        if (!AdsConsentManager.ensureInitializedIfAllowed(appContext)) {
            throw InterstitialAdException("AdMob consent not granted; skipping interstitial load.")
        }
        val adUnitId = BuildConfig.ADMOB_INTERSTITIAL_VIDEO_UNIT_ID.trim()
        if (adUnitId.isEmpty()) {
            throw InterstitialAdException("ADMOB_INTERSTITIAL_VIDEO_UNIT_ID is blank.")
        }
        return try {
            withTimeout(INTERSTITIAL_LOAD_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine { continuation ->
                    val resumed = AtomicBoolean(false)
                    InterstitialAd.load(
                        appContext,
                        adUnitId,
                        AdRequest.Builder().build(),
                        object : InterstitialAdLoadCallback() {
                            override fun onAdLoaded(ad: InterstitialAd) {
                                if (resumed.compareAndSet(false, true) && continuation.isActive) {
                                    continuation.resume(ad)
                                }
                            }

                            override fun onAdFailedToLoad(adError: LoadAdError) {
                                if (resumed.compareAndSet(false, true) && continuation.isActive) {
                                    continuation.resumeWithException(
                                        InterstitialAdException(
                                            "code=${adError.code} domain=${adError.domain} message=${adError.message}",
                                        ),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: InterstitialAdException) {
            throw e
        } catch (t: Throwable) {
            throw InterstitialAdException("Interstitial ad load failed or timed out", t)
        }
    }

    /**
     * Shows the preloaded interstitial at the user-initiated Focus→break transition and
     * invokes [onFinished] exactly once — on dismiss, on failure, or immediately when no ad
     * is available — so the caller can start the break only after the ad has closed.
     */
    @Suppress("UNUSED_PARAMETER")
    fun showOnBreakStart(
        activity: Activity,
        isFocusLocked: Boolean = false,
        onFinished: () -> Unit = {},
    ) {
        val appContext = activity.applicationContext
        PomodoroAlarmPlayer.stop()

        val finished = AtomicBoolean(false)
        fun finishOnce() {
            if (finished.compareAndSet(false, true)) onFinished()
        }

        val readyAd = interstitialAd
        interstitialAd = null
        val stale = readyAd != null &&
            SystemClock.elapsedRealtime() - loadedAtElapsed > INTERSTITIAL_MAX_AGE_MILLIS
        if (readyAd == null || stale) {
            loadedAtElapsed = 0L
            Log.w(
                TAG,
                if (stale) {
                    "Discarding a stale interstitial; starting the break without an ad"
                } else {
                    "Interstitial ad was not preloaded in time; starting the break without an ad"
                },
            )
            preload(appContext)
            finishOnce()
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(TAG, "Activity is finishing; starting the break without an ad")
            preload(appContext)
            finishOnce()
            return
        }

        isShowingAd = true
        readyAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
            }

            override fun onAdClicked() {
                // Keep Focus Mode away from the advertiser page/AdChoices until the user
                // comes back; burying a landing page is click interference.
                AdsConsentManager.onExternalAdOrPolicyNavigationStarted()
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingAd = false
                preload(appContext)
                finishOnce()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                isShowingAd = false
                Log.w(
                    TAG,
                    "Interstitial failed to show: code=${adError.code} message=${adError.message}",
                )
                preload(appContext)
                finishOnce()
            }
        }
        // Post to the next UI frame so CompletionDialog is detached from MainActivity's
        // window buffer before AdActivity renders over it.
        val showAd = {
            if (!activity.isFinishing && !activity.isDestroyed) {
                runCatching { readyAd.show(activity) }.onFailure { error ->
                    isShowingAd = false
                    Log.w(TAG, "Interstitial show threw; starting the break without it", error)
                    preload(appContext)
                    finishOnce()
                }
            } else {
                isShowingAd = false
                preload(appContext)
                finishOnce()
            }
        }
        val decorView = activity.window?.decorView
        if (decorView != null) {
            decorView.post { showAd() }
        } else {
            showAd()
        }
    }

    fun dismissFallbackAd() {
        _isFallbackVideoAdShowing.value = false
    }

    internal fun dropLoadedAdForTesting() {
        interstitialAd = null
        isLoading = false
    }
}
