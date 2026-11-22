package com.studyapp.pomodoro

import android.app.Application
import com.studyapp.pomodoro.ads.AdsConsentManager
import com.studyapp.pomodoro.ads.FullscreenVideoAdManager

class PomodoroApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        PomodoroNotifications.createChannels(this)
        PomodoroTimerEngine.initialize(this)
        AdsConsentManager.refresh(this)
        if (AdsConsentManager.canRequestAds.value) {
            FullscreenVideoAdManager.preload(this)
        }
    }
}
