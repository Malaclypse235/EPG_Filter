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
        var outputFolderUri: String? = null,
        var outputFolderPath: String? = null,
        var manualFilterSpeed: String? = "balanced",
        var autoFilterSpeed: String? = "balanced",
        var defaultFolderPath: String? = null,
        var playlistUri: String? = null,
        var epgUri: String? = null,
        var playlistPath: String? = null,
        var epgPath: String? = null,
        var disablePlaylistFiltering: Boolean = false,
        var disableEPGFiltering: Boolean = false,
        var outputLocation: String? = "Documents",  // default
        var autoModeEnabled: Boolean = false,
        var autoCheckInterval: String = "24_hours"  // ✅ Default is 24 hours
    )

    // 🧹 Filtering rules (will grow over time)
    // 🧹 Filtering rules (now with news/weather filters)
    data class FilterSettings(
        var removeNonEnglish: Boolean = false,
        var removeNonLatin: Boolean = false,
        var removeSpanish: Boolean = false,
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
        var removeNBCNews: Boolean = false,
        var removeCBSNews: Boolean = false,
        var removeWeatherChannel: Boolean = false,
        var removeAccuWeather: Boolean = false,
        var removeDuplicates: Boolean = false,

        // 🔽 Sports Filters
        var removeSportsChannels: Boolean = false,
        var removeESPN: Boolean = false,
        var removeFoxSports: Boolean = false,
        var removeCBSSports: Boolean = false,
        var removeNBCSports: Boolean = false,
        var removeSportsnet: Boolean = false,
        var removeTNTSports: Boolean = false,
        var removeDAZN: Boolean = false,
        var removeMLBNetwork: Boolean = false,
        var removeNFLNetwork: Boolean = false,
        var removeNBATV: Boolean = false,
        var removeGolfChannel: Boolean = false,
        var removeTennisChannel: Boolean = false,
        var removeSKYSports: Boolean = false,
        var removeNHLChannel: Boolean = false,
        var removeBigTenNetwork: Boolean = false,

// 🔽 Sports by Type
        var removeBaseball: Boolean = false,
        var removeBasketball: Boolean = false,
        var removeFootball: Boolean = false,
        var removeSoccer: Boolean = false,
        var removeHockey: Boolean = false,
        var removeGolf: Boolean = false,
        var removeFishing: Boolean = false,
        var removeUFCMMA: Boolean = false,
        var removeBoxing: Boolean = false,
        var removeSwimming: Boolean = false,

        // 🔽 Music Filters
        var removeMusicChannels: Boolean = false,
        var removeBETJams: Boolean = false,
        var removeBETSoul: Boolean = false,
        var removeBPM: Boolean = false,
        var removeCMT: Boolean = false,
        var removeClublandTV: Boolean = false,
        var removeDaVinciMusic: Boolean = false,
        var removeDanceMusicTV: Boolean = false,
        var removeFlaunt: Boolean = false,
        var removeFuse: Boolean = false,
        var removeGospelMusicChannel: Boolean = false,
        var removeHeartTV: Boolean = false,
        var removeJuice: Boolean = false,
        var removeJukebox: Boolean = false,
        var removeKerrangTV: Boolean = false,
        var removeKissTV: Boolean = false,
        var removeLiteTV: Boolean = false,
        var removeLoudTV: Boolean = false,
        var removeMTV: Boolean = false,
        var removePulse: Boolean = false,
        var removeQVCMusic: Boolean = false,
        var removeRevolt: Boolean = false,
        var removeRIDEtv: Boolean = false,
        var removeStingray: Boolean = false,
        var removeTheBox: Boolean = false,
        var removeTrace: Boolean = false,
        var removeVevo: Boolean = false,

// 🔽 Music by Genre
        var removeAcappella: Boolean = false,
        var removeAcoustic: Boolean = false,
        var removeAlternative: Boolean = false,
        var removeAmbient: Boolean = false,
        var removeBollywood: Boolean = false,
        var removeChildrensMusic: Boolean = false,
        var removeChristianMusic: Boolean = false,
        var removeClassical: Boolean = false,
        var removeClassicRock: Boolean = false,
        var removeCountry: Boolean = false,
        var removeDance: Boolean = false,
        var removeDisco: Boolean = false,
        var removeEasyListening: Boolean = false,
        var removeElectronic: Boolean = false,
        var removeFolk: Boolean = false,
        var removeGospel: Boolean = false,
        var removeGrunge: Boolean = false,
        var removeHardRock: Boolean = false,
        var removeHipHop: Boolean = false,
        var removeHolidayMusic: Boolean = false,
        var removeIndie: Boolean = false,
        var removeJazz: Boolean = false,
        var removeKaraoke: Boolean = false,
        var removeLatin: Boolean = false,
        var removeLatinPop: Boolean = false,
        var removeLofi: Boolean = false,
        var removeMetal: Boolean = false,
        var removeNewAge: Boolean = false,
        var removeOpera: Boolean = false,
        var removePop: Boolean = false,
        var removePunk: Boolean = false,
        var removeRnB: Boolean = false,
        var removeRap: Boolean = false,
        var removeReggae: Boolean = false,
        var removeRock: Boolean = false,
        var removeTechno: Boolean = false,
        var removeTrance: Boolean = false,
        var removeTriphop: Boolean = false,
        var removeWorldMusic: Boolean = false

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