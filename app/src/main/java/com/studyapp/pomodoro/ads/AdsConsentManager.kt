package com.studyapp.pomodoro.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.studyapp.pomodoro.BuildConfig
import com.studyapp.pomodoro.ui.components.NativeAdCache
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gathers GDPR/EEA/UK consent through Google's User Messaging Platform (UMP) before any ad
 * request is made, and initializes the Google Mobile Ads SDK strictly when consent allows
 * (or in debug builds wired exclusivamente to Google's public test ad unit IDs).
 */
object AdsConsentManager {

    private const val GOOGLE_TEST_PUBLISHER_PREFIX = "ca-app-pub-3940256099942544"
    private const val EXTERNAL_AD_JOURNEY_TIMEOUT_MS = 180_000L
    private val isMobileAdsInitialized = AtomicBoolean(false)
    private val isNetworkCallbackRegistered = AtomicBoolean(false)

    @Volatile
    private var adClickDisconnectElapsed: Long = 0L

    /**
     * Marks that the user tapped an ad or external policy link so Focus Mode does not yank
     * MainActivity back over the advertiser's destination browser/Play Store until the user
     * returns to the app.
     */
    fun onExternalAdOrPolicyNavigationStarted() {
        adClickDisconnectElapsed = android.os.SystemClock.elapsedRealtime()
    }

    fun onHostActivityResumed() {
        adClickDisconnectElapsed = 0L
    }

    val isInExternalAdOrPolicyJourney: Boolean
        get() {
            val started = adClickDisconnectElapsed
            if (started <= 0L) return false
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            return if (elapsed in 0L..EXTERNAL_AD_JOURNEY_TIMEOUT_MS) {
                true
            } else {
                adClickDisconnectElapsed = 0L
                false
            }
        }

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _isPrivacyOptionsRequired = MutableStateFlow(false)
    val isPrivacyOptionsRequired: StateFlow<Boolean> = _isPrivacyOptionsRequired.asStateFlow()

    private fun isUsingOnlyGoogleTestUnits(): Boolean =
        BuildConfig.ADMOB_NATIVE_UNIT_ID.startsWith(GOOGLE_TEST_PUBLISHER_PREFIX) &&
            BuildConfig.ADMOB_INTERSTITIAL_VIDEO_UNIT_ID.startsWith(GOOGLE_TEST_PUBLISHER_PREFIX)

    fun consentInformation(context: Context): ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    fun ensureInitializedIfAllowed(context: Context): Boolean {
        val appContext = context.applicationContext
        val info = runCatching { consentInformation(appContext) }.getOrNull()
        val allowed = (info?.canRequestAds() == true) || isUsingOnlyGoogleTestUnits()
        if (allowed && isMobileAdsInitialized.compareAndSet(false, true)) {
            runCatching { MobileAds.initialize(appContext) }
                .onFailure { isMobileAdsInitialized.set(false) }
        }
        _canRequestAds.value = allowed
        return allowed
    }

    /** Re-reads the cached consent decision and registers network listener for ad preloading. */
    fun refresh(context: Context) {
        val appContext = context.applicationContext
        registerNetworkCallbackIfNeeded(appContext)
        runCatching {
            updateState(appContext, consentInformation(appContext))
        }
    }

    private fun registerNetworkCallbackIfNeeded(appContext: Context) {
        if (!isNetworkCallbackRegistered.compareAndSet(false, true)) return
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    runCatching { updateState(appContext, consentInformation(appContext)) }
                    if (_canRequestAds.value) {
                        if (BuildConfig.NATIVE_ADS_ENABLED) NativeAdCache.onNetworkAvailable(appContext)
                        FullscreenVideoAdManager.preloadIfNeeded(appContext)
                    }
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    ) {
                        runCatching { updateState(appContext, consentInformation(appContext)) }
                        if (_canRequestAds.value) {
                            if (BuildConfig.NATIVE_ADS_ENABLED) NativeAdCache.onNetworkAvailable(appContext)
                            FullscreenVideoAdManager.preloadIfNeeded(appContext)
                        }
                    }
                }
            })
        }.onFailure {
            isNetworkCallbackRegistered.set(false)
        }
    }

    /**
     * Requests an updated consent status and shows the privacy message when one is
     * required. Safe to call on every launch; state flows are updated afterwards
     * whether the update succeeds or fails.
     */
    fun gatherConsent(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val information = runCatching { consentInformation(activity) }.getOrNull() ?: return
        // Check cached UMP consent state immediately
        updateState(activity, information)

        val params = ConsentRequestParameters.Builder().build()
        runCatching {
            information.requestConsentInfoUpdate(
                activity,
                params,
                {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        runCatching {
                            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                                updateState(activity, information)
                            }
                        }.onFailure {
                            updateState(activity, information)
                        }
                    } else {
                        updateState(activity, information)
                    }
                },
                {
                    updateState(activity, information)
                },
            )
        }
    }

    /**
     * Opens the UMP privacy options form so users in regulated regions (EEA/UK) can
     * review or modify their ad consent choices at any time.
     */
    fun showPrivacyOptionsForm(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val information = runCatching { consentInformation(activity) }.getOrNull() ?: return
        runCatching {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) {
                updateState(activity, information)
            }
        }
    }

    private fun updateState(context: Context, information: ConsentInformation) {
        val appContext = context.applicationContext
        val canRequest = runCatching { information.canRequestAds() }.getOrDefault(false) ||
            isUsingOnlyGoogleTestUnits()
        if (canRequest && isMobileAdsInitialized.compareAndSet(false, true)) {
            runCatching { MobileAds.initialize(appContext) }
                .onFailure { isMobileAdsInitialized.set(false) }
        }
        val becameAllowed = !_canRequestAds.value && canRequest
        _canRequestAds.value = canRequest
        _isPrivacyOptionsRequired.value = runCatching {
            information.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED
        }.getOrDefault(false)
        if (becameAllowed || canRequest) {
            if (BuildConfig.NATIVE_ADS_ENABLED) NativeAdCache.onNetworkAvailable(appContext)
            FullscreenVideoAdManager.preloadIfNeeded(appContext)
        }
    }
}
