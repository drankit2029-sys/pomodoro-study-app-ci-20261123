package com.studyapp.pomodoro.export

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.studyapp.pomodoro.DailyRhythmRecord
import com.studyapp.pomodoro.DayHistoryEntry
import com.studyapp.pomodoro.PomodoroUiState
import com.studyapp.pomodoro.buildStudyHistoryCsv
import java.io.File
import java.time.LocalDate

/**
 * Exports the study ledger as a real CSV file: saves a copy to the public Downloads folder
 * on Android 10+ and shares a FileProvider `content://` URI through the system chooser
 * (only when Focus Mode is not blocking external app launches).
 */
object StudyHistoryExporter {

    fun exportAndShareCsv(
        context: Context,
        state: PomodoroUiState,
        fullHistoryMap: Map<Long, DayHistoryEntry>,
        onLaunchingExternalChooser: () -> Unit = {},
    ) {
        if (state.isFocusExitBlocked) return

        val todayRecord = DailyRhythmRecord(
            dayEpoch = state.todayEpoch,
            completedFocus = state.completedFocus,
            focusedMinutes = state.focusedMinutes,
        )
        val csvContent = buildStudyHistoryCsv(
            history = fullHistoryMap,
            todayRecord = todayRecord,
            subjectMinutesByDay = state.subjectMinutesByDay,
            tags = state.subjectTags,
            todayEpoch = state.todayEpoch,
        )
        val dateSuffix = runCatching { LocalDate.ofEpochDay(state.todayEpoch).toString() }
            .getOrDefault("export")
        val fileName = "pomodoro_study_ledger_$dateSuffix.csv"

        val savedToDownloads = saveToDownloads(context, fileName, csvContent)

        val fileUri: Uri? = runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            pruneOldExports(exportDir)
            val exportFile = File(exportDir, fileName)
            exportFile.writeText(csvContent, Charsets.UTF_8)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile,
            )
        }.getOrNull()

        if (savedToDownloads) {
            Toast.makeText(
                context.applicationContext,
                "Saved $fileName to Downloads",
                Toast.LENGTH_SHORT,
            ).show()
        }

        if (fileUri != null && !state.isFocusExitBlocked) {
            onLaunchingExternalChooser()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, fileName)
                putExtra(Intent.EXTRA_SUBJECT, "Pomodoro Study Ledger ($dateSuffix)")
                putExtra(Intent.EXTRA_STREAM, fileUri)
                clipData = ClipData.newRawUri(fileName, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Export Study Ledger CSV").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(chooser) }
        }
    }

    /**
     * The cache directory is invisible to the user and never cleaned by the app, so every
     * export would otherwise leave a CSV behind forever. Keep only the most recent files.
     */
    private fun pruneOldExports(exportDir: File) {
        runCatching {
            exportDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_CACHED_EXPORTS)
                ?.forEach { it.delete() }
        }
    }

    private const val MAX_CACHED_EXPORTS = 5

    private fun saveToDownloads(context: Context, fileName: String, content: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        }.getOrDefault(false)
    }
}
