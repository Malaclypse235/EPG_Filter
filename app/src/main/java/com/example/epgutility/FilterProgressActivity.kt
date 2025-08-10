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
                        EpgProcessorService.MSG_PROGRESS_M3U -> {
                            replaceLastLineWithMarker("PROGRESS_M3U:", message)
                            updateProgress(percentage, message)
                        }
                        EpgProcessorService.MSG_PROGRESS_CHANNELS -> {
                            replaceLastLineWithMarker("PROGRESS_CHANNELS:", message)
                            updateProgress(percentage, message)
                        }
                        EpgProcessorService.MSG_PROGRESS_PROGRAMMES -> {
                            replaceLastLineWithMarker("PROGRESS_PROGRAMMES:", message)
                            updateProgress(percentage, message)
                        }
                        EpgProcessorService.MSG_LOG -> {
                            appendLog(message, phase)
                        }
                        else -> {
                            updateProgress(percentage, message)
                        }
                    }

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

        // Register broadcast receiver
        registerReceiver(epgProgressReceiver, IntentFilter("EPG_PROGRESS_UPDATE"), RECEIVER_EXPORTED)

        // Initial UI state
        buttonStart.isEnabled = false
        buttonStart.text = "Loading..."
        buttonPause.isEnabled = false
        buttonCancel.isEnabled = true
        textProgress.text = "📌 Initializing...\n"

        // Start file sync immediately
        startFileSync()
    }

    override fun onDestroy() {
        unregisterReceiver(epgProgressReceiver)
        super.onDestroy()
    }

    private fun updateProgress(percentage: Int, status: String) {
        textStatus.text = status
        if (percentage in 0..100) {
            progressBar.progress = percentage
            textPercent.text = "$percentage%"
        }
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun appendLog(message: String, phase: String = "") {
        // Skip adding emoji if message already starts with one
        val hasEmoji = message.length >= 2 && listOf("📁", "📋", "✅", "❌", "🔍", "📊", "🎬", "📺", "📡", "🎉", "🔹")
            .any { message.startsWith(it) }

        val prefix = if (hasEmoji) "" else {
            when {
                message.contains("Checking") -> "📁"
                message.contains("updating", ignoreCase = true) -> "📋"
                message.contains("validated", ignoreCase = true) -> "✅"
                message.contains("not found", ignoreCase = true) ||
                        phase == "Error" -> "❌"
                message.contains("Starting M3U", ignoreCase = true) -> "🔍"
                message.contains("M3U filtering complete") -> "✅"
                message.contains("Analyzing EPG") -> "📊"
                message.contains("EPG filtering complete") -> "✅"
                message.contains("All processing complete") -> "🎉"
                else -> "🔹"
            }
        }

        val separator = if (
            message.startsWith("✅") ||
            message.startsWith("🎉") ||
            message.contains("All processing complete") ||
            phase == "Complete"
        ) "\n\n" else "\n"

        val finalMessage = if (prefix.isEmpty()) {
            "$separator$message"
        } else {
            "$separator$prefix $message"
        }

        textProgress.append(finalMessage)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun replaceLastLineWithMarker(marker: String, displayText: String) {
        val lines = textProgress.text.toString().split("\n").toMutableList()
        val fullLine = "$marker$displayText"
        val index = lines.indexOfLast { it.startsWith(marker) }

        if (index != -1) {
            lines[index] = fullLine
        } else {
            lines.add(fullLine)
        }

        // Rebuild text: show only the actual message, not the marker
        textProgress.text = lines.joinToString("\n") { line ->
            if (line.startsWith("PROGRESS_")) {
                line.substringAfter(":", "").trim()
            } else {
                line
            }
        }

        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
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
                        appendLog("⚠️ No files selected or filtering disabled")
                        buttonStart.isEnabled = false
                        buttonStart.text = "Nothing to Filter"
                    }
                    return@Thread
                }

                // Sync M3U
                if (!config.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) {
                    processM3UFile(playlistPath!!, currentStep, totalSteps)
                    currentStep += 3
                }

                // Sync EPG
                if (!config.disableEPGFiltering && !epgPath.isNullOrEmpty()) {
                    processEPGFile(epgPath!!, currentStep, totalSteps)
                    currentStep += 3
                }

                // Finalize
                runOnUiThread {
                    textStatus.text = "Ready to filter"
                    buttonStart.isEnabled = true
                    buttonStart.text = "Start Filtering"
                    buttonPause.isEnabled = false
                    appendLog("\n✅ All files are current")
                    appendLog("➡️ Press 'Start Filtering' to begin")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                runOnUiThread {
                    textStatus.text = "Sync error"
                    appendLog("❌ Sync failed: ${e.message}", "Error")
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
                appendLog("Checking for updates to: $filename")
            }
            Thread.sleep(200)

            if (!file.exists()) {
                runOnUiThread {
                    appendLog("❌ M3U file not found: $path", "Error")
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
                    appendLog("Updating file: $filename")
                }
                Thread.sleep(200)

                if (existing != null && existing.name != filename) {
                    existing.delete()
                }

                val dest = File(inputDir, filename)
                file.copyTo(dest, overwrite = true)

                runOnUiThread {
                    appendLog("✅ $filename updated")
                }
            } else {
                runOnUiThread {
                    textStatus.text = "M3U up to date: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    appendLog("$filename does not need updating")
                }
                Thread.sleep(200)
            }

            currentStep++
            runOnUiThread {
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                appendLog("$filename validated")
            }
        } catch (e: Exception) {
            runOnUiThread {
                appendLog("❌ Failed to sync M3U: ${e.message}", "Error")
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
                appendLog("Checking for updates to: $filename")
            }
            Thread.sleep(200)

            if (!file.exists()) {
                runOnUiThread {
                    appendLog("❌ EPG file not found: $path", "Error")
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
                    appendLog("Updating file: $filename")
                }
                Thread.sleep(200)

                if (existing != null && existing.name != filename) {
                    existing.delete()
                }

                val dest = File(inputDir, filename)
                file.copyTo(dest, overwrite = true)

                runOnUiThread {
                    appendLog("✅ $filename updated")
                }
            } else {
                runOnUiThread {
                    textStatus.text = "EPG up to date: $filename"
                    val progress = (currentStep * 100 / totalSteps)
                    progressBar.progress = progress
                    appendLog("$filename does not need updating")
                }
                Thread.sleep(200)
            }

            currentStep++
            runOnUiThread {
                val progress = (currentStep * 100 / totalSteps)
                progressBar.progress = progress
                appendLog("$filename validated")
            }
        } catch (e: Exception) {
            runOnUiThread {
                appendLog("❌ Failed to sync EPG: ${e.message}", "Error")
            }
        }
    }

    private fun setupButtonListeners() {
        buttonStart.setOnClickListener {
            if (buttonStart.text == "Start Filtering") {
                appendLog("\n🔄 FILTERING STARTED")
                appendLog("────────────────────────")
                val serviceIntent = Intent(this, EpgProcessorService::class.java)
                serviceIntent.action = EpgProcessorService.ACTION_START_EPG_PROCESSING
                startService(serviceIntent)

                buttonStart.isEnabled = false
                buttonPause.isEnabled = true
            }
        }

        buttonPause.setOnClickListener {
            // Pause/resume can be added later
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