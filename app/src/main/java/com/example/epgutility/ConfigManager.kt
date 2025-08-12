package com.example.epgutility

import android.content.Context
import android.os.Environment
import com.google.gson.GsonBuilder
import java.io.File

object ConfigManager {

    private const val CONFIG_FILE = "config.json"
    private const val BACKUP_FILE = "config_backup.json"
    private const val CONFIG_DIR = "configuration"
    private const val PREFS_NAME = "epg_utility_prefs"

    // Wrapper for config load result
    data class LoadResult(
        val config: ConfigData,
        val revokedPermissions: Boolean
    )

    // 🔷 Main configuration container
    data class ConfigData(
        var version: Int = 1, // Future-proof for migrations
        var system: SystemSettings = SystemSettings(),
        var filters: FilterSettings = FilterSettings()
    )

    // 🔧 System-level settings: paths, URIs, app behavior
    data class SystemSettings(
        var defaultFolderPath: String? = null,
        var playlistUri: String? = null,
        var epgUri: String? = null,
        var playlistPath: String? = null,
        var epgPath: String? = null,
        var disablePlaylistFiltering: Boolean = false,
        var disableEPGFiltering: Boolean = false
    )

    // 🧹 Filtering rules (will grow over time)
    // 🧹 Filtering rules (now with news/weather filters)
    data class FilterSettings(
        var removeNonEnglish: Boolean = false,
        var removeNonLatin: Boolean = false,
        var excludeKeywords: List<String> = emptyList(),
        var includeCategories: List<String> = emptyList(),
        var hideEncrypted: Boolean = false,
        var hideRadio: Boolean = false,

        // 🔽 News & Weather Filters
        var removeNewsAndWeather: Boolean = false,
        var removeFoxNews: Boolean = false,
        var removeCNN: Boolean = false,
        var removeMSNBC: Boolean = false,
        var removeNewsMax: Boolean = false,
        var removeCNBC: Boolean = false,
        var removeOAN: Boolean = false,
        var removeWeatherChannel: Boolean = false,
        var removeAccuWeather: Boolean = false,
        var removeDuplicates: Boolean = false
    )

    // Get the configuration directory
    private fun getConfigurationDir(context: Context): File {
        return File(context.filesDir, CONFIG_DIR).apply { mkdirs() }
    }

    private fun getConfigFile(context: Context, filename: String): File {
        return File(getConfigurationDir(context), filename)
    }

    /**
     * Load config from main file, fall back to backup if needed.
     * Also checks if URI permissions were revoked.
     */
    fun loadConfig(context: Context): LoadResult {
        val configFile = getConfigFile(context, CONFIG_FILE)
        val gson = GsonBuilder().create()

        val config: ConfigData = if (configFile.exists()) {
            try {
                gson.fromJson(configFile.readText(), ConfigData::class.java)
            } catch (e: Exception) {
                e.printStackTrace()
                // Try backup
                val backupFile = getConfigFile(context, BACKUP_FILE)
                if (backupFile.exists()) {
                    gson.fromJson(backupFile.readText(), ConfigData::class.java)
                } else {
                    ConfigData() // Return defaults
                }
            }
        } else {
            ConfigData()
        }

        // Ensure defaultFolderPath has a value if unset
        if (config.system.defaultFolderPath.isNullOrEmpty()) {
            config.system.defaultFolderPath = "${Environment.getExternalStorageDirectory()}/Documents/MalEPG"
        }

        // Check if persisted permissions were revoked
        val revoked = listOf(config.system.playlistUri, config.system.epgUri).any { uri ->
            uri != null && !context.contentResolver.persistedUriPermissions.any { perm ->
                perm.uri.toString() == uri
            }
        }

        return LoadResult(config, revoked)
    }

    /**
     * Save current config to both main and backup files (pretty-printed)
     */
    fun saveConfig(context: Context, config: ConfigData) {
        try {
            val gson = GsonBuilder()
                .setPrettyPrinting()
                .create()
            val json = gson.toJson(config)

            val configFile = getConfigFile(context, CONFIG_FILE)
            val backupFile = getConfigFile(context, BACKUP_FILE)

            configFile.writeText(json)
            backupFile.writeText(json)

            println("📂 Saved config and backup to ${configFile.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
            println("⚠️ Failed to save config: ${e.message}")
        }
    }

    /**
     * Clear sensitive URI and path fields (e.g., on logout/reset)
     */
    fun clearConfig(context: Context) {
        // Clear legacy SharedPreferences (if still used)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()

        // Helper to clear and rewrite a config file
        fun clearFileEntries(file: File) {
            if (file.exists()) {
                try {
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    val config = gson.fromJson(file.readText(), ConfigData::class.java)

                    // Clear URIs and paths
                    config.system.playlistUri = null
                    config.system.epgUri = null
                    config.system.playlistPath = null
                    config.system.epgPath = null

                    file.writeText(gson.toJson(config))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        clearFileEntries(getConfigFile(context, CONFIG_FILE))
        clearFileEntries(getConfigFile(context, BACKUP_FILE))
    }
}