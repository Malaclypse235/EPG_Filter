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
                                percentage
                            )
                        }
                        EpgProcessorService.MSG_PROGRESS_CHANNELS -> {
                            updateProgressLine(
                                "${Emojis.CHANNELS} Channels: $message",
                                ::channelsLineIndex,
                                ::channelsLineIndex::set,
                                percentage
                            )
                        }

                        EpgProcessorService.MSG_LOG -> {
                            Log.d("FILTER_DEBUG", "Received log message: '$message'")
                            // Handle M3U count
                            if (message.startsWith("M3U_COUNT: ")) {
                                if (m3uStartLineIndex != -1 && m3uStartLineIndex < logLines.size) {
                                    logLines[m3uStartLineIndex] = "${Emojis.M3U} Starting M3U filtering... (${message.substring(11)})"
                                    textProgress.text = logLines.joinToString("\n")
                                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                }
                            }
                            // Handle M3U start
                            else if (message.contains("Starting M3U filtering", ignoreCase = true)) {
                                updateProgressLine(
                                    "${Emojis.M3U} Starting M3U filtering...",
                                    ::m3uStartLineIndex,
                                    ::m3uStartLineIndex::set,
                                    percentage
                                )
                            }
                            // Handle EPG start
                            else if (message.contains("Starting EPG filtering", ignoreCase = true)) {
                                updateProgressLine(
                                    "${Emojis.CHANNELS} Starting EPG filtering...",
                                    ::epgStartLineIndex,
                                    ::epgStartLineIndex::set,
                                    percentage
                                )
                            }
                            // Handle EPG count
                            else if (message.startsWith("EPG_COUNT: ")) {
                                if (epgStartLineIndex != -1 && epgStartLineIndex < logLines.size) {
                                    logLines[epgStartLineIndex] = "${Emojis.CHANNELS} Starting EPG filtering... (${message.substring(11)})"
                                    textProgress.text = logLines.joinToString("\n")
                                    scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
                                }
                            }
                            // All other logs
                            // All other logs
                            else {
                                if ((message.contains("M3U:") || message.contains("EPG:")) &&
                                    message.contains("kept,") &&
                                    message.contains("removed")) {
                                    appendLog(message, phase)     // Log the result
                                    appendLog("", phase)          // Add blank line after
                                } else {
                                    appendLog(message, phase)     // All other logs unchanged
                                }
                            }
                        }
                        else -> {
                            // General status
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
    private fun updateProgressLine(line: String, indexRef: () -> Int, setIndex: (Int) -> Unit, percentage: Int) {
        val now = System.currentTimeMillis()
        if (now - lastProgressUpdate < PROGRESS_UPDATE_INTERVAL) {
            // Throttle log update, but still update status
            textStatus.text = line
            if (percentage in 0..100) {
                progressBar.progress = percentage
                textPercent.text = "$percentage%"
            }
            return
        }

        // Update log
        if (indexRef() == -1 || indexRef() >= logLines.size) {
            logLines.add(line)
            setIndex(logLines.lastIndex)
        } else {
            logLines[indexRef()] = line
        }

        // Apply to UI
        textStatus.text = line
        if (percentage in 0..100) {
            progressBar.progress = percentage
            textPercent.text = "$percentage%"
        }

        textProgress.text = logLines.joinToString("\n")
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        lastProgressUpdate = now
    }

    private fun appendLog(message: String, phase: String) {
        logLines.add(message)  // Just add the message exactly as it comes

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

                if (!config.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) totalSteps += 3
                if (!config.disableEPGFiltering && !epgPath.isNullOrEmpty()) totalSteps += 3

                if (totalSteps == 0) {
                    runOnUiThread {
                        textStatus.text = "Nothing to sync"
                        appendLog("⚠\uFE0F No files selected or filtering disabled", "Error")
                        buttonStart.isEnabled = false
                        buttonStart.text = "Nothing to Filter"
                    }
                    return@Thread
                }

                if (!config.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) {
                    processM3UFile(playlistPath!!, currentStep, totalSteps)
                    currentStep += 3
                }

                if (!config.disableEPGFiltering && !epgPath.isNullOrEmpty()) {
                    processEPGFile(epgPath!!, currentStep, totalSteps)
                    currentStep += 3
                }

                runOnUiThread {
                    textStatus.text = "Ready to filter"
                    buttonStart.isEnabled = true
                    buttonStart.text = "Start Filtering"
                    buttonPause.isEnabled = false

                    appendLog("❇\uFE0F All files are current", "SyncDone")
                    logLines.add("") // One blank line after "All files are current"
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
                // ✅ Clear all previous log lines
                logLines.clear()
                m3uStartLineIndex = -1
                m3uLineIndex = -1
                channelsLineIndex = -1
                programmesLineIndex = -1
                textProgress.text = ""

                // ✅ Add fresh header
                logLines.add("  ✨ FILTERING STARTED ✨")
                logLines.add("────────────────────────")
                logLines.add("") // Blank line after header
                textProgress.text = logLines.joinToString("\n")

                // Start service
                val serviceIntent = Intent(this, EpgProcessorService::class.java)
                serviceIntent.action = EpgProcessorService.ACTION_START_EPG_PROCESSING
                startService(serviceIntent)

                buttonStart.isEnabled = false
                buttonPause.isEnabled = true
            }
        }

        buttonPause.setOnClickListener {
            // To be implemented
        }

        buttonCancel.setOnClickListener {
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