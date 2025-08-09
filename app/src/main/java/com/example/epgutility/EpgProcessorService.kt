package com.example.epgutility

import android.app.*
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.Service.START_STICKY
import android.content.Intent
import android.os.IBinder
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

// Background service for EPG processing
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
    private var currentStatus = "remove this text <---" // used to say "Idle"
    private var totalChannels = 0
    private var processedChannels = 0
    private var totalProgrammes = 0
    private var processedProgrammes = 0
    private var currentPhase = "Starting"

    // --- Filtering State ---
    private lateinit var config: ConfigManager.ConfigData
    private val nonLatinPattern = Pattern.compile("[^\\u0000-\\u00FF\\u2010-\\u2027\\u02B0-\\u02FF]")
    // Use a map to store the keep/remove decision for each channel ID
    private val channelFilterDecisions = HashMap<String, Boolean>()
    // --- Counters for final report ---
    private var keptChannelsCount = 0
    private var removedChannelsCount = 0
    private var keptProgrammesCount = 0
    private var removedProgrammesCount = 0
    // -------------------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EPG_PROCESSING -> startEpgProcessing()
            ACTION_STOP_EPG_PROCESSING -> stopEpgProcessing()
            ACTION_GET_PROGRESS -> sendCurrentProgress()
        }
        return START_STICKY
    }

    private fun startEpgProcessing() {
        if (isProcessing) {
            // If already processing, send current progress
            sendCurrentProgress()
            return
        }
        isProcessing = true
        currentProgress = 0
        currentStatus = "Starting EPG processing..."
        // --- Reset counters and state ---
        keptChannelsCount = 0
        removedChannelsCount = 0
        keptProgrammesCount = 0
        removedProgrammesCount = 0
        processedChannels = 0
        processedProgrammes = 0
        channelFilterDecisions.clear()
        // ---------------------
        try {
            // --- Load Config ---
            // Load config using your ConfigManager within the service context
            val loadResult = ConfigManager.loadConfig(this)
            config = loadResult.config
            // Consider handling loadResult.revokedPermissions if needed, maybe log a warning
            if (loadResult.revokedPermissions) {
                Log.w(TAG, "File permissions were reported as revoked when loading config in service.")
                // The service might still run if files are in internal storage, but it's good to know.
            }
            // -------------------

            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification("Starting EPG processing..."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service or load config", e)
        }
        processingThread = Thread {
            processEpgInBackground()
        }
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
            Log.w(TAG, "Error stopping foreground service", e)
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            "EPG Processing",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
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

    private fun updateNotification(text: String) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.notify(NOTIFICATION_ID, createNotification(text))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification", e)
        }
    }

    private fun processEpgInBackground() {
        try {
            val inputDir = File(this.filesDir, "input")
            val epgFile = findExistingEPGFile(inputDir)
            if (epgFile == null) {
                logAndSend("❌ No EPG file found", 0, "No EPG file found")
                stopEpgProcessing()
                return
            }
            logAndSend("📁 Found EPG file: ${epgFile.name} (${epgFile.length()} bytes)", 5, "Found EPG file")
            processEpgFile(epgFile)
        } catch (e: InterruptedException) {
            logAndSend("🚫 EPG processing cancelled", 0, "Cancelled")
        } catch (e: Exception) {
            logAndSend("❌ EPG processing failed: ${e.message}", 0, "Failed")
            Log.e(TAG, "EPG processing error", e)
            writeCrashLog(e)
        } finally {
            stopEpgProcessing()
        }
    }

    // --- Filtering Logic (adapted from FilterProgressActivity) ---
    private fun shouldKeepChannel(channelTextContent: String): Boolean {
        // If both filters are disabled, keep all channels
        if (!config.removeNonLatin && !config.removeNonEnglish) {
            return true
        }
        // If Non-English filter is enabled, use the stricter filter (ignores Non-Latin setting)
        if (config.removeNonEnglish) {
            // Stricter pattern: Only allow basic ASCII characters
            // This excludes accented letters like é, ñ, ü, etc.
            val nonEnglishPattern = Pattern.compile("[^\\u0020-\\u007E]")
            return !nonEnglishPattern.matcher(channelTextContent).find()
        }
        // If only Non-Latin filter is enabled, use the less strict filter
        if (config.removeNonLatin) {
            return !nonLatinPattern.matcher(channelTextContent).find()
        }
        return true // Default to keeping if no conditions matched (shouldn't happen with above logic)
    }
    // ---------------------------------------------------------------

    private fun processEpgFile(epgFile: File) {
        val outputDir = File(this.filesDir, "output")
        if (!outputDir.exists()) outputDir.mkdirs()
        val keptFile = File(outputDir, "kept_channels.xml")
        val removedFile = File(outputDir, "removed_channels.xml")
        var inputStream: InputStream? = null
        var keptWriter: BufferedWriter? = null
        var removedWriter: BufferedWriter? = null
        try {
            // Phase 1: Count channels and programmes for accurate progress
            currentPhase = "Analyzing file structure"
            logAndSend("🔍 Analyzing EPG file structure...", 10, currentPhase)
            countElementsInFile(epgFile)

            // Phase 2: Process the file
            currentPhase = "Processing EPG data"
            logAndSend("🔄 Starting EPG processing...", 15, currentPhase)
            updateNotification("Processing EPG data...")

            inputStream = if (epgFile.name.endsWith(".gz", ignoreCase = true)) {
                GZIPInputStream(FileInputStream(epgFile))
            } else {
                FileInputStream(epgFile)
            }

            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(inputStream, null)

            keptWriter = keptFile.bufferedWriter()
            removedWriter = removedFile.bufferedWriter()

            // Read and copy XML declaration
            val xmlDeclaration = readXmlDeclaration(epgFile)
            keptWriter.write("$xmlDeclaration\n")
            removedWriter.write("$xmlDeclaration\n")

            var insideChannel = false
            var insideProgramme = false
            var currentChannelId: String? = null
            var shouldKeepCurrentChannel = false // Decision for the current channel being processed
            val channelBuffer = StringBuilder()
            var tvRootWritten = false
            var tvRootClosed = false
            // --- Use class-level counters ---
            // processedChannels = 0 // Already reset in startEpgProcessing
            // processedProgrammes = 0 // Already reset in startEpgProcessing
            // keptChannelsCount, removedChannelsCount, etc. are class-level
            // --------------------------------

            // Helper function to get the correct writer based on current context for programmes/content
            // For channels, the decision is made upfront and the whole buffer is written to the correct file at the end.
            fun getCurrentWriter(): BufferedWriter = if (shouldKeepCurrentChannel) keptWriter else removedWriter

            // --- Variables to capture channel content for filtering ---
            val channelTextContentBuffer = StringBuilder()
            // ------------------------------------------------------------

            // --- Variables for throttling progress updates ---
            // Send an update to the UI every N elements processed to avoid overwhelming it.
            val progressUpdateInterval = 500 // Adjust this value as needed (e.g., 100, 1000)
            var lastUpdateCount = 0
            // -------------------------------------------------

            while (parser.eventType != XmlPullParser.END_DOCUMENT && isProcessing) {
                try {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "tv" -> {
                                    if (!tvRootWritten) {
                                        val tvElement = buildXmlStartTag(parser)
                                        keptWriter.write("$tvElement\n")
                                        removedWriter.write("$tvElement\n")
                                        keptWriter.flush()
                                        removedWriter.flush()
                                        tvRootWritten = true
                                    }
                                }
                                "channel" -> {
                                    insideChannel = true
                                    currentChannelId = parser.getAttributeValue(null, "id")
                                    processedChannels++
                                    // --- Reset state for new channel ---
                                    shouldKeepCurrentChannel = true // Default assumption, will be checked
                                    channelBuffer.setLength(0) // Clear buffer for new channel
                                    channelTextContentBuffer.setLength(0) // Clear text buffer for filtering
                                    channelBuffer.append(buildXmlStartTag(parser)) // Start building the channel element in buffer
                                    // -----------------------------------
                                    // --- More Frequent Progress Updates ---
                                    // Check if we need to send an update based on channel count
                                    if (processedChannels - lastUpdateCount >= progressUpdateInterval) {
                                        // Send a log message indicating ongoing processing
                                        logAndSend(
                                            "📺 Processing channels... ($processedChannels/$totalChannels)",
                                            // Calculate progress percentage: 15% (start) + (channels processed / total channels) * 40% (channel weight)
                                            15 + ((processedChannels.toLong() * 40) / totalChannels.coerceAtLeast(1)).toInt(),
                                            currentPhase
                                        )
                                        lastUpdateCount = processedChannels
                                    }
                                    // --- End Frequent Updates ---
                                }
                                "programme" -> {
                                    insideProgramme = true
                                    processedProgrammes++
                                    val programmeChannelId = parser.getAttributeValue(null, "channel")

                                    // --- Determine if programme should be kept ---
                                    // Look up the decision made for the channel this programme belongs to.
                                    // If the channel ID is not found (shouldn't happen in a well-formed EPG after channel processing),
                                    // default to keeping it or handle as an error.
                                    shouldKeepCurrentChannel = if (programmeChannelId != null) {
                                        channelFilterDecisions.getOrDefault(programmeChannelId, true) // Default to keeping if not found
                                    } else {
                                        Log.w(TAG, "Programme element found without a 'channel' attribute.")
                                        true // Default to keeping if no channel ID
                                    }
                                    // Update programme counters based on the decision
                                    if (shouldKeepCurrentChannel) {
                                        keptProgrammesCount++
                                    } else {
                                        removedProgrammesCount++
                                    }
                                    // Write programme start tag to the correct file
                                    getCurrentWriter().write(buildXmlStartTag(parser))
                                    // ------------------------------------------------

                                    // --- More Frequent Progress Updates ---
                                    // Check if we need to send an update based on programme count
                                    if (processedProgrammes - lastUpdateCount >= progressUpdateInterval) {
                                        // Send a log message indicating ongoing processing
                                        logAndSend(
                                            "📺 Processing programmes... ($processedProgrammes/$totalProgrammes)",
                                            // Calculate progress percentage: 55% (start of programmes) + (programmes processed / total programmes) * 40% (programme weight)
                                            55 + ((processedProgrammes.toLong() * 40) / totalProgrammes.coerceAtLeast(1)).toInt(),
                                            currentPhase
                                        )
                                        lastUpdateCount = processedProgrammes
                                    }
                                    // --- End Frequent Updates ---
                                }
                                else -> {
                                    // Handle other elements (display-name, icon, title, desc, etc.)
                                    val elementString = buildXmlStartTag(parser)
                                    if (insideChannel) {
                                        // Append to channel buffer for structural content
                                        channelBuffer.append(elementString)
                                        // --- Capture text content for filtering decision ---
                                        // We specifically look for display-name to decide if we keep the channel.
                                        // Other elements inside channel could be captured too if needed.
                                        if (parser.name == "display-name") {
                                            // The text content will come in the next TEXT event(s).
                                            // We don't do anything here, just prepare to capture it.
                                        }
                                        // ----------------------------------------------------
                                    } else if (insideProgramme) {
                                        // Write to the correct programme file
                                        getCurrentWriter().write(elementString)
                                    } else {
                                        // Elements outside channel/programme - write to kept file for now
                                        keptWriter.write(elementString)
                                    }
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            val text = parser.text
                            if (text != null) { // Handle all text, including whitespace
                                val escapedText = escapeXmlText(text)
                                if (insideChannel) {
                                    // Append to channel structural buffer
                                    channelBuffer.append(escapedText)
                                    // --- Capture text content specifically for display-name ---
                                    // This assumes display-name text follows the <display-name> tag immediately.
                                    // A more robust parser might track the last opened tag name.
                                    // For simplicity, we'll assume text inside a channel relates to the last opened tag for filtering.
                                    // This is generally true for EPG structure.
                                    channelTextContentBuffer.append(text) // Append raw text for filtering logic
                                    // ----------------------------------------------------------
                                } else if (insideProgramme) {
                                    // Write to the correct programme file
                                    getCurrentWriter().write(escapedText)
                                } else {
                                    // Text outside specific tags - write to kept file
                                    keptWriter.write(escapedText)
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "channel" -> {
                                    insideChannel = false
                                    // Complete the channel element in the buffer
                                    channelBuffer.append("</channel>\n")

                                    // --- Apply Filtering Logic for the Channel ---
                                    // Now that we have captured the relevant text content, decide if the channel should be kept.
                                    val channelTextForFiltering = channelTextContentBuffer.toString()
                                    shouldKeepCurrentChannel = shouldKeepChannel(channelTextForFiltering)

                                    // Store the decision for this channel ID for associated programmes
                                    if (currentChannelId != null) {
                                        channelFilterDecisions[currentChannelId] = shouldKeepCurrentChannel
                                    } else {
                                        Log.w(TAG, "Channel element found without an 'id' attribute. Cannot store filter decision.")
                                        // Decide what to do if channel has no ID? For now, assume it's kept if logic says so.
                                    }

                                    // Update channel counters
                                    if (shouldKeepCurrentChannel) {
                                        keptChannelsCount++
                                    } else {
                                        removedChannelsCount++
                                    }

                                    // --- Write the completed channel element to the appropriate file ---
                                    if (shouldKeepCurrentChannel) {
                                        keptWriter.write(channelBuffer.toString())
                                        // keptWriter.flush() // Optional flush here if needed for large channels
                                    } else {
                                        removedWriter.write(channelBuffer.toString())
                                        // removedWriter.flush() // Optional flush here if needed for large channels
                                    }
                                    // -------------------------------------------------------------------
                                    channelBuffer.setLength(0) // Clear buffer after writing
                                    channelTextContentBuffer.setLength(0) // Clear text buffer
                                    currentChannelId = null
                                    // shouldKeepCurrentChannel remains set for any subsequent programmes
                                    // that might be associated if EPG structure allows, though typically programmes
                                    // are nested within or follow channel definitions. It's correctly looked up per programme now.
                                }
                                "programme" -> {
                                    insideProgramme = false
                                    // Complete the programme element and write to the correct file
                                    getCurrentWriter().write("</programme>\n")
                                    // Counters (keptProgrammesCount/removedProgrammesCount) already updated in START_TAG
                                }
                                "tv" -> {
                                    if (!tvRootClosed) {
                                        currentPhase = "Finalizing files"
                                        logAndSend("📝 Writing final tags...", 95, currentPhase)
                                        updateNotification("Finalizing files...")
                                        keptWriter.write("</tv>\n")
                                        removedWriter.write("</tv>\n")
                                        keptWriter.flush()
                                        removedWriter.flush()
                                        tvRootClosed = true
                                    }
                                }
                                else -> {
                                    // Handle closing of other elements
                                    val closingTag = "</${parser.name}>"
                                    if (insideChannel) {
                                        // Append closing tag to channel structural buffer
                                        channelBuffer.append(closingTag)
                                        // --- Capture closing tags for text content if needed ---
                                        // For filtering based on display-name, closing tags don't add text.
                                        // If filtering involved element structure, you might note it.
                                        // For now, no action needed for text buffer on closing tags.
                                        // --------------------------------------------------------
                                    } else if (insideProgramme) {
                                        // Write closing tag to the correct programme file
                                        getCurrentWriter().write(closingTag)
                                    } else {
                                        // Closing tags outside channel/programme - write to kept file
                                        keptWriter.write(closingTag)
                                    }
                                }
                            }
                        }
                    }
                    parser.next()
                } catch (e: Exception) {
                    logAndSend("⚠️ Parser error: ${e.message}", currentProgress, "Parser error")
                    Log.e(TAG, "Parser error during EPG processing", e)
                    writeCrashLog(e)
                    break // Exit the processing loop on error
                }
            }

            // Ensure TV root is closed if not already done (defensive)
            if (!tvRootClosed && tvRootWritten) {
                keptWriter.write("</tv>\n")
                removedWriter.write("</tv>\n")
                keptWriter.flush()
                removedWriter.flush()
            }

            // keptWriter.flush() // Final flush, already done above
            // removedWriter.flush() // Final flush, already done above

            currentPhase = "Completed"
            // --- Updated final log message with detailed counts ---
            val finalMsg = "✅ Processing complete: $processedChannels channels ($keptChannelsCount kept, $removedChannelsCount removed), $processedProgrammes programmes ($keptProgrammesCount kept, $removedProgrammesCount removed)"
            logAndSend(finalMsg, 100, currentPhase)
            updateNotification("Processing completed!")
        } catch (e: Exception) {
            val errorMsg = "❌ Error processing EPG: ${e.message}"
            logAndSend(errorMsg, 0, "Error")
            updateNotification(errorMsg)
            Log.e(TAG, "Processing error in processEpgFile", e)
            writeCrashLog(e)
            // Re-throw to be caught by processEpgInBackground
            throw e
        } finally {
            safeClose(keptWriter, "kept writer")
            safeClose(removedWriter, "removed writer")
            safeClose(inputStream, "input stream")
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
            logAndSend("📊 Analysis complete: $totalChannels channels, $totalProgrammes programmes found", 12, "Analysis complete")
        } catch (e: Exception) {
            Log.w(TAG, "Could not count elements, using estimates", e)
            // Use estimates if counting fails
            totalChannels = 2000
            totalProgrammes = 50000
        } finally {
            safeClose(inputStream, "counting input stream")
        }
    }

    private fun updateChannelProgress() {
        if (totalChannels > 0) {
            // Channels are weighted as 40% of total progress (15% to 55%)
            val channelProgress = (processedChannels * 40 / totalChannels)
            currentProgress = 15 + channelProgress
            currentStatus = "Processing channel $processedChannels of $totalChannels"
            if (processedChannels % 10 == 0 || processedChannels == totalChannels) { // Check for completion too
                sendCurrentProgress()
                updateNotification(currentStatus)
            }
        }
    }

    private fun updateProgrammeProgress() {
        if (totalProgrammes > 0) {
            // Programmes are weighted as 40% of total progress (55% to 95%)
            val programmeProgress = (processedProgrammes * 40 / totalProgrammes)
            currentProgress = 55 + programmeProgress
            currentStatus = "Processing programme $processedProgrammes of $totalProgrammes"
            sendCurrentProgress()
            updateNotification(currentStatus)
        }
    }

    private fun readXmlDeclaration(epgFile: File): String {
        return try {
            val inputStream = if (epgFile.name.endsWith(".gz", ignoreCase = true)) {
                GZIPInputStream(FileInputStream(epgFile))
            } else {
                FileInputStream(epgFile)
            }
            inputStream.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream))
                val firstLine = reader.readLine()?.trim()
                if (firstLine != null && firstLine.startsWith("<?xml")) {
                    firstLine
                } else {
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read XML declaration: ${e.message}")
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        }
    }

    private fun safeClose(closeable: Closeable?, name: String) {
        try {
            closeable?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing $name", e)
        }
    }

    private fun writeCrashLog(exception: Exception) {
        try {
            val crashFile = File(this.filesDir, "crash_log.txt")
            crashFile.appendText("\n=== CRASH ${System.currentTimeMillis()} ===\n")
            crashFile.appendText("Exception: ${exception.javaClass.simpleName}\n")
            crashFile.appendText("Message: ${exception.message}\n")
            crashFile.appendText("Stack trace:\n")
            exception.stackTrace.forEach { element ->
                crashFile.appendText("  at $element\n")
            }
            crashFile.appendText("=== END CRASH ===\n")
        } catch (e: Exception) {
            Log.e(TAG, "Could not write crash log", e)
        }
    }

    private fun logAndSend(message: String, percentage: Int, phase: String) {
        Log.d(TAG, message)
        currentProgress = percentage
        currentStatus = message
        currentPhase = phase
        sendProgressUpdate(
            message,
            percentage,
            totalChannels,
            processedChannels,
            totalProgrammes,
            processedProgrammes,
            phase
        )
    }

    private fun buildXmlStartTag(parser: XmlPullParser): String {
        val tagBuilder = StringBuilder("<${parser.name}")
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            val attrValue = parser.getAttributeValue(i)
            tagBuilder.append(" $attrName=\"${escapeXmlAttribute(attrValue)}\"")
        }
        tagBuilder.append(">")
        return tagBuilder.toString()
    }

    // --- CRITICAL FIX: Corrected XML Escaping ---
    private fun escapeXmlAttribute(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "<")  // <-- FIXED: Was .replace("<", "<")
            .replace(">", ">")  // <-- FIXED: Was .replace(">", ">")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun escapeXmlText(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "<")   // <-- FIXED: Was .replace("<", "<")
            .replace(">", ">")   // <-- FIXED: Was .replace(">", ">")
    }
    // --- END CRITICAL FIX ---

    // --- ADD DEBUG LOGGING TO sendProgressUpdate ---
    private fun sendProgressUpdate(
        message: String,
        percentage: Int,
        totalChannels: Int,
        processedChannels: Int,
        totalProgrammes: Int,
        processedProgrammes: Int,
        phase: String
    ) {
        // --- ADD THIS LINE AT THE VERY BEGINNING ---
        Log.d(TAG, "EpgProcessorService: About to send broadcast with message: '$message'")
        // --- END ADDITION ---
        val intent = Intent("EPG_PROGRESS_UPDATE")
        intent.putExtra("message", message)
        intent.putExtra("percentage", percentage)
        intent.putExtra("totalChannels", totalChannels)
        intent.putExtra("processedChannels", processedChannels)
        intent.putExtra("totalProgrammes", totalProgrammes)
        intent.putExtra("processedProgrammes", processedProgrammes)
        intent.putExtra("phase", phase)
        // --- ADD THIS LINE RIGHT BEFORE sendBroadcast ---
        Log.d(TAG, "EpgProcessorService: Actually calling sendBroadcast now.")
        // --- END ADDITION ---
        Log.d(TAG, "EpgProcessorService: Actually calling sendBroadcast now. Intent action: ${intent.action}")
        sendBroadcast(intent)
        Log.d(TAG, "EpgProcessorService: sendBroadcast call completed.")
        // --- ADD THIS LINE RIGHT AFTER sendBroadcast ---
        Log.d(TAG, "EpgProcessorService: sendBroadcast call completed.")
        // --- END ADDITION ---
    }
    // --- END DEBUG LOGGING ---

    private fun findExistingEPGFile(directory: File): File? {
        return directory.listFiles()?.find {
            it.name.endsWith(".xml", ignoreCase = true) ||
                    it.name.endsWith(".xml.gz", ignoreCase = true)
        }
    }
}