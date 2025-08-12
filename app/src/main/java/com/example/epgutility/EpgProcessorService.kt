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

class EpgProcessorService : Service() {

    companion object {
        const val CHANNELS = "📺"
        const val DONE = "✅"
        const val ACTION_START_EPG_PROCESSING = "START_EPG_PROCESSING"
        const val ACTION_STOP_EPG_PROCESSING = "STOP_EPG_PROCESSING"
        const val ACTION_GET_PROGRESS = "GET_EPG_PROGRESS"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "epg_processor"
        private const val TAG = "EpgProcessorService"

        // Message types for UI handling
        const val MSG_STATUS = "STATUS"
        const val MSG_PROGRESS_M3U = "PROGRESS_M3U"
        const val MSG_PROGRESS_CHANNELS = "PROGRESS_CHANNELS"
        const val MSG_LOG = "LOG"
    }

    private var isProcessing = false
    private var processingThread: Thread? = null

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
                // ✅ Extract paths from intent
                this.playlistPath = intent.getStringExtra("PLAYLIST_PATH")
                this.epgPath = intent.getStringExtra("EPG_PATH")
                startEpgProcessing()
            }
            ACTION_STOP_EPG_PROCESSING -> stopEpgProcessing()
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
        val channel = NotificationChannel(CHANNEL_ID, "EPG Processing", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Processing EPG XML files"
            setShowBadge(false)
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
        // Step 1: Count total channels
        val total = countExtinfLines(playlistFile)
        this.totalChannels = total
        logAndSend("Starting M3U filtering...", 0, "M3U_Start", MSG_LOG)

        try {
            val outputDir = File(this.filesDir, "output").apply { mkdirs() }
            val keptFile = File(outputDir, "kept_channels.m3u")
            val removedFile = File(outputDir, "removed_channels.m3u")

            var kept = 0
            var removed = 0
            var processed = 0

            // --- NEW: Timer for smooth progress updates ---
            var lastProgressUpdate = System.currentTimeMillis()
            val PROGRESS_UPDATE_INTERVAL = 500L

            val progressUpdater = object : Thread() {
                override fun run() {
                    try {
                        while (!isInterrupted && isProcessing && processed < total) {
                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL) {
                                val progress = (processed.toLong() * 100 / total.coerceAtLeast(1)).toInt()
                                logAndSend(
                                    "Filtering: $processed / $total",
                                    progress,
                                    "M3U_Filtering",
                                    MSG_PROGRESS_M3U
                                )
                                lastProgressUpdate = now
                            }
                            sleep(50) // Check every 50ms
                        }
                    } catch (e: InterruptedException) {
                        // Stopped intentionally
                    }
                }
            }.apply { start() }
            // --- END Timer ---

            // ✅ NEW: Track tvg-id values for duplicate detection
            val seenTvgIds = mutableSetOf<String>()

            keptFile.bufferedWriter().use { keptWriter ->
                removedFile.bufferedWriter().use { removedWriter ->
                    playlistFile.bufferedReader().use { reader ->
                        var line: String?
                        var currentChannel = mutableListOf<String>()
                        var headerWritten = false

                        while (reader.readLine().also { line = it } != null) {
                            val trimmed = line?.trim() ?: continue

                            if (trimmed.startsWith("#EXTM3U")) {
                                if (!headerWritten) {
                                    keptWriter.write("$trimmed\n\n")
                                    removedWriter.write("$trimmed\n\n")
                                    headerWritten = true
                                }
                                continue
                            }

                            if (trimmed.startsWith("#EXTGRP")) {
                                keptWriter.write("$trimmed\n\n")
                                removedWriter.write("$trimmed\n\n")
                                continue
                            }

                            if (trimmed.startsWith("#EXTINF:")) {
                                if (currentChannel.size >= 2) {
                                    processed++

                                    // ✅ Check for tvg-id
                                    val tvgId = extractTvgId(currentChannel[0])
                                    val isDuplicate = tvgId != null && !config.filters.removeDuplicates.not() && seenTvgIds.contains(tvgId)

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
                                        keptWriter.write("${currentChannel[0]}\n${currentChannel[1].trimEnd()}\n\n")
                                    } else {
                                        removed++
                                        removedWriter.write("${currentChannel[0]}\n${currentChannel[1].trimEnd()}\n\n")
                                    }
                                }
                                currentChannel.clear()
                                currentChannel.add(trimmed)
                            } else if (!trimmed.startsWith("#") && currentChannel.size == 1) {
                                currentChannel.add(trimmed)
                            }
                        }

                        // Last channel
                        if (currentChannel.size >= 2) {
                            processed++
                            val tvgId = extractTvgId(currentChannel[0])
                            val isDuplicate = tvgId != null && !config.filters.removeDuplicates.not() && seenTvgIds.contains(tvgId)

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
                                keptWriter.write("${currentChannel[0]}\n${currentChannel[1].trimEnd()}\n\n")
                            } else {
                                removed++
                                removedWriter.write("${currentChannel[0]}\n${currentChannel[1].trimEnd()}\n\n")
                            }
                        }
                    }
                }
            }

            // Stop updater before final update
            progressUpdater.interrupt()
            try {
                progressUpdater.join(100)
            } catch (e: InterruptedException) { }

            // Final progress update
            val progress = (processed.toLong() * 100 / total.coerceAtLeast(1)).toInt()
            logAndSend("Filtering: $processed / $total", progress, "M3U_Filtering", MSG_PROGRESS_M3U)

            // Final result
            val result = "✅ M3U: $kept kept, $removed removed"
            logAndSend(result, 100, "M3U_Done", MSG_LOG)

            // Removed: do not set keptChannelsCount here — it's for EPG only
        } catch (e: Exception) {
            Log.e(TAG, "❌ M3U filtering failed", e)
            logAndSend("❌ M3U error: ${e.message}", 0, "Error", MSG_LOG)
            throw e
        }
    }

    // ✅ Helper to extract tvg-id from #EXTINF line
    private fun extractTvgId(extinfLine: String): String? {
        val regex = Regex("""tvg-id=["']?([^"'\s>]+)""")
        return regex.find(extinfLine)?.groupValues?.get(1)
    }

    private fun processEpgInBackground() {
        try {
            val inputDir = File(this.filesDir, "input")
            if (!inputDir.exists()) {
                logAndSend("❌ Input folder missing", 0, "Error", MSG_LOG)
                return
            }

            // ✅ Use only the files passed in the intent
            val playlistFile = if (!playlistPath.isNullOrEmpty() && !config.system.disablePlaylistFiltering) {
                val file = File(playlistPath!!)
                if (file.exists()) file else null
            } else {
                null
            }

            val epgFile = if (!epgPath.isNullOrEmpty() && !config.system.disableEPGFiltering) {
                val file = File(epgPath!!)
                if (file.exists()) file else null
            } else {
                null
            }

            // --- Step 1: M3U Filtering ---
            if (playlistFile != null) {
                logAndSend("\uD83D\uDCE1 Starting M3U filtering...", 0, "M3U_Start", MSG_LOG)
                processM3uFile(playlistFile)
            } else {
                logAndSend("⚠\uFE0F Skipped M3U filtering", 0, "M3U_Skipped", MSG_LOG)
            }

            // --- Step 2: EPG Filtering ---
            if (epgFile != null) {
                logAndSend("📺 Starting EPG filtering...", 5, "EPG_Start", MSG_LOG)
                countElementsInFile(epgFile)
                processEpgFile(epgFile)
            } else {
                logAndSend("⚠\uFE0F Skipped EPG filtering", 0, "EPG_Skipped", MSG_LOG)
            }

            logAndSend("✅ All processing complete!", 100, "Complete", MSG_LOG)
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
                "weather channel", "accuweather"
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
            if (config.filters.removeWeatherChannel && ("weather channel" in searchText || "weather." in searchText)) return false
            if (config.filters.removeAccuWeather && "accuweather" in searchText) return false
        }

        return true
    }

    private fun processEpgFile(epgFile: File) {
        countElementsInFile(epgFile)

        var inputStream: InputStream? = null
        var keptWriter: BufferedWriter? = null
        var removedWriter: BufferedWriter? = null
        var tvClosed = false

        try {
            val outputDir = File(this.filesDir, "output").apply { mkdirs() }
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
                                val progress = (processedChannels.toLong() * 100 / totalChannels.coerceAtLeast(1)).toInt()
                                logAndSend(
                                    "Filtering: $processedChannels / $totalChannels",
                                    progress,
                                    "EPG_Channels",
                                    MSG_PROGRESS_CHANNELS
                                )
                                lastProgressUpdate = now
                            }
                            sleep(50)
                        }
                    } catch (e: InterruptedException) { }
                }
            }.apply { start() }

            // ✅ NEW: Track seen and duplicate channel IDs
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

                                // ✅ Check for duplicate
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
                                // ✅ If channel is duplicate, mark for removal
                                shouldKeepCurrentChannel = !duplicateChannelIds.contains(channelId)

                                programmeBuffer.setLength(0)
                                programmeBuffer.append(buildXmlStartTag(parser))
                            }
                            else -> {
                                val element = buildXmlStartTag(parser)
                                val tagName = parser.name.trim()

                                if (tagName.equals("tv", ignoreCase = true)) {
                                    // Skip — we handled it
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

                                // ✅ If duplicate, don't apply other filters
                                val shouldKeep = if (config.filters.removeDuplicates && duplicateChannelIds.contains(channelId)) {
                                    false
                                } else {
                                    shouldKeepXmlChannel(channelText)
                                }

                                if (shouldKeep) {
                                    keptChannelsCount++
                                    keptWriter.write(channelText)
                                } else {
                                    removedChannelsCount++
                                    removedWriter.write(channelText)
                                }
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
            }

            keptWriter.flush()
            removedWriter.flush()

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
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "channel" -> totalChannels++
                    }
                }
                parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not count elements", e)
            totalChannels = totalChannels.coerceAtLeast(100)
            totalProgrammes = totalProgrammes.coerceAtLeast(1000)
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

        // 🔥 3. News & Weather Master Filter
        if (config.filters.removeNewsAndWeather) {
            if (listOf("news", "weather").any { it in textContent }) return false

            val newsPatterns = listOf(
                "fox news", "cnn", "msnbc", "newsmax", "cnbc", "oan",
                "weather channel", "accuweather"
            )
            if (newsPatterns.any { it in textContent }) return false
        } else {
            // Apply individual sub-filters
            if (config.filters.removeFoxNews && "fox news" in textContent) return false
            if (config.filters.removeCNN && "cnn" in textContent) return false
            if (config.filters.removeMSNBC && "msnbc" in textContent) return false
            if (config.filters.removeNewsMax && "newsmax" in textContent) return false
            if (config.filters.removeCNBC && "cnbc" in textContent) return false
            if (config.filters.removeOAN && "oan" in textContent) return false
            if (config.filters.removeWeatherChannel && ("weather channel" in textContent || "weather." in textContent)) return false
            if (config.filters.removeAccuWeather && "accuweather" in textContent) return false
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