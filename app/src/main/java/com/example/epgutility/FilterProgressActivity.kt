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
    private var m3uLineIndex = -1
    private var channelsLineIndex = -1
    private var programmesLineIndex = -1

    private lateinit var config: ConfigManager.ConfigData
    private var playlistPath: String? = null
    private var epgPath: String? = null

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
                        EpgProcessorService.MSG_PROGRESS_M3U -> updateOrAdd("📡 M3U: $message", ::m3uLineIndex, ::m3uLineIndex::set)
                        EpgProcessorService.MSG_PROGRESS_CHANNELS -> updateOrAdd("📺 Channels: $message", ::channelsLineIndex, ::channelsLineIndex::set)
                        EpgProcessorService.MSG_PROGRESS_PROGRAMMES -> updateOrAdd("🎬 Programmes: $message", ::programmesLineIndex, ::programmesLineIndex::set)
                        EpgProcessorService.MSG_LOG -> appendLine("🔹 $message")
                        else -> {
                            textStatus.text = message
                            if (percentage in 0..100) {
                                progressBar.progress = percentage
                                textPercent.text = "$percentage%"
                            }
                        }
                    }

                    // Only one setText call per update
                    textProgress.text = logLines.joinToString("\n")

                    // Auto-scroll (post delayed to ensure layout)
                    scrollView.postDelayed({
                        scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                    }, 50)

                    // Final state
                    if (phase == "Complete" || phase.contains("Error")) {
                        buttonStart.isEnabled = true
                        buttonStart.text = "Start Filtering"
                        buttonPause.isEnabled = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "UI update failed", e)
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

        Log.d(TAG, "Received playlistPath: $playlistPath")
        Log.d(TAG, "Received epgPath: $epgPath")

        // Register receiver
        registerReceiver(epgProgressReceiver, IntentFilter("EPG_PROGRESS_UPDATE"), Context.RECEIVER_EXPORTED)

        // Initial state
        buttonStart.isEnabled = false
        buttonStart.text = "Loading..."
        buttonPause.isEnabled = false
        buttonCancel.isEnabled = true

        // Start file sync
        startFileSync()
    }

    override fun onDestroy() {
        unregisterReceiver(epgProgressReceiver)
        super.onDestroy()
    }

    private fun updateOrAdd(line: String, indexRef: () -> Int, setIndex: (Int) -> Unit) {
        if (indexRef() == -1) {
            logLines.add(line)
            setIndex(logLines.lastIndex)
        } else {
            logLines[indexRef()] = line
        }
        textStatus.text = line  // Always show latest progress
    }

    private fun appendLine(line: String) {
        logLines.add(line)
    }

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
                        appendLine("⚠️ No files selected or filtering disabled")
                        textProgress.text = logLines.joinToString("\n")
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
                    appendLine("")
                    appendLine("✅ All files are current")
                    appendLine("➡️ Press 'Start Filtering' to begin")
                    textProgress.text = logLines.joinToString("\n")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                runOnUiThread {
                    textStatus.text = "Sync error"
                    appendLine("❌ Sync failed: ${e.message}")
                    textProgress.text = logLines.joinToString("\n")
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
                appendLine("📁 Checking for updates to: $filename")
                textProgress.text = logLines.joinToString("\n")
            }
            Thread.sleep(200)

            if (!file.exists()) {
                runOnUiThread {
                    appendLine("❌ M3U file not found: $path")
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
                    appendLine("📋 Updating file: $filename")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)

                if (existing != null && existing.name != filename) {
                    existing.delete()
                }

                val dest = File(inputDir, filename)
                file.copyTo(dest, overwrite = true)

                runOnUiThread {
                    appendLine("✅ $filename updated")
                    textProgress.text = logLines.joinToString("\n")
                }
            } else {
                runOnUiThread {
                    textStatus.text = "M3U up to date: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    appendLine("$filename does not need updating")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)
            }

            currentStep++
            runOnUiThread {
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                appendLine("✅ $filename validated")
                textProgress.text = logLines.joinToString("\n")
            }
        } catch (e: Exception) {
            runOnUiThread {
                appendLine("❌ Failed to sync M3U: ${e.message}")
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
                appendLine("📁 Checking for updates to: $filename")
                textProgress.text = logLines.joinToString("\n")
            }
            Thread.sleep(200)

            if (!file.exists()) {
                runOnUiThread {
                    appendLine("❌ EPG file not found: $path")
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
                    appendLine("📋 Updating file: $filename")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)

                if (existing != null && existing.name != filename) {
                    existing.delete()
                }

                val dest = File(inputDir, filename)
                file.copyTo(dest, overwrite = true)

                runOnUiThread {
                    appendLine("✅ $filename updated")
                    textProgress.text = logLines.joinToString("\n")
                }
            } else {
                runOnUiThread {
                    textStatus.text = "EPG up to date: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    appendLine("$filename does not need updating")
                    textProgress.text = logLines.joinToString("\n")
                }
                Thread.sleep(200)
            }

            currentStep++
            runOnUiThread {
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                appendLine("✅ $filename validated")
                textProgress.text = logLines.joinToString("\n")
            }
        } catch (e: Exception) {
            runOnUiThread {
                appendLine("❌ Failed to sync EPG: ${e.message}")
                textProgress.text = logLines.joinToString("\n")
            }
        }
    }

    private fun setupButtonListeners() {
        buttonStart.setOnClickListener {
            if (buttonStart.text == "Start Filtering") {
                appendLine("")
                appendLine("🔄 FILTERING STARTED")
                appendLine("────────────────────────")
                textProgress.text = logLines.joinToString("\n")

                val serviceIntent = Intent(this, EpgProcessorService::class.java)
                serviceIntent.action = EpgProcessorService.ACTION_START_EPG_PROCESSING
                startService(serviceIntent)

                buttonStart.isEnabled = false
                buttonPause.isEnabled = true
            }
        }

        buttonPause.setOnClickListener {
            // Pause/resume later
        }

        buttonCancel.setOnClickListener {
            finish()
        }
    }

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