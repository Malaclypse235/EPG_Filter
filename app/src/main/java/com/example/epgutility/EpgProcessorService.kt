package com.example.epgutility

import android.app.*
import android.app.NotificationManager
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import android.content.ContentUris


class EpgProcessorService : Service() {

    companion object {
        // Main UI/Service constants
        const val CHANNELS = "📺"
        const val DONE = "✅"
        const val ACTION_START_EPG_PROCESSING = "START_EPG_PROCESSING"
        const val ACTION_STOP_EPG_PROCESSING = "STOP_EPG_PROCESSING"
        const val ACTION_GET_PROGRESS = "GET_EPG_PROGRESS"
        const val ACTION_START_AUTO_EPG_PROCESSING = "START_AUTO_EPG_PROCESSING"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "epg_processor"
        private const val TAG = "EpgProcessorService"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"

        // Message types for UI handling
        const val MSG_STATUS = "STATUS"
        const val MSG_PROGRESS_M3U = "PROGRESS_M3U"
        const val MSG_PROGRESS_CHANNELS = "PROGRESS_CHANNELS"
        const val MSG_LOG = "LOG"

        // ✅ Throttle delay constants (in milliseconds)
        const val DELAY_FULL = 0L      // No pause
        const val DELAY_BALANCED = 1L  // Light pause
        const val DELAY_SLOW = 5L      // Noticeable pause

        // ✅ Auto Mode throttle constants (slower than manual)
        const val DELAY_AUTO_FULL = 1L      // Still gentle
        const val DELAY_AUTO_BALANCED = 4L  // Noticeable pause
        const val DELAY_AUTO_SLOW = 8L      // Very gentle on system
    }

    private var isProcessing = false
    private var processingThread: Thread? = null
    private var isPaused = false  // Tracks whether processing is paused
    private var isAutoMode = false

    // Progress tracking
    private var currentProgress = 0
    private var currentStatus = "Idle"
    private var totalChannels = 0
    private var processedChannels = 0
    private var totalProgrammes = 0
    private var currentPhase = "Idle"

    // --- Filtering State ---
    private lateinit var config: ConfigManager.ConfigData
    private val nonLatinPattern = Pattern.compile("[^\\u0000-\\u00FF\\u2010-\\u2027\\u02B0-\\u02FF]")
    private val channelFilterDecisions = HashMap<String, Boolean>()

    // Counters
    private var keptChannelsCount = 0
    private var removedChannelsCount = 0

