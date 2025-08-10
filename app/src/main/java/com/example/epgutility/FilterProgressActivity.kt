package com.example.epgutility

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

private const val TIME_TO_PAUSE: Long = 250L

class FilterProgressActivity : Activity() {
    private lateinit var textTitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var textStatus: TextView
    private lateinit var textPercent: TextView
    private lateinit var textProgress: TextView
    private lateinit var buttonStart: Button
    private lateinit var buttonPause: Button
    private lateinit var buttonCancel: Button

    private val TAG = "FilterProgress"
    private lateinit var config: ConfigManager.ConfigData
    private var isProcessing = false
    private var isPaused = false

    // Broadcast receiver to get updates from EPG service
    private val epgProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) {
                Log.w(TAG, "Received null intent in epgProgressReceiver")
                return
            }

            val message = intent.getStringExtra("message")
            val percentage = intent.getIntExtra("percentage", -1)
            val phase = intent.getStringExtra("phase") ?: "Processing"

            if (message == null) {
                Log.w(TAG, "Received broadcast with null 'message' extra")
                return
            }

            runOnUiThread {
                try {
                    // Handle "Processing programmes" as a single updating line
                    if (message.contains("Processing programmes") ||
                        message.contains("Processing channels")) {
                        // Update status text only, don't append to log
                        textStatus.text = message
                        if (percentage in 0..100) {
                            progressBar.progress = percentage
                            textPercent.text = "$percentage%"
                        }
                        return@runOnUiThread
                    }

                    // Handle other messages normally
                    if (message.isNotBlank() &&
                        !message.contains("remove this text") &&
                        message != "Idle") {

                        val currentLog = textProgress.text.toString()

                        // Add blank lines at key points
                        when {
                            message.contains("validated") -> {
                                textProgress.text = "$currentLog\n$message"
                            }
                            message.contains("All files are current") -> {
                                textProgress.text = "$currentLog\n\n$message"
                            }
                            message.contains("Playlist: ready") -> {
                                textProgress.text = "$currentLog\n\n$message"
                            }
                            else -> {
                                textProgress.text = "$currentLog\n$message"
                            }
                        }

                        // Update progress bar for valid percentages
                        if (percentage in 0..100) {
                            progressBar.progress = percentage
                            textPercent.text = "$percentage%"
                        }

                        // Update status text
                        textStatus.text = message

                        // Auto-scroll to bottom
                        val scrollView = findViewById<ScrollView>(R.id.scrollContainer)
                        scrollView.post {
                            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }
                    }

                    // Handle final states
                    if (phase == "Completed" || phase == "Error") {
                        buttonStart.isEnabled = true
                        buttonStart.text = "Start Filtering"
                        buttonPause.isEnabled = false
                        isProcessing = false
                    }
                } catch (uiException: Exception) {
                    Log.e(TAG, "Exception inside runOnUiThread block: ${uiException.message}", uiException)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter_progress)


        // Initialize UI elements
        textTitle = findViewById(R.id.textTitle)
        progressBar = findViewById(R.id.progressBar)
        textStatus = findViewById(R.id.textStatus)
        textPercent = findViewById(R.id.textPercent)
        textProgress = findViewById(R.id.textProgress)
        buttonStart = findViewById(R.id.buttonStart)
        buttonPause = findViewById(R.id.buttonPause)
        buttonCancel = findViewById(R.id.buttonCancel)

        // Load config
        val loadResult = ConfigManager.loadConfig(this)
        config = loadResult.config

        val playlistPath = intent.getStringExtra("PLAYLIST_PATH")
        val epgPath = intent.getStringExtra("EPG_PATH")

        Log.d(TAG, "Playlist Path: $playlistPath")
        Log.d(TAG, "EPG Path: $epgPath")

        // Register broadcast receiver
        val filter = IntentFilter("EPG_PROGRESS_UPDATE")
        registerReceiver(epgProgressReceiver, filter, RECEIVER_EXPORTED)
        Log.d(TAG, "FilterProgressActivity: epgProgressReceiver registered")

        // Set up button listeners
        setupButtonListeners(playlistPath, epgPath)

        // Start processing
        startProcessing(playlistPath, epgPath)
    }

    override fun onResume() {
        super.onResume()
        val serviceIntent = Intent(this, EpgProcessorService::class.java)
        serviceIntent.action = EpgProcessorService.ACTION_GET_PROGRESS
        startService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(epgProgressReceiver)
    }

    private fun setupButtonListeners(playlistPath: String?, epgPath: String?) {
        buttonStart.setOnClickListener {
            if (!isProcessing) {
                textProgress.text = ""
                startFiltering(playlistPath, epgPath)
            }
        }

        buttonPause.setOnClickListener {
            if (isProcessing && !isPaused) {
                pauseProcessing()
            } else if (isProcessing && isPaused) {
                resumeProcessing()
            }
        }

        buttonCancel.setOnClickListener {
            cancelProcessing()
        }

        buttonStart.isEnabled = false
        buttonStart.text = "Updating..."
    }

    private fun startProcessing(playlistPath: String?, epgPath: String?) {
        isProcessing = true
        isPaused = false
        buttonStart.isEnabled = false
        buttonPause.isEnabled = true
        buttonCancel.isEnabled = true
        textStatus.text = "Starting file processing..."
        textPercent.text = "0%"
        progressBar.progress = 0
        textProgress.text = ""

        Thread {
            try {
                val results = mutableListOf<String>()
                var hasProcessedFiles = false
                var totalSteps = 0
                var currentStep = 0

                if (!config.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) totalSteps += 3
                if (!config.disableEPGFiltering && !epgPath.isNullOrEmpty()) totalSteps += 3

                if (totalSteps == 0) {
                    runOnUiThread {
                        textStatus.text = "Nothing to process"
                        textPercent.text = "Done"
                        progressBar.progress = 100
                        textProgress.text = "No files selected or filtering disabled for both M3U and EPG files."
                        buttonStart.isEnabled = false
                        buttonStart.text = "Nothing to Process"
                        buttonPause.isEnabled = false
                        isProcessing = false
                    }
                    return@Thread
                }

                if (!config.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) {
                    val m3uResult = processM3UFile(playlistPath, currentStep, totalSteps)
                    currentStep += 3
                    if (m3uResult.isNotEmpty()) {
                        results.add(m3uResult)
                        hasProcessedFiles = true
                    }
                    runOnUiThread {
                        textProgress.append("\n")
                    }
                }

                if (!config.disableEPGFiltering && !epgPath.isNullOrEmpty()) {
                    val epgResult = processEPGFile(epgPath, currentStep, totalSteps)
                    currentStep += 3
                    if (epgResult.isNotEmpty()) {
                        results.add(epgResult)
                        hasProcessedFiles = true
                    }
                    runOnUiThread {
                        textProgress.append("\n")
                    }
                }

                runOnUiThread {
                    if (hasProcessedFiles) {
                        textStatus.text = "All files processed successfully"
                        textPercent.text = "100%"
                        progressBar.progress = 100
                        val finalLog = textProgress.text.toString() + "\n\n✅ All files are current"
                        textProgress.text = finalLog + "\n\n" + getFilteringStatusSummary(playlistPath, epgPath)
                    }
                    buttonStart.isEnabled = true
                    buttonStart.text = "Start Filtering"
                    buttonPause.isEnabled = false
                    isProcessing = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in file processing", e)
                runOnUiThread {
                    textStatus.text = "Error occurred during processing"
                    textPercent.text = "Error"
                    val errorLog = textProgress.text.toString() + "\n❌ Error: ${e.message}"
                    textProgress.text = errorLog
                    buttonStart.isEnabled = true
                    buttonPause.isEnabled = false
                    isProcessing = false
                }
            }
        }.start()
    }

    private fun startFiltering(playlistPath: String?, epgPath: String?) {

        Log.d("FilterDebug", "Sending start command to EpgProcessorService")
        val serviceIntent = Intent(this, EpgProcessorService::class.java)
        serviceIntent.action = EpgProcessorService.ACTION_START_EPG_PROCESSING
        startService(serviceIntent)
        Log.d("FilterDebug", "Service start command sent")
        isProcessing = true
        isPaused = false
        buttonStart.isEnabled = false
        buttonStart.text = "Filtering..."
        buttonPause.isEnabled = true
        buttonCancel.isEnabled = true

        textStatus.text = "Starting file filtering..."
        textPercent.text = "0%"
        progressBar.progress = 0

        val separator = "=".repeat(50)
        textProgress.text = "$separator\n🔄 STARTING FILE FILTERING\n$separator"

        Thread {
            try {
                if (!config.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) {
                    val result = filterM3UFile(0, if (config.disableEPGFiltering || epgPath.isNullOrEmpty()) 1 else 1)
                    runOnUiThread {
                        textStatus.text = "M3U filtering complete. Starting EPG processing..."
                    }
                } else {
                    runOnUiThread {
                        val logUpdate = textProgress.text.toString() + "\nℹ️ M3U filtering is disabled or no M3U file provided."
                        textProgress.text = logUpdate
                    }
                }

                if (!config.disableEPGFiltering && !epgPath.isNullOrEmpty()) {
                    runOnUiThread {
                        val logUpdate = textProgress.text.toString() + "\n🔍 Starting EPG XML processing in background..."
                        textProgress.text = logUpdate
                        val backgroundNote = textProgress.text.toString() + "\n📱 This will continue processing even if you leave the app"
                        textProgress.text = backgroundNote
                    }

                    Log.d(TAG, "Starting EpgProcessorService...")
                    val serviceIntent = Intent(this@FilterProgressActivity, EpgProcessorService::class.java)
                    serviceIntent.action = EpgProcessorService.ACTION_START_EPG_PROCESSING
                    startService(serviceIntent)
                    Log.d(TAG, "EpgProcessorService start command sent.")
                } else {
                    if (config.disableEPGFiltering || epgPath.isNullOrEmpty()) {
                        runOnUiThread {
                            val logUpdate = textProgress.text.toString() + "\nℹ️ EPG filtering is disabled or no EPG file provided."
                            textProgress.text = logUpdate
                        }
                        if (config.disablePlaylistFiltering || playlistPath.isNullOrEmpty()) {
                            Log.d(TAG, "No filtering steps applicable, signaling local completion.")
                            runOnUiThread {
                                textStatus.text = "Nothing to filter"
                                textPercent.text = "Done"
                                progressBar.progress = 100
                                val noFilterLog = textProgress.text.toString() + "\n⚠️ No applicable files to filter"
                                textProgress.text = noFilterLog
                                buttonStart.isEnabled = true
                                buttonStart.text = "Start Filtering"
                                buttonPause.isEnabled = false
                                isProcessing = false
                            }
                        } else {
                            Log.d(TAG, "M3U filtering initiated, waiting for its completion signal.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initiating filtering process", e)
                runOnUiThread {
                    textStatus.text = "Error initiating filtering"
                    textPercent.text = "Error"
                    val errorLog = textProgress.text.toString() + "\n❌ Error initiating filtering: ${e.message}"
                    textProgress.text = errorLog
                    buttonStart.isEnabled = true
                    buttonStart.text = "Start Filtering"
                    buttonPause.isEnabled = false
                    isProcessing = false
                }
            }
        }.start()
    }

    private fun filterM3UFile(currentStep: Int, totalSteps: Int): String {
        runOnUiThread {
            textStatus.text = "Filtering M3U playlist..."
            val progress = ((currentStep + 1) * 100 / totalSteps)
            progressBar.progress = progress
            textPercent.text = "$progress%"
            textProgress.append("\n🔍 Filtering M3U playlist...")
        }
        return "M3U filtering completed"
    }

    private fun shouldKeepChannel(channelLines: List<String>, nonLatinPattern: java.util.regex.Pattern): Boolean {
        return true
    }

    private fun processM3UFile(playlistPath: String?, startingStep: Int, totalSteps: Int): String {
        try {
            val results = mutableListOf<String>()
            val externalFile = File(playlistPath!!)
            val filename = externalFile.name
            var currentStep = startingStep

            currentStep++
            runOnUiThread {
                textStatus.text = "Checking for updates to file: $filename"
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                textPercent.text = "$progress%"
                textProgress.append("\n📁 Checking for updates to file: $filename")
            }

            Thread.sleep(TIME_TO_PAUSE)

            if (!externalFile.exists()) {
                val error = "❌ M3U file not found at $playlistPath"
                runOnUiThread {
                    textProgress.append("\n$error")
                }
                return error
            }

            val internalDir = File(filesDir, "input")
            if (!internalDir.exists()) internalDir.mkdirs()
            val internalFile = findExistingM3UFile(internalDir)
            val externalModified = externalFile.lastModified()
            val needsUpdate = internalFile == null || internalFile.lastModified() < externalModified

            currentStep++
            if (needsUpdate) {
                runOnUiThread {
                    textStatus.text = "File: $filename updating"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    textPercent.text = "$progress%"
                    textProgress.append("\n📋 File: $filename updating")
                }

                Thread.sleep(TIME_TO_PAUSE)

                if (internalFile != null && internalFile.name != filename) {
                    if (!internalFile.delete()) {
                        val error = "❌ Failed to delete old M3U file: ${internalFile.name}"
                        runOnUiThread {
                            textProgress.append("\n$error")
                        }
                        return error
                    }
                }

                val newInternalFile = File(internalDir, filename)
                try {
                    externalFile.copyTo(newInternalFile, overwrite = true)
                } catch (e: Exception) {
                    val error = "❌ Failed to copy M3U file: ${e.message}"
                    runOnUiThread {
                        textProgress.append("\n$error")
                    }
                    return error
                }

                results.add("$filename updated")
            } else {
                runOnUiThread {
                    textStatus.text = "File: $filename does not need updating"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    textPercent.text = "$progress%"
                    textProgress.append("\n✅ File: $filename does not need updating")
                }

                Thread.sleep(TIME_TO_PAUSE)
                results.add("$filename already up to date")
            }

            currentStep++
            runOnUiThread {
                textStatus.text = "$filename validated (up to date)"
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                textPercent.text = "$progress%"
                textProgress.append("\n✅ $filename validated (up to date)")
            }

            Thread.sleep(TIME_TO_PAUSE)
            results.add("$filename validated")

            return results.joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing M3U file", e)
            val error = "❌ Critical error processing M3U file: ${e.message}"
            runOnUiThread {
                textProgress.append("\n$error")
            }
            return error
        }
    }

    private fun processEPGFile(epgPath: String?, startingStep: Int, totalSteps: Int): String {
        try {
            val results = mutableListOf<String>()
            val externalFile = File(epgPath!!)
            val filename = externalFile.name
            var currentStep = startingStep

            currentStep++
            runOnUiThread {
                textStatus.text = "Checking for updates to file: $filename"
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                textPercent.text = "$progress%"
                textProgress.append("\n📁 Checking for updates to file: $filename")
            }

            Thread.sleep(TIME_TO_PAUSE)

            if (!externalFile.exists()) {
                val error = "❌ EPG file not found at $epgPath"
                runOnUiThread {
                    textProgress.append("\n$error")
                }
                return error
            }

            val internalDir = File(filesDir, "input")
            if (!internalDir.exists()) internalDir.mkdirs()
            val internalFile = findExistingEPGFile(internalDir)
            val externalModified = externalFile.lastModified()
            val needsUpdate = internalFile == null || internalFile.lastModified() < externalModified

            currentStep++
            if (needsUpdate) {
                runOnUiThread {
                    textStatus.text = "File: $filename updating"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    textPercent.text = "$progress%"
                    textProgress.append("\n📋 File: $filename updating")
                }

                Thread.sleep(TIME_TO_PAUSE)

                if (internalFile != null && internalFile.name != filename) {
                    if (!internalFile.delete()) {
                        val error = "❌ Failed to delete old EPG file: ${internalFile.name}"
                        runOnUiThread {
                            textProgress.append("\n$error")
                        }
                        return error
                    }
                }

                val newInternalFile = File(internalDir, filename)
                try {
                    externalFile.copyTo(newInternalFile, overwrite = true)
                } catch (e: Exception) {
                    val error = "❌ Failed to copy EPG file: ${e.message}"
                    runOnUiThread {
                        textProgress.append("\n$error")
                    }
                    return error
                }

                results.add("$filename updated")
            } else {
                runOnUiThread {
                    textStatus.text = "File: $filename does not need updating"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    textPercent.text = "$progress%"
                    textProgress.append("\n✅ File: $filename does not need updating")
                }

                Thread.sleep(TIME_TO_PAUSE)
                results.add("$filename already up to date")
            }

            currentStep++
            runOnUiThread {
                textStatus.text = "$filename validated (up to date)"
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                textPercent.text = "$progress%"
                textProgress.append("\n✅ $filename validated (up to date)")
            }

            Thread.sleep(TIME_TO_PAUSE)
            results.add("$filename validated")

            return results.joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing EPG file", e)
            val error = "❌ Critical error processing EPG file: ${e.message}"
            runOnUiThread {
                textProgress.append("\n$error")
            }
            return error
        }
    }

    private fun pauseProcessing() {
        isPaused = true
        buttonPause.text = "Resume"
        textStatus.text = "Paused..."
    }

    private fun resumeProcessing() {
        isPaused = false
        buttonPause.text = "Pause"
        textStatus.text = "Processing resumed"
    }

    private fun cancelProcessing() {
        isProcessing = false
        isPaused = false
        buttonStart.isEnabled = true
        buttonPause.isEnabled = false
        buttonPause.text = "Pause"
        textStatus.text = "Processing cancelled"
        textPercent.text = "Cancelled"
        textProgress.append("\n🚫 Processing cancelled by user")
    }

    private fun getFilteringStatusSummary(playlistPath: String?, epgPath: String?): String {
        val playlistStatus = when {
            config.disablePlaylistFiltering -> "Not Ready (Disabled by User)"
            playlistPath.isNullOrEmpty() -> "Not Ready (Not Chosen by User)"
            else -> "Ready"
        }
        val epgStatus = when {
            config.disableEPGFiltering -> "Not Ready (Disabled by User)"
            epgPath.isNullOrEmpty() -> "Not Ready (Not Chosen by User)"
            else -> "Ready"
        }
        return "Playlist: $playlistStatus - EPG: $epgStatus"
    }

    private fun findExistingM3UFile(directory: File): File? {
        return directory.listFiles()?.find {
            it.name.endsWith(".m3u", ignoreCase = true) ||
                    it.name.endsWith(".m3u8", ignoreCase = true)
        }
    }

    private fun findExistingEPGFile(directory: File): File? {
        return directory.listFiles()?.find {
            it.name.endsWith(".xml", ignoreCase = true) ||
                    it.name.endsWith(".xml.gz", ignoreCase = true)
        }
    }
}