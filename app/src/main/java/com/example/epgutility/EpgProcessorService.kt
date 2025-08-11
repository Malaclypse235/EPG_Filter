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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EPG_PROCESSING -> startEpgProcessing()
            ACTION_STOP_EPG_PROCESSING -> stopEpgProcessing()
            ACTION_GET_PROGRESS -> sendCurrentProgress()
            else -> Log.w(TAG, "Unknown action: ${intent?.action}")
        }
        return START_STICKY
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

    private fun processEpgInBackground() {
        try {
            val inputDir = File(this.filesDir, "input")
            if (!inputDir.exists()) {
                logAndSend("❌ Input folder missing", 0, "Error", MSG_LOG)
                return
            }

            val playlistFile = findExistingPlaylistFile(inputDir)
            val epgFile = findExistingEPGFile(inputDir)

            if (playlistFile == null && epgFile == null) {
                logAndSend("❌ No .m3u or .xml files found", 0, "Error", MSG_LOG)
                return
            }

            // --- Step 1: M3U Filtering ---
            if (playlistFile != null && !config.disablePlaylistFiltering) {
                logAndSend("\uD83D\uDCE1 Starting M3U filtering...", 0, "M3U_Start", MSG_LOG)
                processM3uFile(playlistFile)
            } else {
                logAndSend("⚠\uFE0F Skipped M3U filtering", 0, "M3U_Skipped", MSG_LOG)
            }

            // --- Step 2: EPG Filtering ---
            if (epgFile != null && !config.disableEPGFiltering) {
                logAndSend("📺 Starting EPG filtering...", 5, "EPG_Start", MSG_LOG)
                countElementsInFile(epgFile)
                // logAndSend("EPG: $totalChannels channels found", 5, "EPG_Count", MSG_LOG)
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

    private fun processM3uFile(playlistFile: File) {
        // Step 1: Count total channels
        val total = countExtinfLines(playlistFile)
        Log.d(TAG, "FILTER_DEBUG: local total = $total, this.totalChannels = $this.totalChannels")
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

                                    val shouldKeep = shouldKeepM3uChannel(currentChannel)
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
                            val shouldKeep = shouldKeepM3uChannel(currentChannel)
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

            keptChannelsCount = kept
            removedChannelsCount = removed
            // this.totalChannels = total

            Log.d(TAG, "✅ M3U filtered: $total total, $kept kept, $removed removed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ M3U filtering failed", e)
            logAndSend("❌ M3U error: ${e.message}", 0, "Error", MSG_LOG)
            throw e
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
        if (!config.removeNonLatin && !config.removeNonEnglish) return true

        val text = channelLines.joinToString(" ")

        if (config.removeNonEnglish) {
            val matcher = Pattern.compile("[^\\u0020-\\u007E]").matcher(text)
            if (matcher.find()) return false
        }

        if (config.removeNonLatin) {
            val matcher = nonLatinPattern.matcher(text)
            if (matcher.find()) return false
        }

        return true
    }

    private fun processEpgFile(epgFile: File) {
        // Count elements first
        countElementsInFile(epgFile)

        Log.d("EpgProcessorService", "After count: totalChannels = $totalChannels")

        // Send count to update UI
        // logAndSend("EPG: $totalChannels channels found", 5, "EPG_Count", MSG_LOG)

        var inputStream: InputStream? = null
        var keptWriter: BufferedWriter? = null
        var removedWriter: BufferedWriter? = null

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
            var tvRootWritten = false

            // Track last progress update time
            var lastProgressUpdate = System.currentTimeMillis()
            val PROGRESS_UPDATE_INTERVAL = 500L

// Start a background updater
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
                            sleep(50) // Check every 50ms to avoid missing interval
                        }
                    } catch (e: InterruptedException) {
                        // Expected on stop
                    }
                }
            }.apply { start() }

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

                                channelBuffer.setLength(0)
                                channelBuffer.append(buildXmlStartTag(parser))

                            }
                            "programme" -> {
                                insideProgramme = true
                                val channelId = parser.getAttributeValue(null, "channel") ?: "unknown"
                                shouldKeepCurrentChannel = channelFilterDecisions.getOrDefault(channelId, true)
                            }
                            else -> {
                                val element = buildXmlStartTag(parser)
                                if (insideChannel) {
                                    channelBuffer.append(element)
                                } else if (insideProgramme) {
                                    (if (shouldKeepCurrentChannel) keptWriter else removedWriter).write(element)
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
                            (if (shouldKeepCurrentChannel) keptWriter else removedWriter).write(escaped)
                        } else {
                            keptWriter.write(escaped)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                insideChannel = false
                                channelBuffer.append("</channel>")

                                val channelText = channelBuffer.toString()
                                shouldKeepCurrentChannel = shouldKeepXmlChannel(channelText)
                                currentChannelId?.let { channelFilterDecisions[it] = shouldKeepCurrentChannel }

                                if (shouldKeepCurrentChannel) {
                                    keptChannelsCount++
                                } else {
                                    removedChannelsCount++
                                }
                            }
                            "programme" -> {
                                insideProgramme = false
                            }
                            "tv" -> {
                                if (tvRootWritten) {
                                    keptWriter.write("</tv>\n")
                                    removedWriter.write("</tv>\n")
                                }
                            }
                            else -> {
                                val closing = "</${parser.name}>"
                                if (insideChannel) {
                                    channelBuffer.append(closing)
                                } else if (insideProgramme) {
                                    (if (shouldKeepCurrentChannel) keptWriter else removedWriter).write(closing)
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

            // Final progress update
            val finalProgress = (processedChannels.toLong() * 100 / totalChannels.coerceAtLeast(1)).toInt()
            logAndSend(
                "Filtering: $processedChannels / $totalChannels",
                finalProgress,
                "EPG_Channels",
                MSG_PROGRESS_CHANNELS
            )

            // Final EPG result (only channels)
            // Final EPG result (only channels)
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
                        "programme" -> totalProgrammes++
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
        if (!config.removeNonLatin && !config.removeNonEnglish) return true

        val textContent = channelXml.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (config.removeNonEnglish) {
            val matcher = Pattern.compile("[^\\u0020-\\u007E]").matcher(textContent)
            if (matcher.find()) return false
        }

        if (config.removeNonLatin) {
            val matcher = nonLatinPattern.matcher(textContent)
            if (matcher.find()) return false
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
                BufferedReader(InputStreamReader(s)).readLine()?.trim()
            } ?: "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
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
        totalChannelsOverride: Int? = null  // Add this parameter
    ) {
        currentStatus = message
        currentProgress = percentage
        currentPhase = phase
        val channelsToSend = totalChannelsOverride ?: totalChannels  // Use override if provided
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