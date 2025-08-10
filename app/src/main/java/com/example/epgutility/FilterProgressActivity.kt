package com.example.epgutility

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
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
    private var m3uLineIndex = -1
    private var channelsLineIndex = -1
    private var programmesLineIndex = -1

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
                        EpgProcessorService.MSG_PROGRESS_PROGRAMMES -> {
                            updateProgressLine(
                                "${Emojis.PROGRAMMES} Programmes: $message",
                                ::programmesLineIndex,
                                ::programmesLineIndex::set,
                                percentage
                            )
                        }
                        EpgProcessorService.MSG_LOG -> {
                            appendLog(message, phase)
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
        val prefix = when {
            message.contains("All processing complete", ignoreCase = true) -> Emojis.DONE
            message.contains("M3U filtering complete", ignoreCase = true) -> Emojis.DONE
            message.contains("EPG filtering complete", ignoreCase = true) -> Emojis.DONE
            message.contains("All files are current", ignoreCase = true) -> Emojis.DONE
            else -> Emojis.INFO
        }

        logLines.add("$prefix $message")

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
                        appendLog("No files selected or filtering disabled", "Error")
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

                    appendLog("All files are current", "SyncDone")
                    logLines.add("") // ✅ One blank line AFTER "All files are current"
                    appendLog("Press 'Start Filtering' to begin", "Info")
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
                logLines.add("Checking for updates to: $filename")
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
                    logLines.add("Updating file: $filename")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)

                if (existing != null && existing.name != filename) {
                    existing.delete()
                }

                val dest = File(inputDir, filename)
                file.copyTo(dest, overwrite = true)

                runOnUiThread {
                    logLines.add("✅ $filename updated")
                    textProgress.text = logLines.joinToString("\n")
                }
            } else {
                runOnUiThread {
                    textStatus.text = "M3U up to date: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    logLines.add("$filename does not need updating")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)
            }

            currentStep++
            runOnUiThread {
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                logLines.add("✅ $filename validated")
                logLines.add("") // ← Add blank line after validation
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
                logLines.add("Checking for updates to: $filename")
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
                    logLines.add("Updating file: $filename")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)

                if (existing != null && existing.name != filename) {
                    existing.delete()
                }

                val dest = File(inputDir, filename)
                file.copyTo(dest, overwrite = true)

                runOnUiThread {
                    logLines.add("✅ $filename updated")
                    textProgress.text = logLines.joinToString("\n")
                }
            } else {
                runOnUiThread {
                    textStatus.text = "EPG up to date: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    logLines.add("$filename does not need updating")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)
            }

            currentStep++
            runOnUiThread {
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                logLines.add("✅ $filename validated")
                logLines.add("") // ← Add blank line after validation
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
                m3uLineIndex = -1
                channelsLineIndex = -1
                programmesLineIndex = -1
                textProgress.text = ""

                // ✅ Add fresh header
                logLines.add("${Emojis.START} FILTERING STARTED")
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
        return directory.listFiles()?.find {
            it.name.endsWith(".m3u", true) || it.name.endsWith(".m3u8", true)
        }
    }

    private fun findExistingEPGFile(directory: File): File? {
        return directory.listFiles()?.find {
            it.name.endsWith(".xml", true) || it.name.endsWith(".xml.gz", true)
        }
    }

    override fun onResume() {
        super.onResume()
        setupButtonListeners()
    }
}