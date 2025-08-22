// File: AutoFilterWorker.kt

package com.example.epgutility

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

class AutoFilterWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AutoFilterWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "🔍 AutoFilterWorker started — checking for file updates")

        val configResult = ConfigManager.loadConfig(applicationContext)
        val config = configResult.config

        // ❌ Auto mode not enabled
        if (!config.system.autoModeEnabled) {
            Log.d(TAG, "⏸️ Auto mode disabled in settings")
            return Result.success()
        }

        val playlistPathStr = config.system.playlistPath
        val epgPathStr = config.system.epgPath

        val inputDir = File(applicationContext.filesDir, "input")
        inputDir.mkdirs()

        var shouldAlert = false

        // ✅ Check playlist file
        if (!config.system.disablePlaylistFiltering && !playlistPathStr.isNullOrEmpty()) {
            val srcFile = File(playlistPathStr)
            val destFile = findExistingM3UFile(inputDir)

            val needsUpdate = config.system.forceSyncPlaylist ||
                    destFile == null ||
                    srcFile.lastModified() > destFile.lastModified()

            if (srcFile.exists() && needsUpdate) {
                Log.d(TAG, "🔔 Playlist has changed — alert user")
                shouldAlert = true
            }
        }

        // ✅ Check EPG file
        if (!config.system.disableEPGFiltering && !epgPathStr.isNullOrEmpty()) {
            val srcFile = File(epgPathStr)
            val destFile = findExistingEPGFile(inputDir)

            val needsUpdate = config.system.forceSyncEpg ||
                    destFile == null ||
                    srcFile.lastModified() > destFile.lastModified()

            if (srcFile.exists() && needsUpdate) {
                Log.d(TAG, "🔔 EPG has changed — alert user")
                shouldAlert = true
            }
        }

        if (shouldAlert) {
            showUpdateAvailableNotification()
        }

        return Result.success()
    }

    // File helpers (copy from FilterProgressActivity)
    private fun findExistingM3UFile(directory: File): File? {
        return directory.listFiles()?.find { it.isPlaylist() }
    }

    private fun findExistingEPGFile(directory: File): File? {
        return directory.listFiles()?.find { it.isEpg() }
    }

    private fun showUpdateAvailableNotification() {
        val channelId = "filter_update_channel"
        val manager = applicationContext.getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Filter Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when playlist or EPG has changed"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Filter Files Update")
            .setContentText("Your playlist or EPG has changed. Open EPG Filter Utility to filter at your convenience.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
    private fun File.isPlaylist() = name.endsWith(".m3u", true) || name.endsWith(".m3u8", true)
    private fun File.isEpg() = name.endsWith(".xml", true) || name.endsWith(".xml.gz", true)
}