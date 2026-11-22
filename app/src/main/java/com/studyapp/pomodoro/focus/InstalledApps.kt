package com.studyapp.pomodoro.focus

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.Locale

/**
 * Loads the apps that can actually be launched from the home screen. Using a
 * `CATEGORY_LAUNCHER` query (declared in the manifest `<queries>`) avoids the sensitive
 * `QUERY_ALL_PACKAGES` permission while still covering everything a blocker needs.
 */
object InstalledApps {

    fun loadLaunchable(context: Context): List<BlockableApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolved = packageManager.queryIntentActivities(launcherIntent, 0)
        val homePackages = resolveHomePackages(packageManager)
        return resolved.asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { info ->
                info.packageName != context.packageName &&
                    info.packageName !in homePackages &&
                    !BlockPolicy.isAlwaysAllowed(info.packageName)
            }
            .distinctBy { it.packageName }
            .map { info ->
                BlockableApp(
                    packageName = info.packageName,
                    label = applicationLabel(packageManager, info),
                    category = categoryOf(info),
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    private fun applicationLabel(packageManager: PackageManager, info: ApplicationInfo): String =
        runCatching { packageManager.getApplicationLabel(info).toString() }
            .getOrDefault(info.packageName)
            .ifBlank { info.packageName }

    private fun categoryOf(info: ApplicationInfo): AppCategory = when (info.category) {
        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
        ApplicationInfo.CATEGORY_VIDEO -> AppCategory.VIDEO
        ApplicationInfo.CATEGORY_GAME -> AppCategory.GAME
        else -> AppCategory.OTHER
    }

    private fun resolveHomePackages(packageManager: PackageManager): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            val resolved = if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.resolveActivity(
                    homeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolved?.activityInfo?.packageName?.let(::setOf) ?: emptySet()
        }.getOrDefault(emptySet())
    }
}

/** Human-readable category chip shown in the picker. */
fun AppCategory.displayLabel(): String = when (this) {
    AppCategory.SOCIAL -> "Social"
    AppCategory.VIDEO -> "Video"
    AppCategory.GAME -> "Game"
    AppCategory.OTHER -> ""
}

/** Case-insensitive search used by the picker. */
fun BlockableApp.matchesQuery(query: String): Boolean {
    val clean = query.trim().lowercase(Locale.getDefault())
    if (clean.isEmpty()) return true
    return label.lowercase(Locale.getDefault()).contains(clean) ||
        packageName.lowercase(Locale.getDefault()).contains(clean)
}