    // ✅ NEW: Store paths from intent
    private var playlistPath: String? = null
    private var epgPath: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EPG_PROCESSING -> {
                this.playlistPath = intent.getStringExtra("PLAYLIST_PATH")
                this.epgPath = intent.getStringExtra("EPG_PATH")
                this.isAutoMode = false
                startEpgProcessing()
            }
            ACTION_START_AUTO_EPG_PROCESSING -> {
                this.playlistPath = intent.getStringExtra("PLAYLIST_PATH")
                this.epgPath = intent.getStringExtra("EPG_PATH")
                this.isAutoMode = true
                startEpgProcessing()
            }
            ACTION_STOP_EPG_PROCESSING -> stopEpgProcessing()
            ACTION_PAUSE -> pauseEpgProcessing()       // ← ADD THIS
            ACTION_RESUME -> resumeEpgProcessing()     // ← ADD THIS
            ACTION_GET_PROGRESS -> sendCurrentProgress()
            else -> Log.w(TAG, "Unknown action: ${intent?.action}")
        }
        return START_STICKY
    }

    private fun resetState() {
        currentProgress = 0
        currentStatus = "Idle"
        currentPhase = "Idle"
        keptChannelsCount = 0
        removedChannelsCount = 0
        processedChannels = 0
        totalChannels = 0
        totalProgrammes = 0
        channelFilterDecisions.clear()
    }

    private fun startEpgProcessing() {
        if (isProcessing) return
        isProcessing = true
        resetState()

        try {
            val loadResult = ConfigManager.loadConfig(this)
            config = loadResult.config

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("Starting..."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load config or start service", e)
            stopEpgProcessing()
            return
        }

        processingThread = Thread { processEpgInBackground() }
        processingThread?.start()
    }

    private fun stopEpgProcessing() {
        isProcessing = false
        currentProgress = 0
        currentStatus = "Stopped"
        processingThread?.interrupt()
        try {
            Thread.sleep(100)
            stopForeground(true)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground", e)
        }
        stopSelf()
    }

    private fun sendCurrentProgress() {
        sendProgressUpdate(
            message = currentStatus,
            percentage = currentProgress,
            totalChannels = totalChannels,
            processedChannels = processedChannels,
            totalProgrammes = totalProgrammes,
            processedProgrammes = 0,
            phase = currentPhase,
            messageType = MSG_STATUS
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "EPG Processing",
            NotificationManager.IMPORTANCE_MIN  // 🔽 Most unobtrusive
        ).apply {
            description = "Background EPG filtering"
            setShowBadge(false)
            setSound(null, null)
            enableLights(false)
            enableVibration(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(text: String): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Processing EPG File")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun processM3uFile(playlistFile: File) {
        if (!isProcessing) {
            logAndSend("⏩ M3U processing skipped: was canceled", 0, "M3U_Skipped", MSG_LOG)
            return
        }

        val outputDir = File(this.filesDir, "output").apply { mkdirs() }
        // ✅ Correct: internal file should be kept_channels.m3u
        val keptFile = File(outputDir, "kept_channels.m3u")
        val removedFile = File(outputDir, "removed_channels.m3u")
        val total = countExtinfLines(playlistFile)
        this.totalChannels = total
        var processed = 0
        var kept = 0
        var removed = 0

        val seenTvgIds = mutableSetOf<String>()

        keptFile.bufferedWriter().use { keptWriter ->
            removedFile.bufferedWriter().use { removedWriter ->
                playlistFile.bufferedReader().use { reader ->
                    var line: String?
                    var currentChannel = mutableListOf<String>()
                    var headerWritten = false

                    // - Progress updater thread -
                    var lastProgressUpdate = System.currentTimeMillis()
                    val PROGRESS_UPDATE_INTERVAL = 500L
                    val progressUpdater = object : Thread() {
                        override fun run() {
                            try {
                                while (!isInterrupted && isProcessing && processed < total) {
                                    // ✅ Pause support
                                    while (isPaused && isProcessing && processed < total) {
                                        try {
                                            Thread.sleep(100)
                                        } catch (e: InterruptedException) {
                                            break
                                        }
                                    }
                                    if (isPaused) continue

                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                                        val progress = (processed.toLong() * 100 / total.coerceAtLeast(1)).toInt()
                                        logAndSend("Filtering: $processed / $total", progress, "M3U_Filtering", MSG_PROGRESS_M3U)
                                        lastProgressUpdate = now
                                    }
                                    Thread.sleep(500)
                                }
                            } catch (e: InterruptedException) { }
                        }
                    }.apply { start() }

                    // - Main filtering loop -
                    while (reader.readLine().also { line = it } != null && isProcessing) {
                        val trimmed = line?.trim() ?: continue

                        if (trimmed.startsWith("#EXTM3U")) {
                            if (!headerWritten) {
                                keptWriter.write("$trimmed\n")
                                removedWriter.write("$trimmed\n")
                                headerWritten = true
                            }
                            continue
                        }

                        if (trimmed.startsWith("#EXTGRP")) {
                            keptWriter.write("$trimmed\n")
                            removedWriter.write("$trimmed\n")
                            continue
                        }

                        if (trimmed.startsWith("#EXTINF:")) {
                            if (currentChannel.size >= 2) {
                                processed++
                                val tvgId = extractTvgId(currentChannel[0])
                                val isDuplicate = tvgId != null && config.filters.removeDuplicates && seenTvgIds.contains(tvgId)
                                val shouldKeep = if (config.filters.removeDuplicates && isDuplicate) {
                                    false
                                } else {
                                    shouldKeepM3uChannel(currentChannel)
                                }

                                if (tvgId != null && shouldKeep) {
                                    seenTvgIds.add(tvgId)
                                }

                                if (shouldKeep) {
                                    kept++
                                    keptWriter.write("${currentChannel[0]}\n${currentChannel[1].trimEnd()}\n")
                                } else {
                                    removed++
                                    removedWriter.write("${currentChannel[0]}\n${currentChannel[1].trimEnd()}\n")
                                }
                                currentChannel.clear()
                            }
                            currentChannel.add(trimmed)
                        } else if (!trimmed.startsWith("#") && currentChannel.size == 1) {
                            currentChannel.add(trimmed)
                        }

                        // ✅ Pause support: add this block here
                        while (isPaused && isProcessing && processed < total) {
                            try {
                                Thread.sleep(100)
                            } catch (e: InterruptedException) {
                                break
                            }
                        }

                        // ✅ Throttle: pause based on speed
                        val delay = getThrottleDelay()
                        if (delay > 0) Thread.sleep(delay)
                    }

                    // Stop progress updater
                    progressUpdater.interrupt()
                    try {
                        progressUpdater.join(100)
                    } catch (e: InterruptedException) { }

                    val progress = (processed.toLong() * 100 / total.coerceAtLeast(1)).toInt()
                    logAndSend("Filtering: $processed / $total", progress, "M3U_Filtering", MSG_PROGRESS_M3U)
                    val result = "✅ M3U: $kept kept, $removed removed"
                    logAndSend(result, 100, "M3U_Done", MSG_LOG)
                }
            }
        }
    }

    // ✅ Helper to extract tvg-id from #EXTINF line
    private fun extractTvgId(extinfLine: String): String? {
        val regex = Regex("""tvg-id=["']?([^"'\s>]+)""")
        return regex.find(extinfLine)?.groupValues?.get(1)
    }

    private fun processEpgInBackground() {
        var m3uSuccess = false
        var epgSuccess = false
        try {
            val inputDir = File(this.filesDir, "input")
            if (!inputDir.exists()) {
                logAndSend("❌ Input folder missing", 0, "Error", MSG_LOG)
                return
            }

            // ✅ DECLARE playlistFile — this was missing
            val playlistFile = if (!this.playlistPath.isNullOrEmpty() && !config.system.disablePlaylistFiltering) {
                val file = File(this.playlistPath!!)
                if (file.exists()) file else null
            } else {
                null
            }

            // ✅ DECLARE epgFile — this was missing
            val epgFile = if (!this.epgPath.isNullOrEmpty() && !config.system.disableEPGFiltering) {
                val file = File(this.epgPath!!)
                if (file.exists()) file else null
            } else {
                null
            }

            // - Step 1: M3U Filtering -
            if (playlistFile != null) {
                logAndSend("\uD83D\uDCE1 Starting M3U filtering...", 0, "M3U_Start", MSG_LOG)
                processM3uFile(playlistFile)
                if (isProcessing) m3uSuccess = true
            } else {
                logAndSend("⚠\uFE0F Skipped M3U filtering", 0, "M3U_Skipped", MSG_LOG)
            }

            // - Step 2: EPG Filtering -
            if (epgFile != null) {
                logAndSend("📺 Starting EPG filtering...", 5, "EPG_Start", MSG_LOG)
                countElementsInFile(epgFile)
                processEpgFile(epgFile)
                if (isProcessing) epgSuccess = true
            } else {
                logAndSend("⚠\uFE0F Skipped EPG filtering", 0, "EPG_Skipped", MSG_LOG)
            }

            // ✅ Only export if successful and not canceled
            if (m3uSuccess) {
                val keptM3u = File(filesDir, "output/kept_channels.m3u")
                if (keptM3u.exists()) {
                    exportToFile(keptM3u, "filtered_playlist.m3u")
                }
            }

            if (epgSuccess) {
                val keptXml = File(filesDir, "output/kept_channels.xml")
                if (keptXml.exists()) {
                    exportToFile(keptXml, "filtered_epg.xml")
                }
            }

            // ✅ Only log "complete" if not canceled
            if (m3uSuccess || epgSuccess) {
                logAndSend("✅ All processing complete!", 100, "Complete", MSG_LOG)
            }

        } catch (e: Exception) {
            Log.e(TAG, "💥 Critical error", e)
            logAndSend("‼\uFE0F Processing failed: ${e.message}", 0, "Error", MSG_LOG)
            writeCrashLog(e)
        } finally {
            stopEpgProcessing()
        }
    }

    private fun countExtinfLines(file: File): Int {
        return try {
            file.bufferedReader().use { reader ->
                var count = 0
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.trim()?.startsWith("#EXTINF:") == true) {
                        count++
                    }
                }
                count
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to count EXTINF lines", e)
            0
        }
    }

    private fun shouldKeepM3uChannel(channelLines: List<String>): Boolean {
        val extinfLine = channelLines.getOrNull(0) ?: return true
        val channelNameLine = channelLines.getOrNull(1) ?: return true

        // Extract text to search: EXTINF attributes + visible name
        val searchText = buildString {
            append(extinfLine.lowercase())
            append(" ")
            append(channelNameLine.trim().lowercase())
        }

        // 1. General filters: Non-English / Non-Latin
        if (config.filters.removeNonEnglish) {
            if (Regex("[^\\u0020-\\u007E\\u2010-\\u2027]").find(searchText) != null) return false
        }
        if (config.filters.removeNonLatin) {
            if (nonLatinPattern.matcher(searchText).find()) return false
        }

        // 2. Exclude custom keywords
        if (config.filters.excludeKeywords.isNotEmpty()) {
            if (config.filters.excludeKeywords.any { it.lowercase() in searchText }) return false
        }

        // 🌐 3. Define Spanish Keywords List
        val spanishWords = listOf(
            "cine",       // not in "cinema"
            "novela",     // not in "novel"
            "telenovela",
            "deporte",    // not in "importante"
            "noticias",
            "canal",
            "en vivo",
            "enlace",
            "futbol",     // not in "football"
            "baloncesto",
            "beisbol",
            "tenis",
            "musica",
            "programa",
            "serie",
            "pelicula",
            "noticia",
            "clima",
            "tiempo",
            "hispano",
            "latino",
            "espanol",
            "spanish",
            "el",
            "la",
            "gratis",
            "estrella",
            "azteca",
            "vix",
            "caso",
            "casa",
            "luz",
            "amor",
            "oro",
            "grandes",
            "y",
            "estelar",
            "canela",
            "cielo",
            "misterios"
        )
        // 🌐 Remove Spanish Channels
        if (config.filters.removeSpanish) {
            if (spanishWords.any { matchesWholeWord(searchText, it) }) return false
        }
        // 3. Hide Radio
        if (config.filters.hideRadio) {
            if ("radio" in searchText || "music" in searchText) return false
        }

        // 4. Hide Encrypted (very basic — improve if needed)
        if (config.filters.hideEncrypted) {
            if ("drm" in searchText || "encrypt" in searchText) return false
        }

        // 🔥 5. News & Weather Master Filter
        if (config.filters.removeNewsAndWeather) {
            // Check for generic terms
            if (listOf("news", "weather").any { it in searchText }) return false

            // Also apply all sub-filters if master is enabled
            val newsPatterns = listOf(
                "fox news", "cnn", "msnbc", "newsmax", "cnbc", "oan",
                "weather channel", "accuweather", "nbc news", "cbs news"
            )
            if (newsPatterns.any { it in searchText }) return false
        } else {
            // Master is OFF → apply sub-filters individually
            if (config.filters.removeFoxNews && "fox news" in searchText) return false
            if (config.filters.removeCNN && "cnn" in searchText) return false
            if (config.filters.removeMSNBC && "msnbc" in searchText) return false
            if (config.filters.removeNewsMax && "newsmax" in searchText) return false
            if (config.filters.removeCNBC && "cnbc" in searchText) return false
            if (config.filters.removeOAN && "oan" in searchText) return false
            if (config.filters.removeNBCNews && "nbc news" in searchText) return false
            if (config.filters.removeCBSNews && "cbs news" in searchText) return false
            if (config.filters.removeWeatherChannel && ("weather channel" in searchText || "weather." in searchText)) return false
            if (config.filters.removeAccuWeather && "accuweather" in searchText) return false
        }

        // ⚽ Sports Filters
        if (config.filters.removeSportsChannels) {
            val sportsKeywords = listOf(
                // Sports Networks
                "espn", "fox sports", "cbs sports", "nbc sports",
                "sportsnet", "tnt sports", "dazn", "mlb network",
                "nfl network", "nba tv", "golf channel", "tennis channel",
                "sky sports", "nhl channel", "big 10 network", "btn",

                // Sports by Type
                "baseball", "basketball", "football", "soccer", "futbol", "fútbol",
                "hockey", "golf", "fishing", "ufc", "mma", "boxing", "swimming",
                "premier league", "la liga", "pga", "nhl", "nba", "nfl", "mlb", "college football"
            )
            if (sportsKeywords.any { it in searchText }) return false
        } else {
            // Apply individual network filters
            if (config.filters.removeESPN && "espn" in searchText) return false
            if (config.filters.removeFoxSports && "fox sports" in searchText) return false
            if (config.filters.removeCBSSports && "cbs sports" in searchText) return false
            if (config.filters.removeNBCSports && "nbc sports" in searchText) return false
            if (config.filters.removeSportsnet && "sportsnet" in searchText) return false
            if (config.filters.removeTNTSports && "tnt sports" in searchText) return false
            if (config.filters.removeDAZN && "dazn" in searchText) return false
            if (config.filters.removeMLBNetwork && "mlb network" in searchText) return false
            if (config.filters.removeNFLNetwork && "nfl network" in searchText) return false
            if (config.filters.removeNBATV && "nba tv" in searchText) return false
            if (config.filters.removeGolfChannel && "golf channel" in searchText) return false
            if (config.filters.removeTennisChannel && "tennis channel" in searchText) return false
            if (config.filters.removeSKYSports && "sky sports" in searchText) return false
            if (config.filters.removeNHLChannel && "nhl channel" in searchText) return false
            if (config.filters.removeBigTenNetwork && ("big 10 network" in searchText || "btn" in searchText)) return false
        }

// 🏈 Sports by Type
        if (config.filters.removeBaseball && ("baseball" in searchText || "mlb" in searchText)) return false
        if (config.filters.removeBasketball && ("basketball" in searchText || "nba" in searchText)) return false
        if (config.filters.removeFootball && ("football" in searchText || "nfl" in searchText || "college football" in searchText)) return false
        if (config.filters.removeSoccer && ("soccer" in searchText || "futbol" in searchText || "fútbol" in searchText || "premier league" in searchText || "la liga" in searchText)) return false
        if (config.filters.removeHockey && ("hockey" in searchText || "nhl" in searchText)) return false
        if (config.filters.removeGolf && ("golf" in searchText || "pga" in searchText)) return false
        if (config.filters.removeFishing && "fishing" in searchText) return false
        if (config.filters.removeBoxing && "boxing" in searchText) return false
        if (config.filters.removeSwimming && ("swimming" in searchText || "aquatics" in searchText)) return false
        if (config.filters.removeUFCMMA && ("ufc" in searchText || "mma" in searchText)) return false  // ✅ Checks both

        // 🎵 Music Filters
        if (config.filters.removeMusicChannels) {
            val musicKeywords = listOf(
                "bet jams", "bet soul", "bpm", "cmt", "clubland", "da vinci music",
                "dance music tv", "flaunt", "fuse", "gospel music", "heart tv",
                "juice", "jukebox", "kerrang", "kiss tv", "lite tv", "loud tv",
                "mtv", "pulse", "qvc music", "revolt", "ride tv", "stingray",
                "the box", "trace", "vevo",
                "a cappella", "acoustic", "alternative", "ambient", "bollywood",
                "children's music", "christian music", "classical", "classic rock",
                "country", "dance", "disco", "easy listening", "electronic", "folk",
                "gospel", "grunge", "hard rock", "hip hop", "holiday music", "indie",
                "jazz", "karaoke", "latin", "latin pop", "lofi", "metal", "new age",
                "opera", "pop", "punk", "r&b", "rap", "reggae", "rock", "techno",
                "trance", "trip hop", "world music"
            )
            if (musicKeywords.any { it in searchText }) return false
        } else {
            // Apply individual network filters
            if (config.filters.removeBETJams && "bet jams" in searchText) return false
            if (config.filters.removeBETSoul && "bet soul" in searchText) return false
            if (config.filters.removeBPM && "bpm" in searchText) return false
            if (config.filters.removeCMT && "cmt" in searchText) return false
            if (config.filters.removeClublandTV && "clubland" in searchText) return false
            if (config.filters.removeDaVinciMusic && "da vinci music" in searchText) return false
            if (config.filters.removeDanceMusicTV && "dance music tv" in searchText) return false
            if (config.filters.removeFlaunt && "flaunt" in searchText) return false
            if (config.filters.removeFuse && "fuse" in searchText) return false
            if (config.filters.removeGospelMusicChannel && "gospel music" in searchText) return false
            if (config.filters.removeHeartTV && "heart tv" in searchText) return false
            if (config.filters.removeJuice && "juice" in searchText) return false
            if (config.filters.removeJukebox && "jukebox" in searchText) return false
            if (config.filters.removeKerrangTV && "kerrang" in searchText) return false
            if (config.filters.removeKissTV && "kiss tv" in searchText) return false
            if (config.filters.removeLiteTV && "lite tv" in searchText) return false
            if (config.filters.removeLoudTV && "loud tv" in searchText) return false
            if (config.filters.removeMTV && "mtv" in searchText) return false
            if (config.filters.removePulse && "pulse" in searchText) return false
            if (config.filters.removeQVCMusic && "qvc music" in searchText) return false
            if (config.filters.removeRevolt && "revolt" in searchText) return false
            if (config.filters.removeRIDEtv && "ride tv" in searchText) return false
            if (config.filters.removeStingray && "stingray" in searchText) return false
            if (config.filters.removeTheBox && "the box" in searchText) return false
            if (config.filters.removeTrace && "trace" in searchText) return false
            if (config.filters.removeVevo && "vevo" in searchText) return false

            // Apply individual genre filters
            if (config.filters.removeAcappella && "a cappella" in searchText) return false
            if (config.filters.removeAcoustic && "acoustic" in searchText) return false
            if (config.filters.removeAlternative && "alternative" in searchText) return false
            if (config.filters.removeAmbient && "ambient" in searchText) return false
            if (config.filters.removeBollywood && "bollywood" in searchText) return false
            if (config.filters.removeChildrensMusic && "children's music" in searchText) return false
            if (config.filters.removeChristianMusic && "christian music" in searchText) return false
            if (config.filters.removeClassical && "classical" in searchText) return false
            if (config.filters.removeClassicRock && "classic rock" in searchText) return false
            if (config.filters.removeCountry && "country" in searchText) return false
            if (config.filters.removeDance && "dance" in searchText) return false
            if (config.filters.removeDisco && "disco" in searchText) return false
            if (config.filters.removeEasyListening && "easy listening" in searchText) return false
            if (config.filters.removeElectronic && "electronic" in searchText) return false
            if (config.filters.removeFolk && "folk" in searchText) return false
            if (config.filters.removeGospel && "gospel" in searchText) return false
            if (config.filters.removeGrunge && "grunge" in searchText) return false
            if (config.filters.removeHardRock && "hard rock" in searchText) return false
            if (config.filters.removeHipHop && "hip hop" in searchText) return false
            if (config.filters.removeHolidayMusic && "holiday music" in searchText) return false
            if (config.filters.removeIndie && "indie" in searchText) return false
            if (config.filters.removeJazz && "jazz" in searchText) return false
            if (config.filters.removeKaraoke && "karaoke" in searchText) return false
            if (config.filters.removeLatin && "latin" in searchText) return false
            if (config.filters.removeLatinPop && "latin pop" in searchText) return false
            if (config.filters.removeLofi && "lofi" in searchText) return false
            if (config.filters.removeMetal && "metal" in searchText) return false
            if (config.filters.removeNewAge && "new age" in searchText) return false
            if (config.filters.removeOpera && "opera" in searchText) return false
            if (config.filters.removePop && "pop" in searchText) return false
            if (config.filters.removePunk && "punk" in searchText) return false
            if (config.filters.removeRnB && "r&b" in searchText) return false
            if (config.filters.removeRap && "rap" in searchText) return false
            if (config.filters.removeReggae && "reggae" in searchText) return false
            if (config.filters.removeRock && "rock" in searchText) return false
            if (config.filters.removeTechno && "techno" in searchText) return false
            if (config.filters.removeTrance && "trance" in searchText) return false
            if (config.filters.removeTriphop && "trip hop" in searchText) return false
            if (config.filters.removeWorldMusic && "world music" in searchText) return false
        }

        return true
    }



    private fun processEpgFile(epgFile: File) {
        if (!isProcessing) {
            logAndSend("⏩ EPG processing skipped: was canceled", 0, "EPG_Skipped", MSG_LOG)
            return
        }
        val outputDir = File(this.filesDir, "output").apply { mkdirs() }

        var inputStream: InputStream? = null
        var keptWriter: BufferedWriter? = null
        var removedWriter: BufferedWriter? = null
        var tvClosed = false

        try {
            val keptFile = File(outputDir, "kept_channels.xml")
            val removedFile = File(outputDir, "removed_channels.xml")

            inputStream = if (epgFile.name.endsWith(".gz", true)) {
                GZIPInputStream(FileInputStream(epgFile))
            } else {
                FileInputStream(epgFile)
            }

            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(inputStream, "UTF-8")

            keptWriter = keptFile.bufferedWriter()
            removedWriter = removedFile.bufferedWriter()

            val xmlDeclaration = readXmlDeclaration(epgFile)
            keptWriter.write("$xmlDeclaration\n")
            removedWriter.write("$xmlDeclaration\n")

            var insideChannel = false
            var insideProgramme = false
            var currentChannelId: String? = null
            var shouldKeepCurrentChannel = true
            val channelBuffer = StringBuilder()
            var programmeBuffer = StringBuilder()
            var tvRootWritten = false

            var lastProgressUpdate = System.currentTimeMillis()
            val PROGRESS_UPDATE_INTERVAL = 500L

            val progressUpdater = object : Thread() {
                override fun run() {
                    try {
                        while (!isInterrupted && isProcessing && processedChannels < totalChannels) {
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                                // ✅ Skip sending "Filtering: x / y" if paused
                                if (isPaused) continue

                                val progress = (processedChannels.toLong() * 100 / totalChannels.coerceAtLeast(1)).toInt()
                                logAndSend("Filtering: $processedChannels / $totalChannels", progress, "EPG_Channels", MSG_PROGRESS_CHANNELS)
                                lastProgressUpdate = now
                            }
                            Thread.sleep(1000)
                        }
                    } catch (e: InterruptedException) { }
                }
            }.apply { start() }

            // ✅ Track seen and duplicate channel IDs
            val seenChannelIds = mutableSetOf<String>()
            val duplicateChannelIds = mutableSetOf<String>()

            while (parser.eventType != XmlPullParser.END_DOCUMENT && isProcessing) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "tv" -> {
                                if (!tvRootWritten) {
                                    val tvElement = buildXmlStartTag(parser)
                                    keptWriter.write("$tvElement\n")
                                    removedWriter.write("$tvElement\n")
                                    tvRootWritten = true
                                }
                            }
                            "channel" -> {
                                insideChannel = true
                                currentChannelId = parser.getAttributeValue(null, "id") ?: "unknown"
                                processedChannels++
                                if (config.filters.removeDuplicates) {
                                    if (currentChannelId in seenChannelIds) {
                                        duplicateChannelIds.add(currentChannelId)
                                    } else {
                                        seenChannelIds.add(currentChannelId)
                                    }
                                }
                                channelBuffer.setLength(0)
                                channelBuffer.append(buildXmlStartTag(parser))
                            }
                            "programme" -> {
                                insideProgramme = true
                                val channelId = parser.getAttributeValue(null, "channel") ?: "unknown"
                                shouldKeepCurrentChannel = channelFilterDecisions.getOrDefault(channelId, true)
                                programmeBuffer.setLength(0)
                                programmeBuffer.append(buildXmlStartTag(parser))
                            }
                            else -> {
                                val element = buildXmlStartTag(parser)
                                val tagName = parser.name.trim()
                                if (tagName.equals("tv", ignoreCase = true)) {
                                    // Skip
                                } else if (insideChannel) {
                                    channelBuffer.append(element)
                                } else if (insideProgramme) {
                                    programmeBuffer.append(element)
                                } else {
                                    keptWriter.write(element)
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text ?: ""
                        val escaped = escapeXmlText(text)
                        if (insideChannel) {
                            channelBuffer.append(escaped)
                        } else if (insideProgramme) {
                            programmeBuffer.append(escaped)
                        } else {
                            keptWriter.write(escaped)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                insideChannel = false
                                channelBuffer.append("</channel>\n")
                                val channelText = channelBuffer.toString()
                                val channelId = currentChannelId ?: "unknown"
                                val shouldKeep = if (config.filters.removeDuplicates && duplicateChannelIds.contains(channelId)) {
                                    false
                                } else {
                                    shouldKeepXmlChannel(channelText)
                                }
                                channelFilterDecisions[channelId] = shouldKeep
                                if (shouldKeep) {
                                    keptChannelsCount++
                                    keptWriter.write(channelText)
                                } else {
                                    removedChannelsCount++
                                    removedWriter.write(channelText)
                                }
                                // ✅ Throttle: pause based on speed
                                val delay = getThrottleDelay()
                                if (delay > 0) Thread.sleep(delay)
                            }
                            "programme" -> {
                                insideProgramme = false
                                programmeBuffer.append("</programme>\n")
                                val programmeXml = programmeBuffer.toString()
                                if (shouldKeepCurrentChannel) {
                                    keptWriter.write(programmeXml)
                                } else {
                                    removedWriter.write(programmeXml)
                                }
                            }
                            "tv" -> {
                                if (!tvClosed) {
                                    keptWriter.write("</tv>\n")
                                    removedWriter.write("</tv>\n")
                                    tvClosed = true
                                }
                            }
                            else -> {
                                val closing = "</${parser.name}>"
                                val tagName = parser.name.trim()
                                if (tagName.equals("tv", ignoreCase = true)) {
                                    // Skip
                                } else if (insideChannel) {
                                    channelBuffer.append(closing)
                                } else if (insideProgramme) {
                                    programmeBuffer.append(closing)
                                } else {
                                    keptWriter.write(closing)
                                }
                            }
                        }
                    }
                }
                parser.next()

                // ✅ Pause support
                while (isPaused && isProcessing) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }

            // ✅ Close writers to ensure file is fully written
            keptWriter.close()
            removedWriter.close()

            val finalProgress = (processedChannels.toLong() * 100 / totalChannels.coerceAtLeast(1)).toInt()
            logAndSend(
                "Filtering: $processedChannels / $totalChannels",
                finalProgress,
                "EPG_Channels",
                MSG_PROGRESS_CHANNELS
            )

            val result = "✅ EPG: $keptChannelsCount channels kept, $removedChannelsCount removed"
            logAndSend(result, 100, "EPG_Done", MSG_LOG)


        } catch (e: Exception) {
            Log.e(TAG, "❌ EPG filtering failed", e)
            logAndSend("❌ EPG error: ${e.message}", 0, "Error", MSG_LOG)
            throw e
        } finally {
            try { inputStream?.close() } catch (e: IOException) { Log.w(TAG, "Error closing input stream", e) }
            try { keptWriter?.close() } catch (e: IOException) { Log.w(TAG, "Error closing keptWriter", e) }
            try { removedWriter?.close() } catch (e: IOException) { Log.w(TAG, "Error closing removedWriter", e) }
        }
    }

    private fun exportToFile(src: File, displayName: String): Uri? {
        try {
            val location = config.system.outputLocation ?: "Documents"
            val relativePath = if (location == "Documents") {
                Environment.DIRECTORY_DOCUMENTS + "/MalEPG"
            } else {
                Environment.DIRECTORY_DOWNLOADS + "/MalEPG"
            }

            val resolver = this.contentResolver
            val queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            // ✅ Fix: Query by DISPLAY_NAME only — RELATIVE_PATH is unreliable on TV
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=?"
            val selectionArgs = arrayOf(displayName)

            resolver.query(queryUri, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val existingUri = ContentUris.withAppendedId(queryUri, id)

                    // ✅ Overwrite the existing file
                    resolver.openOutputStream(existingUri, "rwt")?.use { outputStream ->
                        src.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    Log.d(TAG, "✅ Overwrote existing file: $existingUri")
                    return existingUri
                }
            }

            // ✅ File doesn't exist — insert new one
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }

            val newUri = resolver.insert(queryUri, values) ?: return null

            resolver.openOutputStream(newUri, "rwt")?.use { outputStream ->
                src.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Log.d(TAG, "✅ Created new file: $newUri")
            return newUri

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to export $displayName", e)
            logAndSend("❌ Export failed: ${e.message}", 0, "Error", MSG_LOG)
            return null
        }
    }
    private fun countElementsInFile(epgFile: File) {
        var inputStream: InputStream? = null
        try {
            inputStream = if (epgFile.name.endsWith(".gz", ignoreCase = true)) {
                GZIPInputStream(FileInputStream(epgFile))
            } else {
                FileInputStream(epgFile)
            }
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(inputStream, null)
            totalChannels = 0
            totalProgrammes = 0

            var tagCount = 0
            val UPDATE_UI_EVERY_MS = 500L
            var lastUpdate = System.currentTimeMillis()

            // 🔁 Send initial message
            logAndSend(
                "Channels: $totalChannels",
                5,
                "EPG_Count",
                MSG_LOG
            )

            while (parser.eventType != XmlPullParser.END_DOCUMENT && isProcessing) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> totalChannels++
                        "programme" -> totalProgrammes++
                    }
                    tagCount++

                    // ✅ Yield every 500 tags to keep system responsive
                    if (tagCount % 500 == 0) {
                        Thread.sleep(2)  // 2ms pause — user won't notice, system will appreciate
                    }

                    // ✅ Update UI every 500ms
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= UPDATE_UI_EVERY_MS) {
                        logAndSend(
                            "Channels: $totalChannels",
                            5,
                            "EPG_Count",
                            MSG_LOG
                        )
                        lastUpdate = now
                    }
                }
                parser.next()

                // ✅ Pause support
                while (isPaused && isProcessing) {
                    try {
                        Thread.sleep(100)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }

            // ✅ Final update
            logAndSend(
                "Channels: $totalChannels",
                5,
                "EPG_Count",
                MSG_LOG
            )

        } catch (e: Exception) {
            Log.w(TAG, "Could not count elements", e)
            totalChannels = totalChannels.coerceAtLeast(100)
            totalProgrammes = totalProgrammes.coerceAtLeast(1000)
            logAndSend("Channels: $totalChannels (estimated)", 5, "EPG_Count", MSG_LOG)
        } finally {
            try { inputStream?.close() } catch (e: IOException) { Log.w(TAG, "Error closing counting stream", e) }
        }
    }

    private fun shouldKeepXmlChannel(channelXml: String): Boolean {
        val textContent = channelXml.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

        // 1. General filters
        if (config.filters.removeNonEnglish) {
            if (Regex("[^\\u0020-\\u007E\\u2010-\\u2027]").find(textContent) != null) return false
        }
        if (config.filters.removeNonLatin) {
            if (nonLatinPattern.matcher(textContent).find()) return false
        }

        // 2. Exclude custom keywords
        if (config.filters.excludeKeywords.isNotEmpty()) {
            if (config.filters.excludeKeywords.any { it.lowercase() in textContent }) return false
        }

        // 🌐 3. Define Spanish Keywords List
        val spanishWords = listOf(
            "cine",       // not in "cinema"
            "novela",     // not in "novel"
            "telenovela",
            "deporte",    // not in "importante"
            "noticias",
            "canal",
            "en vivo",
            "enlace",
            "futbol",     // not in "football"
            "baloncesto",
            "beisbol",
            "tenis",
            "musica",
            "programa",
            "serie",
            "pelicula",
            "noticia",
            "clima",
            "tiempo",
            "hispano",
            "latino",
            "espanol",
            "spanish",
            "el",
            "la",
            "gratis",
            "estrella",
            "azteca",
            "vix",
            "caso",
            "casa",
            "luz",
            "amor",
            "oro",
            "grandes",
            "y",
            "estelar",
            "canela",
            "cielo",
            "misterios"
        )
        // 🌐 Remove Spanish Channels
        if (config.filters.removeSpanish) {
            if (spanishWords.any { matchesWholeWord(textContent, it) }) return false
        }
        // 🔥 3. News & Weather Master Filter
        if (config.filters.removeNewsAndWeather) {
            if (listOf("news", "weather").any { it in textContent }) return false
            val newsPatterns = listOf(
                "fox news", "cnn", "msnbc", "newsmax", "cnbc", "oan",
                "weather channel", "accuweather", "nbc news", "cbs news"
            )
            if (newsPatterns.any { it in textContent }) return false
        } else {
            // Apply individual sub-filters
            if (config.filters.removeFoxNews && "fox news" in textContent) return false
            if (config.filters.removeCNN && "cnn" in textContent) return false
            if (config.filters.removeMSNBC && "msnbc" in textContent) return false
            if (config.filters.removeNewsMax && "newsmax" in textContent) return false
            if (config.filters.removeCBSNews && "cbs news" in textContent) return false
            if (config.filters.removeNBCNews && "nbc news" in textContent) return false
            if (config.filters.removeCNBC && "cnbc" in textContent) return false
            if (config.filters.removeOAN && "oan" in textContent) return false
            if (config.filters.removeWeatherChannel && ("weather channel" in textContent || "weather." in textContent)) return false
            if (config.filters.removeAccuWeather && "accuweather" in textContent) return false
        }

        // ⚽ Sports Filters
        if (config.filters.removeSportsChannels) {
            val sportsKeywords = listOf(
                // Sports Networks
                "espn", "fox sports", "cbs sports", "nbc sports",
                "sportsnet", "tnt sports", "dazn", "mlb network",
                "nfl network", "nba tv", "golf channel", "tennis channel",
                "sky sports", "nhl channel", "big 10 network", "btn",
                // Sports by Type
                "baseball", "basketball", "football", "soccer", "futbol", "fútbol",
                "hockey", "golf", "fishing", "ufc", "mma", "boxing", "swimming",
                "premier league", "la liga", "pga", "nhl", "nba", "nfl", "mlb", "college football"
            )
            if (sportsKeywords.any { it in textContent }) return false
        } else {
            // Apply individual network filters
            if (config.filters.removeESPN && "espn" in textContent) return false
            if (config.filters.removeFoxSports && "fox sports" in textContent) return false
            if (config.filters.removeCBSSports && "cbs sports" in textContent) return false
            if (config.filters.removeNBCSports && "nbc sports" in textContent) return false
            if (config.filters.removeSportsnet && "sportsnet" in textContent) return false
            if (config.filters.removeTNTSports && "tnt sports" in textContent) return false
            if (config.filters.removeDAZN && "dazn" in textContent) return false
            if (config.filters.removeMLBNetwork && "mlb network" in textContent) return false
            if (config.filters.removeNFLNetwork && "nfl network" in textContent) return false
            if (config.filters.removeNBATV && "nba tv" in textContent) return false
            if (config.filters.removeGolfChannel && "golf channel" in textContent) return false
            if (config.filters.removeTennisChannel && "tennis channel" in textContent) return false
            if (config.filters.removeSKYSports && "sky sports" in textContent) return false
            if (config.filters.removeNHLChannel && "nhl channel" in textContent) return false
            if (config.filters.removeBigTenNetwork && ("big 10 network" in textContent || "btn" in textContent)) return false
        }

        // 🏈 Sports by Type
        if (config.filters.removeBaseball && ("baseball" in textContent || "mlb" in textContent)) return false
        if (config.filters.removeBasketball && ("basketball" in textContent || "nba" in textContent)) return false
        if (config.filters.removeFootball && ("football" in textContent || "nfl" in textContent || "college football" in textContent)) return false
        if (config.filters.removeSoccer && ("soccer" in textContent || "futbol" in textContent || "fútbol" in textContent || "premier league" in textContent || "la liga" in textContent)) return false
        if (config.filters.removeHockey && ("hockey" in textContent || "nhl" in textContent)) return false
        if (config.filters.removeGolf && ("golf" in textContent || "pga" in textContent)) return false
        if (config.filters.removeFishing && "fishing" in textContent) return false
        if (config.filters.removeBoxing && "boxing" in textContent) return false
        if (config.filters.removeSwimming && ("swimming" in textContent || "aquatics" in textContent)) return false
        if (config.filters.removeUFCMMA && ("ufc" in textContent || "mma" in textContent)) return false

        // 🎵 Music Filters
        if (config.filters.removeMusicChannels) {
            val musicKeywords = listOf(
                "bet jams", "bet soul", "bpm", "cmt", "clubland", "da vinci music",
                "dance music tv", "flaunt", "fuse", "gospel music", "heart tv",
                "juice", "jukebox", "kerrang", "kiss tv", "lite tv", "loud tv",
                "mtv", "pulse", "qvc music", "revolt", "ride tv", "stingray",
                "the box", "trace", "vevo",
                "a cappella", "acoustic", "alternative", "ambient", "bollywood",
                "children's music", "christian music", "classical", "classic rock",
                "country", "dance", "disco", "easy listening", "electronic", "folk",
                "gospel", "grunge", "hard rock", "hip hop", "holiday music", "indie",
                "jazz", "karaoke", "latin", "latin pop", "lofi", "metal", "new age",
                "opera", "pop", "punk", "r&b", "rap", "reggae", "rock", "techno",
                "trance", "trip hop", "world music"
            )
            if (musicKeywords.any { it in textContent }) return false
        } else {
            // Apply individual network filters
            if (config.filters.removeBETJams && "bet jams" in textContent) return false
            if (config.filters.removeBETSoul && "bet soul" in textContent) return false
            if (config.filters.removeBPM && "bpm" in textContent) return false
            if (config.filters.removeCMT && "cmt" in textContent) return false
            if (config.filters.removeClublandTV && "clubland" in textContent) return false
            if (config.filters.removeDaVinciMusic && "da vinci music" in textContent) return false
            if (config.filters.removeDanceMusicTV && "dance music tv" in textContent) return false
            if (config.filters.removeFlaunt && "flaunt" in textContent) return false
            if (config.filters.removeFuse && "fuse" in textContent) return false
            if (config.filters.removeGospelMusicChannel && "gospel music" in textContent) return false
            if (config.filters.removeHeartTV && "heart tv" in textContent) return false
            if (config.filters.removeJuice && "juice" in textContent) return false
            if (config.filters.removeJukebox && "jukebox" in textContent) return false
            if (config.filters.removeKerrangTV && "kerrang" in textContent) return false
            if (config.filters.removeKissTV && "kiss tv" in textContent) return false
            if (config.filters.removeLiteTV && "lite tv" in textContent) return false
            if (config.filters.removeLoudTV && "loud tv" in textContent) return false
            if (config.filters.removeMTV && "mtv" in textContent) return false
            if (config.filters.removePulse && "pulse" in textContent) return false
            if (config.filters.removeQVCMusic && "qvc music" in textContent) return false
            if (config.filters.removeRevolt && "revolt" in textContent) return false
            if (config.filters.removeRIDEtv && "ride tv" in textContent) return false
            if (config.filters.removeStingray && "stingray" in textContent) return false
            if (config.filters.removeTheBox && "the box" in textContent) return false
            if (config.filters.removeTrace && "trace" in textContent) return false
            if (config.filters.removeVevo && "vevo" in textContent) return false

            // Apply individual genre filters
            if (config.filters.removeAcappella && "a cappella" in textContent) return false
            if (config.filters.removeAcoustic && "acoustic" in textContent) return false
            if (config.filters.removeAlternative && "alternative" in textContent) return false
            if (config.filters.removeAmbient && "ambient" in textContent) return false
            if (config.filters.removeBollywood && "bollywood" in textContent) return false
            if (config.filters.removeChildrensMusic && "children's music" in textContent) return false
            if (config.filters.removeChristianMusic && "christian music" in textContent) return false
            if (config.filters.removeClassical && "classical" in textContent) return false
            if (config.filters.removeClassicRock && "classic rock" in textContent) return false
            if (config.filters.removeCountry && "country" in textContent) return false
            if (config.filters.removeDance && "dance" in textContent) return false
            if (config.filters.removeDisco && "disco" in textContent) return false
            if (config.filters.removeEasyListening && "easy listening" in textContent) return false
            if (config.filters.removeElectronic && "electronic" in textContent) return false
            if (config.filters.removeFolk && "folk" in textContent) return false
            if (config.filters.removeGospel && "gospel" in textContent) return false
            if (config.filters.removeGrunge && "grunge" in textContent) return false
            if (config.filters.removeHardRock && "hard rock" in textContent) return false
            if (config.filters.removeHipHop && "hip hop" in textContent) return false
            if (config.filters.removeHolidayMusic && "holiday music" in textContent) return false
            if (config.filters.removeIndie && "indie" in textContent) return false
            if (config.filters.removeJazz && "jazz" in textContent) return false
            if (config.filters.removeKaraoke && "karaoke" in textContent) return false
            if (config.filters.removeLatin && "latin" in textContent) return false
            if (config.filters.removeLatinPop && "latin pop" in textContent) return false
            if (config.filters.removeLofi && "lofi" in textContent) return false
            if (config.filters.removeMetal && "metal" in textContent) return false
            if (config.filters.removeNewAge && "new age" in textContent) return false
            if (config.filters.removeOpera && "opera" in textContent) return false
            if (config.filters.removePop && "pop" in textContent) return false
            if (config.filters.removePunk && "punk" in textContent) return false
            if (config.filters.removeRnB && "r&b" in textContent) return false
            if (config.filters.removeRap && "rap" in textContent) return false
            if (config.filters.removeReggae && "reggae" in textContent) return false
            if (config.filters.removeRock && "rock" in textContent) return false
            if (config.filters.removeTechno && "techno" in textContent) return false
            if (config.filters.removeTrance && "trance" in textContent) return false
            if (config.filters.removeTriphop && "trip hop" in textContent) return false
            if (config.filters.removeWorldMusic && "world music" in textContent) return false
        }

        return true
    }

    private fun findExistingPlaylistFile(directory: File): File? {
        if (!directory.exists()) return null
        return directory.listFiles()?.find { it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true) }
    }

    private fun findExistingEPGFile(directory: File): File? {
        if (!directory.exists()) return null
        return directory.listFiles()?.find { it.name.endsWith(".xml", true) || it.name.endsWith(".xml.gz", true) }
    }

    private fun readXmlDeclaration(epgFile: File): String {
        return try {
            val stream = if (epgFile.name.endsWith(".gz", true)) {
                GZIPInputStream(FileInputStream(epgFile))
            } else {
                FileInputStream(epgFile)
            }
            stream.use { s ->
                val reader = BufferedReader(InputStreamReader(s))
                val firstLine = reader.readLine() ?: return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                val trimmed = firstLine.trim()

                // Extract only the <?xml ...?> part, if present
                val xmlDeclMatch = Regex("^<\\?xml\\s+[^?>]*\\?>", RegexOption.IGNORE_CASE).find(trimmed)
                if (xmlDeclMatch != null) {
                    xmlDeclMatch.value
                } else {
                    // If no valid XML declaration, fall back
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read XML declaration", e)
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        }
    }

    private fun buildXmlStartTag(parser: XmlPullParser): String {
        val tag = StringBuilder("<${parser.name}")
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i) ?: continue
            val value = parser.getAttributeValue(i) ?: ""
            tag.append(" $name=\"${escapeXmlAttribute(value)}\"")
        }
        tag.append(">")
        return tag.toString()
    }

    private fun getThrottleDelay(): Long {
        return if (isAutoMode) {
            when (config.system.autoFilterSpeed) {
                "full" -> DELAY_AUTO_FULL
                "balanced" -> DELAY_AUTO_BALANCED
                "slow" -> DELAY_AUTO_SLOW
                else -> DELAY_AUTO_BALANCED
            }
        } else {
            when (config.system.manualFilterSpeed) {
                "full" -> DELAY_FULL
                "balanced" -> DELAY_BALANCED
                "slow" -> DELAY_SLOW
                else -> DELAY_BALANCED
            }
        }
    }

    private fun matchesWholeWord(text: String, word: String): Boolean {
        return Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun escapeXmlAttribute(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "<")
        .replace(">", ">")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private fun escapeXmlText(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "<")
        .replace(">", ">")

    private fun writeCrashLog(exception: Exception) {
        try {
            val crashFile = File(this.filesDir, "crash_log.txt")
            crashFile.appendText("\n=== CRASH ${System.currentTimeMillis()} ===\n${exception.stackTraceToString()}\n=== END ===\n")
        } catch (e: Exception) {
            Log.e(TAG, "Could not write crash log", e)
        }
    }

    private fun logAndSend(
        message: String,
        percentage: Int,
        phase: String,
        messageType: String = MSG_STATUS,
        totalChannelsOverride: Int? = null
    ) {
        currentStatus = message
        currentProgress = percentage
        currentPhase = phase
        val channelsToSend = totalChannelsOverride ?: totalChannels
        sendProgressUpdate(message, percentage, channelsToSend, processedChannels, totalProgrammes, 0, phase, messageType)
    }

    private fun pauseEpgProcessing() {
        if (isProcessing && !isPaused) {
            isPaused = true
            logAndSend("⏸️ Paused", currentProgress, "Paused", MSG_STATUS)
            startForeground(NOTIFICATION_ID, createNotification("Paused"))
        }
    }

    private fun resumeEpgProcessing() {
        if (isPaused) {
            isPaused = false
            logAndSend("▶️ Resumed", currentProgress, "Resumed", MSG_STATUS)
            startForeground(NOTIFICATION_ID, createNotification("Resuming..."))
        }
    }
    private fun sendProgressUpdate(
        message: String,
        percentage: Int,
        totalChannels: Int,
        processedChannels: Int,
        totalProgrammes: Int,
        processedProgrammes: Int,
        phase: String,
        messageType: String
    ) {
        val intent = Intent("EPG_PROGRESS_UPDATE").apply {
            putExtra("message", message)
            putExtra("percentage", percentage)
            putExtra("totalChannels", totalChannels)
            putExtra("processedChannels", processedChannels)
            putExtra("totalProgrammes", totalProgrammes)
            putExtra("processedProgrammes", processedProgrammes)
            putExtra("phase", phase)
            putExtra("messageType", messageType)
        }
        sendBroadcast(intent)
    }
}

private fun File.countLines(): Int {
    return try {
        bufferedReader().use { it.lineSequence().count() }
    } catch (e: Exception) {
        100
    }
}