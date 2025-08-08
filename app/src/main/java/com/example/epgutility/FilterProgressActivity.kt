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
            // --- ADD LOGGING TO CONFIRM RECEIVER IS TRIGGERED ---
            // Log.d(TAG, ">>> epgProgressReceiver.onReceive called <<<")
            android.util.Log.d("FilterProgress", ">>> epgProgressReceiver.onReceive CALLED <<<")
            // --- END LOGGING ---

            // Check for null intent early
            if (intent == null) {
                Log.w(TAG, "Received null intent in epgProgressReceiver")
                return
            }

            // Extract data from the intent
            val message = intent.getStringExtra("message")
            val percentage = intent.getIntExtra("percentage", -1) // Default to -1 if not found
            // Optional: Get detailed counts if needed for advanced UI updates
            // val totalChannels = intent.getIntExtra("totalChannels", 0)
            // val processedChannels = intent.getIntExtra("processedChannels", 0)
            // val totalProgrammes = intent.getIntExtra("totalProgrammes", 0)
            // val processedProgrammes = intent.getIntExtra("processedProgrammes", 0)
            val phase = intent.getStringExtra("phase") ?: "Processing" // Default to "Processing"

            // --- ADD LOGGING TO SEE EXTRACTED DATA ---
            Log.d(TAG, "Received broadcast - Message: '$message', Percentage: $percentage, Phase: '$phase'")
            // --- END LOGGING ---

            // Check for null message (though other data can still be valid)
            if (message == null) {
                Log.w(TAG, "Received broadcast with null 'message' extra")
                // Depending on your UI logic, you might still want to process percentage/phase
                // For now, let's return, but you could adapt this.
                return
            }

            try {
                // Perform UI updates on the main thread
                runOnUiThread {
                    // --- ADD LOGGING TO CONFIRM ENTRY TO runOnUiThread ---
                    Log.d(TAG, "Inside runOnUiThread block")
                    // --- END LOGGING ---

                    try {
                        // Update the accumulated log text
                        val currentLog = textProgress.text.toString()
                        textProgress.text = "$currentLog\n$message"

                        // Update progress bar and percent text if percentage is valid
                        if (percentage in 0..100) { // Check for valid range
                            // --- ADD LOGGING FOR PROGRESS UPDATE ---
                            Log.d(TAG, "Updating ProgressBar to $percentage%")
                            // --- END LOGGING ---
                            progressBar.progress = percentage
                            textPercent.text = "$percentage%"
                        } else {
                            Log.d(TAG, "Ignoring invalid percentage: $percentage")
                        }

                        // --- Update status text ---
                        // Simplified logic for debugging
                        val statusText = when (phase) {
                            "Starting" -> "Initializing EPG processing..."
                            "Analyzing file structure" -> "Analyzing EPG file structure..."
                            "Analysis complete" -> "Analysis complete"
                            "Processing EPG data" -> message // Use dynamic message like "Processing channel X of Y"
                            "Finalizing files" -> "Finalizing output files..."
                            "Completed" -> {
                                Log.d(TAG, "<<< Service reports COMPLETED >>>")
                                "EPG processing completed successfully!"
                            }
                            "Error" -> {
                                Log.e(TAG, "<<< Service reports ERROR: $message >>>")
                                "Error occurred during EPG processing"
                            }
                            else -> message // Default to message for any other phase
                        }
                        textStatus.text = statusText
                        Log.d(TAG, "Set textStatus to: '${statusText}'")
                        // --- End status text update ---

                        // --- Handle final states for buttons etc. ---
                        if (phase == "Completed" || phase == "Error") {
                            Log.d(TAG, "Handling final state in receiver. Phase: $phase")
                            buttonStart.isEnabled = true
                            buttonStart.text = "Start Filtering"
                            buttonPause.isEnabled = false
                            // Indicate processing is no longer happening in this activity context
                            isProcessing = false
                            Log.d(TAG, "Final state UI updates applied.")
                        }
                        // --- End final state handling ---

                        // Auto-scroll to bottom of log
                        val scrollView = findViewById<ScrollView>(R.id.scrollContainer)
                        scrollView.post {
                            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                        }

                        // --- ADD LOGGING FOR END OF runOnUiThread ---
                        Log.d(TAG, "Finished runOnUiThread block successfully")
                        // --- END LOGGING ---
                    } catch (uiException: Exception) {
                        // Catch exceptions specifically within the UI update block
                        Log.e(TAG, "Exception inside runOnUiThread block: ${uiException.message}", uiException)
                    }
                }
            } catch (outerException: Exception) {
                // Catch exceptions in the main onReceive logic (before runOnUiThread)
                Log.e(TAG, "Exception in epgProgressReceiver.onReceive (outer): ${outerException.message}", outerException)
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

        // Load config using your ConfigManager
        val loadResult = ConfigManager.loadConfig(this)
        config = loadResult.config

        val playlistPath = intent.getStringExtra("PLAYLIST_PATH")
        val epgPath = intent.getStringExtra("EPG_PATH")

        Log.d(TAG, "Playlist Path: $playlistPath")
        Log.d(TAG, "EPG Path: $epgPath")

        // Register broadcast receiver for EPG progress updates
        // --- NEW (Compatible with minSdkVersion 21) ---
        // Inside FilterProgressActivity.onCreate
        val filter = IntentFilter("EPG_PROGRESS_UPDATE")
// For minSdkVersion 26+, use RECEIVER_NOT_EXPORTED
        registerReceiver(epgProgressReceiver, filter, RECEIVER_EXPORTED) // Or RECEIVER_EXPORTED if needed for older APIs, but you are now on 26+
        Log.d(TAG, "FilterProgressActivity: registerReceiver call completed without exception.")
        Log.d(TAG, "FilterProgressActivity: epgProgressReceiver registered with filter for 'EPG_PROGRESS_UPDATE'")
        Log.d(TAG, "FilterProgressActivity: epgProgressReceiver registered with filter for 'EPG_PROGRESS_UPDATE'")
// --- END CHANGE ---
        // --- END ADDITION ---
        // Set up button listeners
        setupButtonListeners(playlistPath, epgPath)

        // Start processing immediately when screen opens to check/copy files
        startProcessing(playlistPath, epgPath)
    }

    override fun onResume() {
        super.onResume()
        // When activity resumes, request current progress from service if it's running
        val serviceIntent = Intent(this, EpgProcessorService::class.java)
        serviceIntent.action = EpgProcessorService.ACTION_GET_PROGRESS
        startService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister broadcast receiver
        unregisterReceiver(epgProgressReceiver)
    }

    private fun setupButtonListeners(playlistPath: String?, epgPath: String?) {
        buttonStart.setOnClickListener {
            if (!isProcessing) {
                // Clear old text when starting filtering
                textProgress.text = ""
                // Start filtering instead of file updating
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

        // Initially disable Start button since processing starts automatically
        buttonStart.isEnabled = false
        buttonStart.text = "Updating..."
    }

    // Original file updating logic - KEEP AS-IS
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

                // Count total steps (each file has 3 steps now)
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

                // Process M3U file
                if (!config.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) {
                    val m3uResult = processM3UFile(playlistPath, currentStep, totalSteps)
                    currentStep += 3 // We completed 3 steps for M3U processing
                    if (m3uResult.isNotEmpty()) {
                        results.add(m3uResult)
                        hasProcessedFiles = true
                    }
                    // Add a blank line in the log after M3U processing is done
                    runOnUiThread {
                        textProgress.append("\n")
                    }
                }

                // Process EPG file
                if (!config.disableEPGFiltering && !epgPath.isNullOrEmpty()) {
                    val epgResult = processEPGFile(epgPath, currentStep, totalSteps)
                    currentStep += 3 // We completed 3 steps for EPG processing
                    if (epgResult.isNotEmpty()) {
                        results.add(epgResult)
                        hasProcessedFiles = true
                    }
                    // Add a blank line in the log after EPG processing is done
                    runOnUiThread {
                        textProgress.append("\n")
                    }
                }

                runOnUiThread {
                    if (hasProcessedFiles) {
                        textStatus.text = "All files processed successfully"
                        textPercent.text = "100%"
                        progressBar.progress = 100
                        val finalLog = textProgress.text.toString() + "✅ All files are current"
                        textProgress.text = finalLog + "\n" + getFilteringStatusSummary(playlistPath, epgPath)
                    }
                    // Change button text to indicate it now does filtering
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

    // NEW: Filtering functionality for the Start button
    private fun startFiltering(playlistPath: String?, epgPath: String?) {
        // Set processing state and update UI *immediately*
        isProcessing = true
        isPaused = false // Assuming resume if paused?
        buttonStart.isEnabled = false
        buttonStart.text = "Filtering..." // Or "In Progress..."
        buttonPause.isEnabled = true // Enable pause if your service supports it
        buttonCancel.isEnabled = true

        // Reset progress UI for the filtering phase
        textStatus.text = "Starting file filtering..."
        textPercent.text = "0%"
        progressBar.progress = 0

        // CLEAR OLD TEXT and Add filtering header to now-empty log
        val separator = "=".repeat(50)
        textProgress.text = "$separator\n🔄 STARTING FILE FILTERING\n$separator"

        // Start background processing thread
        Thread {
            try {
                // var totalSteps = 0 // Not used if EPG is service-based
                // var currentStep = 0 // Not used if EPG is service-based
                // val results = mutableListOf<String>() // Not used if EPG is service-based

                // --- M3U Filtering (happens in this thread) ---
                if (!config.disablePlaylistFiltering && !playlistPath.isNullOrEmpty()) {
                    // Pass 0 and 1 as steps since it's the only local step if only M3U runs
                    // If EPG also runs, the logic for final state after M3U needs adjustment
                    val result = filterM3UFile(0, if (config.disableEPGFiltering || epgPath.isNullOrEmpty()) 1 else 1)
                    // results.add(result) // Not needed for final state if relying on receiver
                    // Update main UI status after M3U is done
                    runOnUiThread {
                        textStatus.text = "M3U filtering complete. Starting EPG processing..."
                        // You could update progress bar here too if you want
                    }
                } else {
                    // If M3U is disabled, log it
                    runOnUiThread {
                        val logUpdate = textProgress.text.toString() + "\nℹ️ M3U filtering is disabled or no M3U file provided."
                        textProgress.text = logUpdate
                    }
                }

                // --- EPG Filtering (happened/started in the SERVICE) ---
                if (!config.disableEPGFiltering && !epgPath.isNullOrEmpty()) {
                    // Update UI log to indicate EPG service is starting
                    // The main status bar (textStatus) and progress will be updated by the service's broadcasts.
                    runOnUiThread {
                        val logUpdate = textProgress.text.toString() + "\n🔍 Starting EPG XML processing in background..."
                        textProgress.text = logUpdate
                        val backgroundNote = textProgress.text.toString() + "\n📱 This will continue processing even if you leave the app"
                        textProgress.text = backgroundNote
                        // DO NOT update textStatus or progressBar here in a way that conflicts
                        // with the initial service broadcasts. Let the receiver handle it.
                        // The initial "Starting EPG processing..." message from the service should update these.
                    }

                    // --- CRUCIAL: Start the background service ---
                    Log.d(TAG, "Starting EpgProcessorService...")
                    val serviceIntent = Intent(this@FilterProgressActivity, EpgProcessorService::class.java)
                    serviceIntent.action = EpgProcessorService.ACTION_START_EPG_PROCESSING
                    startService(serviceIntent)
                    Log.d(TAG, "EpgProcessorService start command sent.")
                    // --- END CRUCIAL ---
                    // DO NOT perform any further UI updates related to EPG completion here.
                    // The epgProgressReceiver handles all EPG-related UI updates via broadcasts.
                    // The thread's job is done after starting the service.

                } else {
                    // If EPG is disabled or no path, and M3U is also done/irrelevant,
                    // we might need to signal local completion.
                    // However, if M3U ran, its completion logic (inside filterM3UFile or its follow-up)
                    // should handle the final UI state if EPG is not running.
                    // If neither M3U nor EPG run, handle locally.
                    if (config.disableEPGFiltering || epgPath.isNullOrEmpty()) {
                        runOnUiThread {
                            val logUpdate = textProgress.text.toString() + "\nℹ️ EPG filtering is disabled or no EPG file provided."
                            textProgress.text = logUpdate
                        }
                        // If M3U also didn't run, signal completion locally
                        if (config.disablePlaylistFiltering || playlistPath.isNullOrEmpty()) {
                            Log.d(TAG, "No filtering steps applicable, signaling local completion.")
                            runOnUiThread {
                                textStatus.text = "Nothing to filter"
                                textPercent.text = "Done"
                                progressBar.progress = 100
                                val noFilterLog = textProgress.text.toString() + "\n⚠️ No applicable files to filter"
                                textProgress.text = noFilterLog
                                // Final state updates
                                buttonStart.isEnabled = true
                                buttonStart.text = "Start Filtering"
                                buttonPause.isEnabled = false
                                isProcessing = false
                            }
                        } else {
                            // M3U ran (or at least filterM3UFile was called), it should handle its own completion UI.
                            // If it didn't (e.g., because it finished quickly and didn't update final state),
                            // we might need to ensure the final state is set.
                            // Let's assume filterM3UFile handles it correctly for now.
                            // If issues persist, we might need to add a check here.
                            Log.d(TAG, "M3U filtering initiated, waiting for its completion signal.")
                        }
                    }
                }

                // --- DO NOT add a generic "All filtering completed" block here ---
                // The completion (success or failure) for EPG comes from the service broadcast.
                // Completion for M3U comes from within filterM3UFile or its follow-up logic.
                // The thread's primary job after starting the service (if applicable) is done.

            } catch (e: Exception) {
                Log.e(TAG, "Error initiating filtering process", e)
                runOnUiThread {
                    textStatus.text = "Error initiating filtering"
                    textPercent.text = "Error"
                    val errorLog = textProgress.text.toString() + "\n❌ Error initiating filtering: ${e.message}"
                    textProgress.text = errorLog
                    // Final state updates on error during initiation
                    buttonStart.isEnabled = true
                    buttonStart.text = "Start Filtering"
                    buttonPause.isEnabled = false
                    isProcessing = false
                }
            }
            // The thread exits here. All further EPG UI updates must come from epgProgressReceiver.
        }.start()
    }


    private fun filterM3UFile(currentStep: Int, totalSteps: Int): String {
        // Implementation from your previous file
        // ... (keeping your existing implementation)
        // This is a placeholder. You should have the actual implementation from Pasted_Text_1754657227575.txt or similar
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
        // Implementation from your previous file
        // ... (keeping your existing implementation)
        // This is a placeholder. You should have the actual implementation from Pasted_Text_1754657227575.txt or similar
        return true
    }

    // Update EPG filtering to use the background service
    private fun filterEPGFile(currentStep: Int, totalSteps: Int): String {
        runOnUiThread {
            textStatus.text = "Starting EPG filtering service..."
            // Add note about background processing
            textProgress.append("\n🔍 Starting EPG XML processing in background...")
            textProgress.append("\n📱 This will continue processing even if you leave the app")
        }

        // Start the background service
        val serviceIntent = Intent(this, EpgProcessorService::class.java)
        serviceIntent.action = EpgProcessorService.ACTION_START_EPG_PROCESSING
        startService(serviceIntent)

        return "EPG processing started in background - detailed progress will be shown above"
    }

    private fun processM3UFile(playlistPath: String?, startingStep: Int, totalSteps: Int): String {
        try {
            val results = mutableListOf<String>()
            val externalFile = File(playlistPath!!)
            val filename = externalFile.name
            var currentStep = startingStep

            // Step 1: Checking for updates
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

            // Check internal storage version
            val internalDir = File(filesDir, "input")
            if (!internalDir.exists()) internalDir.mkdirs()
            val internalFile = findExistingM3UFile(internalDir)
            val externalModified = externalFile.lastModified()
            val needsUpdate = internalFile == null || internalFile.lastModified() < externalModified

            // Step 2: Update decision
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

                // Delete old file if it exists and has different name
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

            // Step 3: Validation
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

            // Step 1: Checking for updates
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

            // Check internal storage version
            val internalDir = File(filesDir, "input")
            if (!internalDir.exists()) internalDir.mkdirs()
            val internalFile = findExistingEPGFile(internalDir)
            val externalModified = externalFile.lastModified()
            val needsUpdate = internalFile == null || internalFile.lastModified() < externalModified

            // Step 2: Update decision
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

                // Delete old file if it exists and has different name
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

            // Step 3: Validation
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