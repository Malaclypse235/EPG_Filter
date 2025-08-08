package com.example.epgutility

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import android.content.pm.PackageManager
import java.util.concurrent.TimeUnit
import com.example.epgutility.utils.AnimationManager
import android.widget.CheckBox


class MainActivity : AppCompatActivity() {

    private lateinit var buttonPlaylist: Button
    private lateinit var buttonEPG: Button
    private lateinit var playlistFileText: TextView
    private lateinit var epgFileText: TextView
    private lateinit var buttonFilters: Button
    private lateinit var buttonRunFilter: Button
    private lateinit var titleText: TextView
    private lateinit var checkBoxDisablePlaylist: CheckBox
    private lateinit var checkBoxDisableEPG: CheckBox

    // Config object
    private lateinit var config: ConfigManager.ConfigData

    companion object {
        private const val PICK_PLAYLIST_FILE = 1
        private const val PICK_EPG_FILE = 2
        private const val PERMISSION_REQUEST_STORAGE = 100
        private val FILE_MAX_AGE_MS = TimeUnit.DAYS.toMillis(8)
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        // Ask for MANAGE_EXTERNAL_STORAGE on Android 11+ (emulator-safe)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    Log.w("MainActivity", "MANAGE_ALL_FILES_ACCESS_PERMISSION not supported on this device/emulator")
                }
            }
        }


        // Crash log handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val downloadsDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()

                val crashFile = File(downloadsDir, "epg_utility_last_crash.txt")
                crashFile.writeText("Thread: ${thread.name}\n${throwable.stackTraceToString()}")

                runOnUiThread {
                    Toast.makeText(
                        this,
                        "App crashed! Check Downloads/epg_utility_last_crash.txt",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- Ensure required subfolders exist ---
        ensureRequiredSubfolders()

        // --- Delete old files (older than 8 days) ---
        deleteOldFiles()

        // --- Load Config ---
        val loadResult = ConfigManager.loadConfig(this)
        config = loadResult.config

        // --- Decide permanent working folder ---
        val documentsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val malEpgDir: File = if (documentsDir.exists() || documentsDir.mkdirs()) {
            File(documentsDir, "MalEPG")
        } else {
            File(downloadsDir, "MalEPG")
        }

        if (!malEpgDir.exists()) malEpgDir.mkdirs()

        if (config.defaultFolderPath.isNullOrEmpty()) {
            config.defaultFolderPath = malEpgDir.absolutePath
            ConfigManager.saveConfig(this, config)
        }

        // --- Find views ---
        titleText = findViewById(R.id.titleText)
        buttonPlaylist = findViewById(R.id.buttonPlaylist)
        buttonEPG = findViewById(R.id.buttonEPG)
        playlistFileText = findViewById(R.id.playlistFileText)
        epgFileText = findViewById(R.id.epgFileText)
        buttonFilters = findViewById(R.id.buttonFilters)
        buttonRunFilter = findViewById(R.id.buttonRunFilter)
        checkBoxDisablePlaylist = findViewById(R.id.checkboxDisablePlaylistFilter)  // Use actual ID from your XML
        checkBoxDisableEPG = findViewById(R.id.checkboxDisableEpgFilter)

        // Equalize button sizes
        buttonPlaylist.post {
            val width = buttonPlaylist.width
            val height = buttonPlaylist.height
            buttonEPG.layoutParams.width = width
            buttonEPG.layoutParams.height = height
            buttonEPG.requestLayout()
        }

        // --- Title Flicker Animations --- Do Not Change or Delete

        AnimationManager.startGlowFlicker(titleText)
        AnimationManager.startTextFlicker(titleText)



        // --- Update displayed file names ---
        playlistFileText.text = getDisplayName(config.playlistPath, config.playlistUri)
        epgFileText.text = getDisplayName(config.epgPath, config.epgUri)
        checkBoxDisablePlaylist.isChecked = config.disablePlaylistFiltering
        checkBoxDisableEPG.isChecked = config.disableEPGFiltering

        // Add checkbox listeners
        checkBoxDisablePlaylist.setOnCheckedChangeListener { _, isChecked ->
            config.disablePlaylistFiltering = isChecked
            ConfigManager.saveConfig(this, config)
            Log.d("MainActivity", "Playlist filtering disabled: $isChecked")
        }

        checkBoxDisableEPG.setOnCheckedChangeListener { _, isChecked ->
            config.disableEPGFiltering = isChecked
            ConfigManager.saveConfig(this, config)
            Log.d("MainActivity", "EPG filtering disabled: $isChecked")
        }

        // --- Button Listeners ---
        buttonPlaylist.setOnClickListener { openFilePicker(PICK_PLAYLIST_FILE) }
        buttonEPG.setOnClickListener { openFilePicker(PICK_EPG_FILE) }

        buttonFilters.setOnClickListener {
            startActivity(Intent(this, FiltersActivity::class.java))
        }

        // Run filters screen
        buttonRunFilter.setOnClickListener {
            val loadResult = ConfigManager.loadConfig(this)
            val config = loadResult.config

            val intent = Intent(this, FilterProgressActivity::class.java).apply {
                putExtra("PLAYLIST_PATH", config.playlistPath)
                putExtra("EPG_PATH", config.epgPath)
            }
            startActivity(intent)
        }

        // Reset button
        val buttonResetFiles: Button = findViewById(R.id.buttonResetFiles)
        buttonResetFiles.setOnClickListener {
            config.playlistPath = null
            config.playlistUri = null
            config.epgPath = null
            config.epgUri = null
            ConfigManager.clearConfig(this)

            playlistFileText.text = "No file selected"
            epgFileText.text = "No file selected"
            Toast.makeText(this, "Files reset successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    /** Ensure required subfolders exist in the internal files directory */
    private fun ensureRequiredSubfolders() {
        val requiredFolders = listOf("input", "output", "configuration")
        requiredFolders.forEach { folderName ->
            val dir = File(filesDir, folderName)
            if (!dir.exists()) dir.mkdirs()
        }
    }

    /**
     * Deletes files older than 8 days from the 'input' and 'output' folders
     */
    /**
     * Deletes files older than 8 days from the 'input' and 'output' folders.
     * - Always keeps newest playlist and newest EPG file in each folder.
     */
    private fun deleteOldFiles() {
        val now = System.currentTimeMillis()

        // --- Cleanup function ---
        fun cleanFolder(folder: File) {
            if (!folder.exists() || !folder.isDirectory) return

            val files = folder.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

            // Find newest playlist and newest EPG in this folder
            var newestPlaylist: File? = null
            var newestEpg: File? = null

            files.forEach { file ->
                val ext = file.extension.lowercase()
                if (ext == "m3u" || ext == "m3u8") {
                    if (newestPlaylist == null || file.lastModified() > newestPlaylist!!.lastModified()) {
                        newestPlaylist = file
                    }
                } else if (ext == "xml" || ext == "gz") {
                    if (newestEpg == null || file.lastModified() > newestEpg!!.lastModified()) {
                        newestEpg = file
                    }
                }
            }

            // Delete any file older than 8 days that is not the newest playlist or newest EPG
            files.forEach { file ->
                val age = now - file.lastModified()
                val shouldKeep = (file == newestPlaylist || file == newestEpg)
                if (!shouldKeep && age > FILE_MAX_AGE_MS) {
                    if (file.delete()) {
                        Log.d("MainActivity", "Deleted old file: ${file.name}")
                    } else {
                        Log.w("MainActivity", "Failed to delete old file: ${file.name}")
                    }
                }
            }
        }

        // Clean input and output directories
        val inputFolder = File(filesDir, "input")
        val outputFolder = File(filesDir, "output")

        cleanFolder(inputFolder)
        cleanFolder(outputFolder)
    }

    private val FILE_MAX_AGE_MS = 8L * 24L * 60L * 60L * 1000L // 8 days


    /** Get a display name for a file path or URI */
    private fun getDisplayName(path: String?, uri: String?): String {
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) return file.name
        }

        if (!uri.isNullOrBlank()) {
            try {
                val parsedUri = Uri.parse(uri)
                val hasPermission = contentResolver.persistedUriPermissions.any {
                    it.uri == parsedUri && it.isReadPermission
                }
                if (hasPermission) {
                    val name = UriUtils.getFileNameFromUri(this, parsedUri)
                    if (name != null) return name
                }
            } catch (_: SecurityException) {
            }
        }

        return "Missing file, please (re)select"
    }

    /** File picker intent */
    private fun openFilePicker(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                when (requestCode) {
                    PICK_PLAYLIST_FILE -> arrayOf(
                        "audio/x-mpegurl",
                        "application/vnd.apple.mpegurl"
                    )
                    PICK_EPG_FILE -> arrayOf("application/xml", "application/gzip")
                    else -> arrayOf("*/*")
                }
            )
        }
        startActivityForResult(intent, requestCode)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            val uri = data.data ?: return
            when (requestCode) {
                PICK_PLAYLIST_FILE -> {
                    config.playlistUri = uri.toString()
                    config.playlistPath = UriUtils.getRealPathFromUri(this, uri)
                    playlistFileText.text = getDisplayName(config.playlistPath, config.playlistUri)
                    // Copy in background
                    lifecycleScope.launch {
                        FileManager.checkAndCopyIfNeeded(this@MainActivity, config.playlistPath)
                    }
                }
                PICK_EPG_FILE -> {
                    config.epgUri = uri.toString()
                    config.epgPath = UriUtils.getRealPathFromUri(this, uri)
                    epgFileText.text = getDisplayName(config.epgPath, config.epgUri)
                    // Copy in background
                    lifecycleScope.launch {
                        FileManager.checkAndCopyIfNeeded(this@MainActivity, config.epgPath)
                    }
                }
            }
            ConfigManager.saveConfig(this, config)
        }
    }

    /** Handle storage permission result */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied. Files may not load.", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }


}
