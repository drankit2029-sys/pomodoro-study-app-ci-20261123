package com.studyapp.pomodoro

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class PomodoroViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PomodoroTimerEngine.getInstance(application)

    val uiState: StateFlow<PomodoroUiState> = engine.state

    fun toggle() = engine.toggle()

    fun reset() = engine.reset()

    fun selectPhase(phase: Phase) = engine.selectPhase(phase)

    fun updateSetting(phase: Phase, value: Int) = engine.updateSetting(phase, value)

    fun resetDailySessions() = engine.resetDailySessions()

    fun updateAlarmEnabled(enabled: Boolean) = engine.updateAlarmEnabled(enabled)

    fun updateAlarmSound(sound: String) = engine.updateAlarmSound(sound)

    fun updateAlarmVibration(enabled: Boolean) = engine.updateAlarmVibration(enabled)

    fun updateAlarmDuration(seconds: Int) = engine.updateAlarmDuration(seconds)

    fun previewAlarm() = engine.previewAlarm()

    fun stopAlarm() = engine.stopAlarm()

    fun dismissCompletion() = engine.dismissCompletion()

    fun startNext() = engine.startNext()

    fun startFlowOvertime() = engine.startFlowOvertime()

    fun finishFlow() = engine.finishFlow()

    fun selectSubject(subjectId: String) = engine.selectSubject(subjectId)

    fun addCustomSubject(name: String, colorHex: Long): Boolean =
        engine.addCustomSubject(name, colorHex)

    fun removeCustomSubject(subjectId: String) = engine.removeCustomSubject(subjectId)

    fun setShowYearHistory(showYear: Boolean) = engine.setShowYearHistory(showYear)

    fun exportHistoryCsv(context: Context, onLaunchingExternalChooser: () -> Unit = {}) =
        engine.exportHistoryCsv(context, onLaunchingExternalChooser)

    fun setMusicEnabled(enabled: Boolean) = engine.setMusicEnabled(enabled)

    fun toggleMusic() = engine.toggleMusic()

    fun selectAmbientSoundscape(soundscape: AmbientSoundscape) =
        engine.selectAmbientSoundscape(soundscape)

    fun selectCustomMusicTrack(uri: String, title: String) =
        engine.selectCustomMusicTrack(uri, title)

    fun updateAmbientVolume(volume: Float) = engine.updateAmbientVolume(volume)

    fun previewAmbientSoundscape() = engine.previewAmbientSoundscape()

    fun updateStrictFocus(settings: StrictFocusSettings) = engine.updateStrictFocus(settings)

    fun setAutoFocusEnabled(enabled: Boolean) = engine.setAutoFocusEnabled(enabled)

    fun setAdvancedFocusEnabled(enabled: Boolean) = engine.setAdvancedFocusEnabled(enabled)

    fun setAdvancedFocusConfirmVisible(visible: Boolean) =
        engine.setAdvancedFocusConfirmVisible(visible)

    fun setBlockingMode(mode: FocusBlockingMode) = engine.setBlockingMode(mode)

    fun updateBlockedPackages(packages: Set<String>) = engine.updateBlockedPackages(packages)

    fun setBlockDuringBreaks(enabled: Boolean) = engine.setBlockDuringBreaks(enabled)

    fun useEmergencyPass() = engine.useEmergencyPass()

    fun setFocusModeActive(active: Boolean) = engine.setFocusModeActive(active)

    fun setAppPinned(pinned: Boolean) = engine.setFocusModeActive(pinned)

    fun verifyAndEnforcePermissions() = engine.verifyAndEnforcePermissions()

    fun refreshPermissionStates() = engine.refreshPermissionStates()

    fun onPermissionDeclined(type: FocusPermissionType) = engine.onPermissionDeclined(type)

    fun dismissDeclinedPermission() = engine.dismissDeclinedPermission()

    fun seedDemoHistory() = engine.seedDemoHistory()

    fun addTask(title: String, estimatedPomodoros: Int): Boolean =
        engine.addTask(title, estimatedPomodoros)

    fun selectActiveTask(taskId: String?) = engine.selectActiveTask(taskId)

    fun toggleTaskCompleted(taskId: String) = engine.toggleTaskCompleted(taskId)

    fun deleteTask(taskId: String) = engine.deleteTask(taskId)

    fun clearCompletedTasks() = engine.clearCompletedTasks()
}
