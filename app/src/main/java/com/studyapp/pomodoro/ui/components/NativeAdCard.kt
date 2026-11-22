package com.studyapp.pomodoro.ui.components

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.studyapp.pomodoro.BuildConfig
import com.studyapp.pomodoro.ads.AdsConsentManager
import com.studyapp.pomodoro.ui.theme.Border
import com.studyapp.pomodoro.ui.theme.Muted
import com.studyapp.pomodoro.ui.theme.Parchment
import com.studyapp.pomodoro.ui.theme.StoneLight

/**
 * Retained as a lightweight no-op facade for callers that previously notified the
 * main-screen native ad cache on user/network events.
 * The main-screen NativeAdCard has been removed to eliminate the Play Store exit loophole
 * during Focus Mode and replaced with [EndScreenBannerAdManager] on the timer completion screen.
 */
object NativeAdCache {
    fun onUserAction() = Unit

    @Suppress("UNUSED_PARAMETER")
    fun onNetworkAvailable(appContext: Context) = Unit
}

/**
 * Manages preloading and single-use lifecycle of the non-intrusive Banner Ad shown on the
 * timer completion end screen (`CompletionDialog`).
 *
 * Key guarantees:
 * 1. Uses the exact same AdMob Banner Ad Unit ID (`BuildConfig.ADMOB_BANNER_UNIT_ID` /
 *    `BuildConfig.ADMOB_NATIVE_UNIT_ID`) across all three timer completion end screens
 *    (Focus -> Short Break, Focus -> Long Break, and Short/Long Break -> Focus).
 * 2. Preloads the banner ~30 seconds before the active timer phase finishes (`remainingMillis <= 30_000L`).
 * 3. Displays the banner on the end screen ONLY if it has already finished loading (`isAdReady == true`).
 *    If it failed to load or has not finished loading when the end screen appears, it is skipped cleanly
 *    with zero empty space or layout shift (preventing accidental clicks and complying with AdMob policies).
 * 4. Never reuses an ad across multiple end screens: each preload cycle creates a brand-new `AdView`
 *    with a unique `adInstanceId`, and closing the end screen (or starting a new timer) immediately
 *    destroys and clears the consumed `AdView` so the next end screen loads a fresh ad.
 */
object EndScreenBannerAdManager {
    /**
     * Single shared Banner Ad Unit ID used across all three completion end screens.
     * Maps Google's default Native test ID to Google's official Banner test ID when running
     * without custom release secrets, while preserving any configured production unit ID verbatim.
     */
    val BANNER_AD_UNIT_ID: String = BuildConfig.ADMOB_BANNER_UNIT_ID.trim().let { configured ->
        if (configured.isEmpty() || configured == "ca-app-pub-3940256099942544/2247696110") {
            "ca-app-pub-3940256099942544/6300978111"
        } else {
            configured
        }
    }

    private var preloadedAdView: AdView? = null
    private var isLoading: Boolean = false
    private var hasAttemptedForCurrentCycle: Boolean = false

    /**
     * Mirrors the width the completion dialog leaves for the ad slot (outer 16 dp margin,
     * dialog padding and ad-slot padding) so the banner is never measured wider than its
     * container. Inline adaptive banners pick a creative that fits that width.
     */
    private fun adaptiveBannerSize(context: Context): AdSize {
        val configuration = context.resources.configuration
        val screenWidth = configuration.screenWidthDp
        val screenHeight = configuration.screenHeightDp
        val shortLandscape = screenWidth > screenHeight && screenHeight < 560
        val dialogWidth = if (shortLandscape) {
            minOf(screenWidth - 32, 580)
        } else {
            minOf(screenWidth - 32, 370)
        }
        val horizontalChrome = if (shortLandscape) 56 else 64
        val availableWidth = (dialogWidth - horizontalChrome).coerceAtLeast(240)
        return AdSize.getInlineAdaptiveBannerAdSize(availableWidth, 50)
    }

    var isAdReady by mutableStateOf(false)
        private set

    var adInstanceId by mutableIntStateOf(0)
        private set

