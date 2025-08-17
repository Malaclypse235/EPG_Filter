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

class FilterProgressActivity : Activity() {
    private lateinit var textTitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var textStatus: TextView
    private lateinit var textPercent: TextView
    private lateinit var textProgress: TextView
    private lateinit var buttonStart: Button
    private lateinit var buttonPause: Button
    private lateinit var buttonCancel: Button
    private lateinit var scrollView: ScrollView

    private val TAG = "FilterProgress"

    // --- Log State ---
    private val logLines = mutableListOf<String>()
    private var m3uStartLineIndex = -1  // Track "Starting M3U..." line
    private var m3uLineIndex = -1
    private var channelsLineIndex = -1
    private var programmesLineIndex = -1
    private var epgStartLineIndex = -1

    // --- Throttling ---
    private var lastProgressUpdate = 0L
    private val PROGRESS_UPDATE_INTERVAL = 500L // Once per 500ms

    private lateinit var config: ConfigManager.ConfigData
    private var playlistPath: String? = null
    private var epgPath: String? = null

    // --- Emoji Constants ---
    private companion object {
        object Emojis {
            const val M3U = "📡"
            const val CHANNELS = "📺"
            const val PROGRAMMES = "🎬"
            const val DONE = "✅"
            const val START = "🔄"
            const val INFO = "🔹"
        }
    }

    private val epgProgressReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            val message = intent.getStringExtra("message") ?: return
            val percentage = intent.getIntExtra("percentage", -1)
            val phase = intent.getStringExtra("phase") ?: "Processing"
            val messageType = intent.getStringExtra("messageType") ?: "STATUS"

