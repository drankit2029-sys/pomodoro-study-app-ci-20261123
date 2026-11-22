package com.studyapp.pomodoro.focus

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.studyapp.pomodoro.FocusPermissionType

/**
 * Coordinates Focus Mode permission queries and settings intents by delegating to
 * [GlobalPermissionManager]. Focus Mode blocks app exiting strictly via Accessibility.
 */
object StrictFocusManager {

    fun hasNotificationPermission(context: Context): Boolean =
        GlobalPermissionManager.hasNotificationPermission(context)

    fun hasAccessibilityLockPermission(context: Context): Boolean =
        GlobalPermissionManager.hasAccessibilityPermission(context)

    fun getMissingFocusPermissions(context: Context): List<FocusPermissionType> =
        GlobalPermissionManager.getMissingFocusPermissions(context)

    fun getMissingAutoFocusPermissions(context: Context): List<FocusPermissionType> =
        GlobalPermissionManager.getMissingFocusPermissions(context)

    fun getMissingAdvancedFocusPermissions(context: Context): List<FocusPermissionType> =
        GlobalPermissionManager.getMissingFocusPermissions(context)

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(intent) }
    }
}