    /**
     * Triggered when the running timer enters the final 30 seconds (`remainingMillis in 1..30_000L`).
     * Requests a brand-new banner ad once per timer completion cycle if AdMob consent allows.
     */
    fun preloadForUpcomingEndScreen(context: Context) {
        if (!BuildConfig.NATIVE_ADS_ENABLED) return
        if (hasAttemptedForCurrentCycle || isLoading || preloadedAdView != null) return
        val appContext = context.applicationContext
        if (!AdsConsentManager.ensureInitializedIfAllowed(appContext)) return

        hasAttemptedForCurrentCycle = true
        isLoading = true
        isAdReady = false

        val adView = AdView(appContext).apply {
            // A fixed 320x50 banner is wider than the completion dialog's content area on
            // 360-393 dp phones. Size the creative to the space the dialog actually offers.
            setAdSize(adaptiveBannerSize(appContext))
            adUnitId = BANNER_AD_UNIT_ID
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    if (preloadedAdView === this@apply) {
                        this@EndScreenBannerAdManager.isLoading = false
                        this@EndScreenBannerAdManager.isAdReady = true
                        this@EndScreenBannerAdManager.adInstanceId += 1
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (preloadedAdView === this@apply) {
                        this@EndScreenBannerAdManager.isLoading = false
                        this@EndScreenBannerAdManager.isAdReady = false
                        runCatching { destroy() }
                        this@EndScreenBannerAdManager.preloadedAdView = null
                    }
                }

                override fun onAdClicked() {
                    // Always mark the journey: Focus Mode must not pull the app back over
                    // the advertiser's landing page, locked or not.
                    AdsConsentManager.onExternalAdOrPolicyNavigationStarted()
                }
            }
        }
        preloadedAdView = adView
        runCatching {
            adView.loadAd(AdRequest.Builder().build())
        }.onFailure {
            isLoading = false
            isAdReady = false
            runCatching { adView.destroy() }
            preloadedAdView = null
        }
    }

    /**
     * Returns the preloaded `AdView` only if it has already finished loading.
     * If it is still loading when the end screen opens, we abort and skip it for this end screen
     * so it never pops in late or shifts buttons while the user is about to tap.
     */
    fun getLoadedAdViewOrSkip(): AdView? {
        if (!isAdReady) {
            if (isLoading) {
                val pending = preloadedAdView
                preloadedAdView = null
                isLoading = false
                runCatching { pending?.destroy() }
            }
            return null
        }
        return preloadedAdView
    }

    /**
     * Resets the cycle gate when a new timer starts, finishes, or resets so the next timer's
     * final 30-second window will preload a fresh banner ad.
     */
    fun onNewTimerCycleStarted() {
        consumeAndDestroyAd()
        hasAttemptedForCurrentCycle = false
    }

    /**
     * Destroys the current `AdView` when the completion dialog closes so the same ad is never
     * reused on a subsequent end screen.
     */
    fun consumeAndDestroyAd() {
        val existing = preloadedAdView
        preloadedAdView = null
        isLoading = false
        isAdReady = false
        if (existing != null) {
            runCatching {
                (existing.parent as? ViewGroup)?.removeView(existing)
                existing.destroy()
            }
        }
    }
}

/**
 * Non-intrusive, policy-compliant Banner Ad slot rendered inside `CompletionDialog` (the end screen
 * after each Focus, Short Break, or Long Break timer ends).
 *
 * - Renders ONLY when [EndScreenBannerAdManager] has a preloaded banner ready at dialog open time.
 * - Clearly labeled with an "AD" attribution badge and separated from dialog action buttons to
 *   prevent accidental taps (AdMob Invalid Activity & Layout policy compliance).
 * - The engine destroys the `AdView` when the dialog closes or the next timer starts, so every
 *   end screen displays a newly loaded banner ad and a rotation does not drop it.
 */
@Composable
fun EndScreenBannerAdSlot(
    modifier: Modifier = Modifier,
) {
    val ready = EndScreenBannerAdManager.isAdReady
    val instanceId = EndScreenBannerAdManager.adInstanceId

    // Resolve the ad once per loaded instance instead of mutating the manager from
    // composition. The engine destroys it on dialog dismiss / next timer start, so a
    // configuration change reuses the same creative instead of dropping it.
    val adView = remember(ready, instanceId) {
        if (ready && instanceId > 0) EndScreenBannerAdManager.getLoadedAdViewOrSkip() else null
    }

    if (adView == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Parchment.copy(alpha = 0.72f))
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "AD",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(StoneLight)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        (adView.parent as? ViewGroup)?.removeView(adView)
                        addView(adView)
                    }
                },
                update = { container ->
                    if (adView.parent !== container) {
                        (adView.parent as? ViewGroup)?.removeView(adView)
                        container.removeAllViews()
                        container.addView(adView)
                    }
                },
            )
        }
    }
}