            runOnUiThread {
                try {
                    when (messageType) {
                        EpgProcessorService.MSG_PROGRESS_M3U -> {
                            updateProgressLine(
                                "${Emojis.M3U} M3U: $message",
                                ::m3uLineIndex,
                                ::m3uLineIndex::set,
                                percentage,
                                message
                            )
                        }
                        EpgProcessorService.MSG_PROGRESS_CHANNELS -> {
                            updateProgressLine(
                                "${Emojis.CHANNELS} Channels: $message",
                                ::channelsLineIndex,
                                ::channelsLineIndex::set,
                                percentage,
                                message
                            )
                        }

                        EpgProcessorService.MSG_LOG -> {
                            Log.d("FILTER_DEBUG", "Received log message: '$message'")

                            // Handle M3U start: create the line
                            if (message.contains("Starting M3U filtering", ignoreCase = true)) {
                                updateProgressLine(
                                    "${Emojis.M3U} Starting M3U filtering...",
                                    ::m3uStartLineIndex,
                                    ::m3uStartLineIndex::set,
                                    percentage,
                                    message
                                )
                            }

                            // Handle EPG count: update the "Starting..." line with count
                            else if (message.contains("EPG:") && message.contains("channels found")) {
                                Log.d("FILTER_DEBUG", "🔢 EPG count message: $message")
                                val count = Regex("\\d+").find(message)?.value ?: "0"
                                val updatedLine = "${Emojis.CHANNELS} Starting EPG filtering... ($count channels)"

                                updateProgressLine(
                                    updatedLine,
                                    { epgStartLineIndex },
                                    { epgStartLineIndex = it },
                                    percentage,
                                    message
                                )
                            }

                            // Handle live EPG channel counting
                            else if (phase == "EPG_Count" && message.startsWith("Channels:")) {
                                val count = Regex("\\d+").find(message)?.value ?: "0"
                                val updatedLine = "${Emojis.CHANNELS} $count channels found"

                                updateProgressLine(
                                    updatedLine,
                                    { epgStartLineIndex },        // ← Reuse the same line as "Starting EPG..."
                                    { epgStartLineIndex = it },
                                    percentage,
                                    message
                                )
                            }
                            // All other logs
                            else {
                                if ((message.contains("M3U:") || message.contains("EPG:")) &&
                                    message.contains("kept,") &&
                                    message.contains("removed")) {
                                    appendLog(message, phase)
                                    appendLog("", phase) // Blank line after result
                                } else {
                                    appendLog(message, phase)
                                }
                            }
                        }
                        else -> {
                            // General status (use clean message without emoji)
                            textStatus.text = message
                            if (percentage in 0..100) {
                                progressBar.progress = percentage
                                textPercent.text = "$percentage%"
                            }
                        }
                    }

                    // Final states
                    if (phase == "Complete" || phase.contains("Error")) {
                        buttonStart.isEnabled = true
                        buttonStart.text = "Start Filtering"
                        buttonPause.isEnabled = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "UI update failed", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter_progress)

        // Bind views
        textTitle = findViewById(R.id.textTitle)
        progressBar = findViewById(R.id.progressBar)
        textStatus = findViewById(R.id.textStatus)
        textPercent = findViewById(R.id.textPercent)
        textProgress = findViewById(R.id.textProgress)
        buttonStart = findViewById(R.id.buttonStart)
        buttonPause = findViewById(R.id.buttonPause)
        buttonCancel = findViewById(R.id.buttonCancel)
        scrollView = findViewById(R.id.scrollContainer)

        // Load config
        val loadResult = ConfigManager.loadConfig(this)
        config = loadResult.config

        playlistPath = intent.getStringExtra("PLAYLIST_PATH")
        epgPath = intent.getStringExtra("EPG_PATH")

        // Register receiver
        registerReceiver(epgProgressReceiver, IntentFilter("EPG_PROGRESS_UPDATE"), Context.RECEIVER_EXPORTED)

        // Initial UI
        buttonStart.isEnabled = false
        buttonStart.text = "Loading..."
        buttonPause.isEnabled = false
        buttonCancel.isEnabled = true

        startFileSync()
    }

    override fun onDestroy() {
        unregisterReceiver(epgProgressReceiver)
        super.onDestroy()
    }

    // --- Progress Line Management ---
    private fun updateProgressLine(
        line: String,
        indexRef: () -> Int,
        setIndex: (Int) -> Unit,
        percentage: Int,
        originalMessage: String
    ) {
        // Always update the status bar and progress
        textStatus.text = originalMessage
        if (percentage in 0..100) {
            progressBar.progress = percentage
            textPercent.text = "$percentage%"
        }

        // Always update the log line
        if (indexRef() == -1 || indexRef() >= logLines.size) {
            logLines.add(line)
            setIndex(logLines.lastIndex)
        } else {
            logLines[indexRef()] = line
        }

        // Always refresh the displayed log
        textProgress.text = logLines.joinToString("\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun appendLog(message: String, phase: String) {
        logLines.add(message)

        if (logLines.size > 50) {
            logLines.removeAt(0)
        }

        textProgress.text = logLines.joinToString("\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    // --- File Sync ---
    private fun startFileSync() {
        Thread {
            try {
                var totalSteps = 0
                var currentStep = 0

                // ✅ FIXED: access via config.system
                if (!config.system.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) totalSteps += 3
                if (!config.system.disableEPGFiltering && !epgPath.isNullOrEmpty()) totalSteps += 3

                if (totalSteps == 0) {
                    runOnUiThread {
                        textStatus.text = "Nothing to sync"
                        appendLog("⚠\uFE0F No files selected or filtering disabled", "Error")
                        buttonStart.isEnabled = false
                        buttonStart.text = "Nothing to Filter"
                    }
                    return@Thread
                }

                if (!config.system.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) {
                    processM3UFile(playlistPath!!, currentStep, totalSteps)
                    currentStep += 3
                }

                if (!config.system.disableEPGFiltering && !epgPath.isNullOrEmpty()) {
                    processEPGFile(epgPath!!, currentStep, totalSteps)
                    currentStep += 3
                }

                runOnUiThread {
                    textStatus.text = "Ready to filter"
                    buttonStart.isEnabled = true
                    buttonStart.text = "Start Filtering"
                    buttonPause.isEnabled = false

                    appendLog("❇\uFE0F All files are current", "SyncDone")
                    logLines.add("") // Blank line after "All files are current"
                    appendLog("✨ Press 'Start Filtering' to begin ✨", "Info")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    textStatus.text = "Sync error"
                    appendLog("Sync failed: ${e.message}", "Error")
                    buttonStart.isEnabled = false
                }
            }
        }.start()
    }

    private fun processM3UFile(path: String, startingStep: Int, totalSteps: Int) {
        val file = File(path)
        val filename = file.name
        var currentStep = startingStep

        try {
            currentStep++
            runOnUiThread {
                textStatus.text = "Checking M3U: $filename"
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                logLines.add("\uD83D\uDCE1 Checking for updates to: $filename")
                textProgress.text = logLines.joinToString("\n")
            }
            Thread.sleep(200)

            if (!file.exists()) {
                runOnUiThread {
                    logLines.add("❌ M3U file not found: $path")
                    textProgress.text = logLines.joinToString("\n")
                }
                return
            }

            val inputDir = File(filesDir, "input").apply { mkdirs() }
            val existing = findExistingM3UFile(inputDir)
            val needsUpdate = existing == null || file.lastModified() > existing.lastModified()

            currentStep++
            if (needsUpdate) {
                runOnUiThread {
                    textStatus.text = "Updating M3U: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    logLines.add("\uD83D\uDCE1 Updating file: $filename")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)

                if (existing != null && existing.name != filename) {
                    existing.delete()
                }

                val dest = File(inputDir, filename)
                file.copyTo(dest, overwrite = true)

                runOnUiThread {
                    logLines.add("\uD83D\uDCE1 $filename updated")
                    textProgress.text = logLines.joinToString("\n")
                }
            } else {
                runOnUiThread {
                    textStatus.text = "M3U up to date: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    logLines.add("\uD83D\uDCE1 $filename does not need updating")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)
            }

            currentStep++
            runOnUiThread {
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                logLines.add("✅ $filename validated")
                logLines.add("") // Blank line after validation
                textProgress.text = logLines.joinToString("\n")
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        } catch (e: Exception) {
            runOnUiThread {
                logLines.add("❌ Failed to sync M3U: ${e.message}")
                textProgress.text = logLines.joinToString("\n")
            }
        }
    }

    private fun processEPGFile(path: String, startingStep: Int, totalSteps: Int) {
        val file = File(path)
        val filename = file.name
        var currentStep = startingStep

        try {
            currentStep++
            runOnUiThread {
                textStatus.text = "Checking EPG: $filename"
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                logLines.add("\uD83D\uDCFA Checking for updates to: $filename")
                textProgress.text = logLines.joinToString("\n")
            }
            Thread.sleep(200)

            if (!file.exists()) {
                runOnUiThread {
                    logLines.add("❌ EPG file not found: $path")
                    textProgress.text = logLines.joinToString("\n")
                }
                return
            }

            val inputDir = File(filesDir, "input").apply { mkdirs() }
            val existing = findExistingEPGFile(inputDir)
            val needsUpdate = existing == null || file.lastModified() > existing.lastModified()

            currentStep++
            if (needsUpdate) {
                runOnUiThread {
                    textStatus.text = "Updating EPG: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    logLines.add("\uD83D\uDCFA Updating file: $filename")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)

                if (existing != null && existing.name != filename) {
                    existing.delete()
                }

                val dest = File(inputDir, filename)
                file.copyTo(dest, overwrite = true)

                runOnUiThread {
                    logLines.add("\uD83D\uDCFA $filename updated")
                    textProgress.text = logLines.joinToString("\n")
                }
            } else {
                runOnUiThread {
                    textStatus.text = "EPG up to date: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    logLines.add("\uD83D\uDCFA $filename does not need updating")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)
            }

            currentStep++
            runOnUiThread {
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                logLines.add("✅ $filename validated")
                logLines.add("") // Blank line after validation
                textProgress.text = logLines.joinToString("\n")
                scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        } catch (e: Exception) {
            runOnUiThread {
                logLines.add("❌ Failed to sync EPG: ${e.message}")
                textProgress.text = logLines.joinToString("\n")
            }
        }
    }

    // --- Button Listeners ---
    private fun setupButtonListeners() {
        buttonStart.setOnClickListener {
            if (buttonStart.text == "Start Filtering") {
                logLines.clear()
                m3uStartLineIndex = -1
                m3uLineIndex = -1
                channelsLineIndex = -1
                programmesLineIndex = -1
                textProgress.text = ""

                logLines.add("  ✨ FILTERING STARTED ✨")
                logLines.add("────────────────────────")
                logLines.add("")
                textProgress.text = logLines.joinToString("\n")

                val serviceIntent = Intent(this, EpgProcessorService::class.java)
                serviceIntent.action = EpgProcessorService.ACTION_START_EPG_PROCESSING
                if (playlistPath != null) serviceIntent.putExtra("PLAYLIST_PATH", playlistPath)
                if (epgPath != null) serviceIntent.putExtra("EPG_PATH", epgPath)
                startService(serviceIntent)

                buttonStart.isEnabled = false
                buttonPause.isEnabled = true
            }
        }

        buttonPause.setOnClickListener {
            val serviceIntent = Intent(this, EpgProcessorService::class.java)
            if (buttonPause.text == "Pause") {
                serviceIntent.action = EpgProcessorService.ACTION_PAUSE
                startService(serviceIntent)
                buttonPause.text = "Resume"
            } else {
                serviceIntent.action = EpgProcessorService.ACTION_RESUME
                startService(serviceIntent)
                buttonPause.text = "Pause"
            }
        }

        buttonCancel.setOnClickListener {
            val serviceIntent = Intent(this, EpgProcessorService::class.java)
            serviceIntent.action = EpgProcessorService.ACTION_STOP_EPG_PROCESSING
            startService(serviceIntent)
            finish()
        }
    }

    // --- File Helpers ---
    private fun findExistingM3UFile(directory: File): File? {
        return directory.listFiles()?.find { it.isPlaylist() }
    }

    private fun findExistingEPGFile(directory: File): File? {
        return directory.listFiles()?.find { it.isEpg() }
    }

    override fun onResume() {
        super.onResume()
        setupButtonListeners()
    }
}

// File extension helpers
private fun File.isPlaylist() = name.endsWith(".m3u", true) || name.endsWith(".m3u8", true)
private fun File.isEpg() = name.endsWith(".xml", true) || name.endsWith(".xml.gz", true)