// File: AutoFilterWorker.kt
package com.example.epgutility

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.delay
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
        val outputDir = File(applicationContext.filesDir, "output")
        inputDir.mkdirs()
        outputDir.mkdirs()

        var shouldFilter = false

        // ✅ Check playlist file
        if (!config.system.disablePlaylistFiltering && !playlistPathStr.isNullOrEmpty()) {
            val srcFile = File(playlistPathStr)
            val destFile = findExistingM3UFile(inputDir)

            val needsUpdate = config.system.forceSyncPlaylist ||
                    destFile == null ||
                    srcFile.lastModified() > destFile.lastModified()

            if (srcFile.exists() && needsUpdate) {
                Log.d(TAG, "🆕 Playlist file has changed — needs update")
                shouldFilter = true
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
                Log.d(TAG, "🆕 EPG file has changed — needs update")
                shouldFilter = true
            }
        }

        if (!shouldFilter) {
            Log.d(TAG, "✅ No changes detected — skipping filtering")
            return Result.success()
        }

        Log.d(TAG, "⚡ Changes detected — starting background filtering...")

        // ✅ Copy files to internal storage first
        try {
            // ✅ Copy playlist
            if (!config.system.disablePlaylistFiltering && !playlistPathStr.isNullOrEmpty()) {
                val src = File(playlistPathStr)
                val dest = File(inputDir, src.name)
                if (src.exists()) {
                    src.copyTo(dest, overwrite = true)
                    Log.d(TAG, "📎 Copied playlist to internal storage")
                    // ✅ Clear the flag
                    config.system.forceSyncPlaylist = false
                    ConfigManager.saveConfig(applicationContext, config)
                }
            }

            // ✅ Copy EPG
            if (!config.system.disableEPGFiltering && !epgPathStr.isNullOrEmpty()) {
                val src = File(epgPathStr)
                val dest = File(inputDir, src.name)
                if (src.exists()) {
                    src.copyTo(dest, overwrite = true)
                    Log.d(TAG, "📎 Copied EPG to internal storage")
                    // ✅ Clear the flag
                    config.system.forceSyncEpg = false
                    ConfigManager.saveConfig(applicationContext, config)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to copy files for processing", e)
            return Result.retry()
        }

        // ✅ Start filtering via EpgProcessorService in auto mode
        try {
            val intent = android.content.Intent(applicationContext, EpgProcessorService::class.java).apply {
                action = EpgProcessorService.ACTION_START_AUTO_EPG_PROCESSING
                putExtra("PLAYLIST_PATH", playlistPathStr)
                putExtra("EPG_PATH", epgPathStr)
            }
            applicationContext.startService(intent)

            // Wait a bit to let service start
            delay(2_000)

            // Optional: wait until service finishes (but not required)
            // We return success immediately since WorkManager doesn’t need to wait

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start EpgProcessorService", e)
            return Result.retry()
        }

        Log.d(TAG, "✅ Auto filtering triggered successfully")
        return Result.success()
    }

    // File helpers (copy from FilterProgressActivity)
    private fun findExistingM3UFile(directory: File): File? {
        return directory.listFiles()?.find { it.isPlaylist() }
    }

    private fun findExistingEPGFile(directory: File): File? {
        return directory.listFiles()?.find { it.isEpg() }
    }

    private fun File.isPlaylist() = name.endsWith(".m3u", true) || name.endsWith(".m3u8", true)
    private fun File.isEpg() = name.endsWith(".xml", true) || name.endsWith(".xml.gz", true)
}