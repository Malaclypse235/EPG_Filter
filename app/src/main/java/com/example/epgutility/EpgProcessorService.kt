package com.example.epgutility

import android.app.*
import android.app.NotificationManager
import android.app.Service.START_STICKY
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
        const val ACTION_START_EPG_PROCESSING = "START_EPG_PROCESSING"
        const val ACTION_STOP_EPG_PROCESSING = "STOP_EPG_PROCESSING"
        const val ACTION_GET_PROGRESS = "GET_EPG_PROGRESS"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "epg_processor"
        private const val TAG = "EpgProcessorService"
    }

    private var isProcessing = false
    private var processingThread: Thread? = null

    // Progress tracking
    private var currentProgress = 0
    private var currentStatus = "Starting"
    private var totalChannels = 0
    private var processedChannels = 0
    private var totalProgrammes = 0
    private var processedProgrammes = 0
    private var currentPhase = "Starting"

    // --- Filtering State ---
    private lateinit var config: ConfigManager.ConfigData
    private val nonLatinPattern = Pattern.compile("[^\\u0000-\\u00FF\\u2010-\\u2027\\u02B0-\\u02FF]")
    private val channelFilterDecisions = HashMap<String, Boolean>()

    // Counters
    private var keptChannelsCount = 0
    private var removedChannelsCount = 0
    private var keptProgrammesCount = 0
    private var removedProgrammesCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🔥 onStartCommand called! Action: ${intent?.action}")
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
            // ✅ Correct: returns LoadResult
            val loadResult = ConfigManager.loadConfig(this)
            config = loadResult.config

            // Optional: log if permissions were revoked
            if (loadResult.revokedPermissions) {
                Log.w(TAG, "⚠️ Some URI permissions were revoked")
            }

            Log.d(TAG, "✅ Config loaded: disablePlaylistFiltering=${config.disablePlaylistFiltering}")

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
        currentStatus = "Starting"
        currentPhase = "Starting"
        keptChannelsCount = 0
        removedChannelsCount = 0
        keptProgrammesCount = 0
        removedProgrammesCount = 0
        processedChannels = 0
        processedProgrammes = 0
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
            currentStatus,
            currentProgress,
            totalChannels,
            processedChannels,
            totalProgrammes,
            processedProgrammes,
            currentPhase
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
                Log.e(TAG, "❌ Input directory does not exist: $inputDir")
                logAndSend("❌ Input folder missing", 0, "Error")
                return
            }

            val playlistFile = findExistingPlaylistFile(inputDir)
            val epgFile = findExistingEPGFile(inputDir)

            if (playlistFile == null && epgFile == null) {
                Log.e(TAG, "❌ No input files found")
                logAndSend("❌ No .m3u or .xml files found", 0, "Error")
                return
            }

            // --- Step 1: M3U Filtering ---
            if (playlistFile != null && !config.disablePlaylistFiltering) {
                Log.d(TAG, "✅ Starting M3U filtering: ${playlistFile.name}")
                processM3uFile(playlistFile)
                logAndSend("✅ M3U filtering complete", 30, "M3U Done")
            } else {
                Log.d(TAG, "⏭️ Skipping M3U filtering")
                logAndSend("⏭️ Skipped M3U filtering", 0, "Skipped")
            }

            // --- Step 2: EPG Filtering ---
            if (epgFile != null && !config.disableEPGFiltering) {
                Log.d(TAG, "✅ Starting EPG filtering: ${epgFile.name}")
                processEpgFile(epgFile)
                logAndSend("✅ EPG filtering complete", 95, "EPG Done")
            } else {
                Log.d(TAG, "⏭️ Skipping EPG filtering")
                logAndSend("⏭️ Skipped EPG filtering", 0, "Skipped")
            }

            logAndSend("🎉 All processing complete!", 100, "Complete")
        } catch (e: Exception) {
            Log.e(TAG, "💥 Critical error", e)
            logAndSend("❌ Failed: ${e.message}", 0, "Error")
            writeCrashLog(e)
        } finally {
            stopEpgProcessing()
        }
    }

    private fun processM3uFile(playlistFile: File) {
        try {
            val outputDir = File(this.filesDir, "output").apply { mkdirs() }
            val keptFile = File(outputDir, "kept_channels.m3u")
            val removedFile = File(outputDir, "removed_channels.m3u")

            var total = 0
            var kept = 0
            var removed = 0

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
                                    total++
                                    val shouldKeep = shouldKeepM3uChannel(currentChannel)
                                    if (shouldKeep) {
                                        kept++
                                        keptWriter.write("${currentChannel[0]}\n${currentChannel[1]}\n\n")
                                    } else {
                                        removed++
                                        removedWriter.write("${currentChannel[0]}\n${currentChannel[1]}\n\n")
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
                            total++
                            val shouldKeep = shouldKeepM3uChannel(currentChannel)
                            if (shouldKeep) {
                                kept++
                                keptWriter.write("${currentChannel[0]}\n${currentChannel[1]}\n")
                            } else {
                                removed++
                                removedWriter.write("${currentChannel[0]}\n${currentChannel[1]}\n")
                            }
                        }
                    }
                }
            }

            keptChannelsCount = kept
            removedChannelsCount = removed
            this.totalChannels = total

            Log.d(TAG, "✅ M3U filtered: $total total, $kept kept, $removed removed")
        } catch (e: Exception) {
            Log.e(TAG, "❌ M3U filtering failed", e)
            logAndSend("❌ M3U error: ${e.message}", 0, "Error")
            throw e
        }
    }

    private fun shouldKeepM3uChannel(channelLines: List<String>): Boolean {
        if (!config.removeNonLatin && !config.removeNonEnglish) return true

        val text = channelLines.joinToString(" ")

        if (config.removeNonEnglish) {
            val matcher = Pattern.compile("[^\\u0020-\\u007E]").matcher(text)
            if (matcher.find()) {
                Log.v(TAG, "🗑️ M3U: Non-English chars in: $text")
                return false
            }
        }

        if (config.removeNonLatin) {
            val matcher = nonLatinPattern.matcher(text)
            if (matcher.find()) {
                Log.v(TAG, "🗑️ M3U: Non-Latin chars in: $text")
                return false
            }
        }

        return true
    }

    private fun processEpgFile(epgFile: File) {
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
                                processedProgrammes++
                                val channelId = parser.getAttributeValue(null, "channel") ?: "unknown"
                                shouldKeepCurrentChannel = channelFilterDecisions.getOrDefault(channelId, true)
                                if (shouldKeepCurrentChannel) {
                                    keptWriter.write(buildXmlStartTag(parser))
                                } else {
                                    removedWriter.write(buildXmlStartTag(parser))
                                }
                            }
                            else -> {
                                val element = buildXmlStartTag(parser)
                                if (insideChannel) channelBuffer.append(element)
                                else if (insideProgramme) {
                                    if (shouldKeepCurrentChannel) keptWriter.write(element) else removedWriter.write(element)
                                } else {
                                    keptWriter.write(element)
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text ?: ""
                        val escaped = escapeXmlText(text)
                        if (insideChannel) channelBuffer.append(escaped)
                        else if (insideProgramme) {
                            if (shouldKeepCurrentChannel) keptWriter.write(escaped) else removedWriter.write(escaped)
                        } else {
                            keptWriter.write(escaped)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                insideChannel = false
                                channelBuffer.append("</channel>")
                                shouldKeepCurrentChannel = shouldKeepXmlChannel(channelBuffer.toString())
                                currentChannelId?.let { channelFilterDecisions[it] = shouldKeepCurrentChannel }

                                if (shouldKeepCurrentChannel) {
                                    keptChannelsCount++
                                    keptWriter.write("${channelBuffer}\n")
                                } else {
                                    removedChannelsCount++
                                    removedWriter.write("${channelBuffer}\n")
                                }
                            }
                            "programme" -> {
                                insideProgramme = false
                                if (shouldKeepCurrentChannel) {
                                    keptWriter.write("</programme>\n")
                                    keptProgrammesCount++
                                } else {
                                    removedWriter.write("</programme>\n")
                                    removedProgrammesCount++
                                }
                            }
                            "tv" -> {
                                if (tvRootWritten) {
                                    keptWriter.write("</tv>\n")
                                    removedWriter.write("</tv>\n")
                                }
                            }
                            else -> {
                                val closing = "</${parser.name}>"
                                if (insideChannel) channelBuffer.append(closing)
                                else if (insideProgramme) {
                                    if (shouldKeepCurrentChannel) keptWriter.write(closing) else removedWriter.write(closing)
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

            Log.d(TAG, "✅ EPG filtering complete: $processedChannels ch, $processedProgrammes prog")
        } catch (e: Exception) {
            Log.e(TAG, "❌ EPG filtering failed", e)
            logAndSend("❌ EPG error: ${e.message}", 0, "Error")
            throw e
        } finally {
            try { inputStream?.close() } catch (e: IOException) { Log.w(TAG, "Error closing input stream", e) }
            try { keptWriter?.close() } catch (e: IOException) { Log.w(TAG, "Error closing keptWriter", e) }
            try { removedWriter?.close() } catch (e: IOException) { Log.w(TAG, "Error closing removedWriter", e) }
        }
    }

    private fun shouldKeepXmlChannel(channelXml: String): Boolean {
        if (!config.removeNonLatin && !config.removeNonEnglish) return true

        if (config.removeNonEnglish) {
            val matcher = Pattern.compile("[^\\u0020-\\u007E]").matcher(channelXml)
            if (matcher.find()) {
                Log.v(TAG, "🗑️ XML: Non-English in channel")
                return false
            }
        }

        if (config.removeNonLatin) {
            val matcher = nonLatinPattern.matcher(channelXml)
            if (matcher.find()) {
                Log.v(TAG, "🗑️ XML: Non-Latin in channel")
                return false
            }
        }

        return true
    }

    // --- Utility Functions ---
    private fun findExistingPlaylistFile(directory: File): File? {
        if (!directory.exists()) return null
        return directory.listFiles()?.find {
            it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true)
        }
    }

    private fun findExistingEPGFile(directory: File): File? {
        if (!directory.exists()) return null
        return directory.listFiles()?.find {
            it.name.endsWith(".xml", true) || it.name.endsWith(".xml.gz", true)
        }
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

    private fun logAndSend(message: String, percentage: Int, phase: String) {
        Log.d(TAG, "📢 $message (phase=$phase, %=$percentage)")
        currentStatus = message
        currentProgress = percentage
        currentPhase = phase
        sendProgressUpdate(message, percentage, totalChannels, processedChannels, totalProgrammes, processedProgrammes, phase)
    }

    private fun sendProgressUpdate(
        message: String,
        percentage: Int,
        totalChannels: Int,
        processedChannels: Int,
        totalProgrammes: Int,
        processedProgrammes: Int,
        phase: String
    ) {
        val intent = Intent("EPG_PROGRESS_UPDATE").apply {
            putExtra("message", message)
            putExtra("percentage", percentage)
            putExtra("totalChannels", totalChannels)
            putExtra("processedChannels", processedChannels)
            putExtra("totalProgrammes", totalProgrammes)
            putExtra("processedProgrammes", processedProgrammes)
            putExtra("phase", phase)
        }
        sendBroadcast(intent)
    }
}