package com.example.epgutility

import android.content.Context
import android.os.Environment
import com.google.gson.GsonBuilder
import java.io.File

// Utils.kt or inside ConfigManager.kt
fun String.toAutoIntervalMillis(): Long {
    return when (this) {
        // "30_min" -> 30 * 60 * 1000L  // 30 minutes
        "30_min" -> 30 * 60 * 1000L  // 15 min temp for testing
        "1_hour" -> 60 * 60 * 1000L  // 1 hour
        "6_hours" -> 6 * 60 * 60 * 1000L
        "12_hours" -> 12 * 60 * 60 * 1000L
        "24_hours" -> 24 * 60 * 60 * 1000L
        else -> 24 * 60 * 60 * 1000L  // default to 24 hours
    }
}
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
        var autoCheckInterval: String = "24_hours",  // ✅ Default is 24 hours
        var forceSyncPlaylist: Boolean = false,
        var forceSyncEpg: Boolean = false
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
        var removeFifa: Boolean = false,
        var removeF1: Boolean = false,
        var removeXfc: Boolean = false,
        var removeLucha: Boolean = false,
        var removeSport: Boolean = false,

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
        var removeWorldMusic: Boolean = false,

        // 🔽 Young Children Filters
        var removeAllYoungChildren: Boolean = false,
        var removeNickJr: Boolean = false,
        var removeDisneyJunior: Boolean = false,
        var removePBSKids: Boolean = false,
        var removeUniversalKids: Boolean = false,
        var removeBabyTV: Boolean = false,
        var removeBoomerang: Boolean = false,
        var removeCartoonito: Boolean = false,

// 🔽 Young Children Keywords
        var removePawPatrol: Boolean = false,
        var removeDoraTV: Boolean = false,
        var removeMaxAndRuby: Boolean = false,
        var removeArthur: Boolean = false,
        var removeTheWiggles: Boolean = false,
        var removeSpongeBob: Boolean = false,
        var removeRetroKid: Boolean = false,
        var removeRetroToons: Boolean = false,
        var removeTeletubbies: Boolean = false,
        var removeBobTheBuilder: Boolean = false,
        var removeInspectorGadget: Boolean = false,
        var removeBarneyAndFriends: Boolean = false,
        var removeBarbieAndFriends: Boolean = false,
        var removeHotwheels: Boolean = false,
        var removeMoonbug: Boolean = false,
        var removeBlippi: Boolean = false,
        var removeCaillou: Boolean = false,
        var removeFiremanSam: Boolean = false,
        var removeBabyEinstein: Boolean = false,
        var removeStrawberryShortcake: Boolean = false,
        var removeHappyKids: Boolean = false,
        var removeShaunTheSheep: Boolean = false,
        var removeRainbowRuby: Boolean = false,
        var removeZoomoo: Boolean = false,
        var removeRevAndRoll: Boolean = false,
        var removeTgJunior: Boolean = false,
        var removeDuckTV: Boolean = false,
        var removeSensicalJr: Boolean = false,
        var removeKartoonChannel: Boolean = false,
        var removeRyanAndFriends: Boolean = false,
        var removeNinjaKids: Boolean = false,
        var removeToonGoggles: Boolean = false,
        var removeKidoodle: Boolean = false,
        var removePeppaPig: Boolean = false,
        var removeBbcKids: Boolean = false,
        var removeLittleStarsUniverse: Boolean = false,
        var removeLittleAngelsPlayground: Boolean = false,
        var removeBabyShark: Boolean = false,
        var removeKidsAnime: Boolean = false,
        var removeGoGoGadget: Boolean = false,
        var removeForeverKids: Boolean = false,
        var removeLoolooKids: Boolean = false,
        var removePocoyo: Boolean = false,
        var removeKetchupTV: Boolean = false,
        var removeSupertoonsTV: Boolean = false,
        var removeYaaas: Boolean = false,
        var removeSmurfTV: Boolean = false,
        var removeBratTV: Boolean = false,
        var removeCampSnoopy: Boolean = false,
        var removeKidzBop: Boolean = false,

        // 🔽 Reality Shows Filters
        var removeAllRealityShows: Boolean = false,
        var removeAE: Boolean = false,
        var removeBravo: Boolean = false,
        var removeE: Boolean = false,
        var removeLifetime: Boolean = false,
        var removeOWN: Boolean = false,
        var removeTLC: Boolean = false,
        var removeVH1: Boolean = false,
        var removeWEtv: Boolean = false,
        var removeMTVReality: Boolean = false,

// 🔽 Reality Shows Keywords
        var remove90DayFiancee: Boolean = false,
        var removeAmericanIdol: Boolean = false,
        var removeAmazingRace: Boolean = false,
        var removeBigBrother: Boolean = false,
        var removeChallenge: Boolean = false,
        var removeDancingWithTheStars: Boolean = false,
        var removeDragRace: Boolean = false,
        var removeDuckDynasty: Boolean = false,
        var removeFlipOrFlop: Boolean = false,
        var removeGordonRamsay: Boolean = false,
        var removeHellSKitchen: Boolean = false,
        var removeJerseyShore: Boolean = false,
        var removeJudge: Boolean = false,
        var removeKardashians: Boolean = false,
        var removeLoveIsBlind: Boolean = false,
        var removeLoveItOrListIt: Boolean = false,
        var removeLoveIsland: Boolean = false,
        var removeMasterchef: Boolean = false,
        var removeMillionDollarListing: Boolean = false,
        var removeNosey: Boolean = false,
        var removePerfectMatch: Boolean = false,
        var removePawnStars: Boolean = false,
        var removePropertyBrothers: Boolean = false,
        var removeQueerEye: Boolean = false,
        var removeRealHousewives: Boolean = false,
        var removeSharkTank: Boolean = false,
        var removeSinglesInferno: Boolean = false,
        var removeStorageWars: Boolean = false,
        var removeSurvivor: Boolean = false,
        var removeTheBachelor: Boolean = false,
        var removeTheBachelorette: Boolean = false,
        var removeTheMaskedSinger: Boolean = false,
        var removeTheOsbournes: Boolean = false,
        var removeTheUltimatum: Boolean = false,
        var removeTheVoice: Boolean = false,
        var removeTopModel: Boolean = false,
        var removeTeenMom: Boolean = false,
        var removeTooHotToHandle: Boolean = false,
        var removeVanderpumpRules: Boolean = false

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