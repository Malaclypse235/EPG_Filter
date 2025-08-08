package com.example.epgutility

import android.content.Context
import com.google.gson.Gson
import java.io.File

object ConfigManager {

    private const val CONFIG_FILE = "config.json"
    private const val BACKUP_FILE = "config_backup.json"
    private const val PREFS_NAME = "epg_utility_prefs"

    // Wrapper for config load result
    data class LoadResult(
        val config: ConfigData,
        val revokedPermissions: Boolean
    )

    // Main config data model
    data class ConfigData(
        var playlistUri: String? = null,
        var epgUri: String? = null,
        var playlistPath: String? = null,      // Direct file path fallback
        var epgPath: String? = null,           // Direct file path fallback
        var removeNonLatin: Boolean = false,
        var removeNonEnglish: Boolean = false,  // NEW: Stricter English-only filter
        var defaultFolderPath: String? = null,  // Working folder path
        var disablePlaylistFiltering: Boolean = false,
        var disableEPGFiltering: Boolean = false
    )

    fun loadConfig(context: Context): LoadResult {
        val configFile = File(context.filesDir, CONFIG_FILE)
        val config: ConfigData = if (configFile.exists()) {
            try {
                Gson().fromJson(configFile.readText(), ConfigData::class.java)
            } catch (e: Exception) {
                // If main file fails, try backup
                val backupFile = File(context.filesDir, BACKUP_FILE)
                if (backupFile.exists()) {
                    Gson().fromJson(backupFile.readText(), ConfigData::class.java)
                } else {
                    ConfigData()
                }
            }
        } else {
            ConfigData()
        }

        // Check if persisted permissions were revoked
        val revoked = listOf(config.playlistUri, config.epgUri).any { uri ->
            uri != null && !context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == uri
            }
        }

        return LoadResult(config, revoked)
    }

    fun saveConfig(context: Context, config: ConfigData) {
        try {
            val json = Gson().toJson(config)
            File(context.filesDir, CONFIG_FILE).writeText(json)
            File(context.filesDir, BACKUP_FILE).writeText(json)

            println("📂 Saved config and backup in ${context.filesDir}")
        } catch (e: Exception) {
            println("⚠️ Failed to save config: ${e.message}")
        }
    }

    fun clearConfig(context: Context) {
        // Clear any leftover SharedPreferences (if still used)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()

        // Clear file-based entries from both main and backup configs
        fun clearFileEntries(file: File) {
            if (file.exists()) {
                try {
                    val config = Gson().fromJson(file.readText(), ConfigData::class.java)
                    config.playlistUri = null
                    config.playlistPath = null
                    config.epgUri = null
                    config.epgPath = null
                    file.writeText(Gson().toJson(config))
                } catch (_: Exception) { }
            }
        }

        clearFileEntries(File(context.filesDir, CONFIG_FILE))
        clearFileEntries(File(context.filesDir, BACKUP_FILE))
    }
}