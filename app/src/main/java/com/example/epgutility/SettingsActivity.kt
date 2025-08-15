// SettingsActivity.kt
package com.example.epgutility

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var config: ConfigManager.ConfigData
    private lateinit var textOutputFolder: TextView

    private val REQUEST_CODE_PICK_FOLDER = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Load config
        val loadResult = ConfigManager.loadConfig(this)
        config = loadResult.config

        textOutputFolder = findViewById(R.id.textOutputFolder)

        // Restore UI from config
        updateFolderDisplay()
        setupSpeedRadioButtons()

        // Button: Choose Folder
        findViewById<Button>(R.id.buttonChooseOutputFolder).setOnClickListener {
            launchFolderPicker()
        }

        // Save
        findViewById<Button>(R.id.buttonSaveSettings).setOnClickListener {
            saveSettings()
            finish()
        }

        // Cancel
        findViewById<Button>(R.id.buttonCancelSettings).setOnClickListener {
            finish()
        }
    }

    private fun launchFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        val chooser = Intent.createChooser(intent, "Pick any file in the output folder")
        startActivityForResult(chooser, REQUEST_CODE_PICK_FOLDER)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == RESULT_OK) {
            data?.data?.let { fileUri ->
                try {
                    // Grant persistent permission
                    contentResolver.takePersistableUriPermission(
                        fileUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    // Get parent folder via DocumentFile
                    val fileDocument = DocumentFile.fromSingleUri(this, fileUri)
                    val parentFolder = fileDocument?.parentFile

                    if (parentFolder != null) {
                        // ✅ Save folder URI and name
                        config.system.outputFolderUri = parentFolder.uri.toString()
                        config.system.outputFolderPath = parentFolder.name

                        // ✅ Show folder name
                        textOutputFolder.text = parentFolder.name

                        // ✅ Save to disk
                        ConfigManager.saveConfig(this, config)

                        Toast.makeText(this, "Output folder set: ${parentFolder.name}", Toast.LENGTH_LONG).show()
                    } else {
                        // Fallback: use path
                        val filePath = UriUtils.getRealPathFromUri(this, fileUri)
                        if (filePath != null) {
                            val file = File(filePath)
                            val folder = file.parentFile
                            if (folder != null) {
                                config.system.outputFolderPath = folder.name
                                config.system.outputFolderUri = fileUri.toString()
                                ConfigManager.saveConfig(this, config)
                                textOutputFolder.text = folder.name
                                Toast.makeText(this, "Folder selected (path mode)", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to access folder", Toast.LENGTH_SHORT).show()
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun updateFolderDisplay() {
        val folderName = config.system.outputFolderPath
        textOutputFolder.text = folderName ?: "No folder selected"
    }

    private fun setupSpeedRadioButtons() {
        val manualSpeed = config.system.manualFilterSpeed ?: "balanced"
        findViewById<RadioButton>(when (manualSpeed) {
            "full" -> R.id.radioManualFullSpeed
            "slow" -> R.id.radioManualSlow
            else -> R.id.radioManualBalanced
        }).isChecked = true

        val autoSpeed = config.system.autoFilterSpeed ?: "balanced"
        findViewById<RadioButton>(when (autoSpeed) {
            "full" -> R.id.radioAutoFullSpeed
            "slow" -> R.id.radioAutoSlow
            else -> R.id.radioAutoBalanced
        }).isChecked = true
    }

    private fun saveSettings() {
        val manualId = findViewById<RadioGroup>(R.id.radioGroupManualSpeed).checkedRadioButtonId
        val manualSpeed = when (manualId) {
            R.id.radioManualFullSpeed -> "full"
            R.id.radioManualSlow -> "slow"
            else -> "balanced"
        }

        val autoId = findViewById<RadioGroup>(R.id.radioGroupAutoSpeed).checkedRadioButtonId
        val autoSpeed = when (autoId) {
            R.id.radioAutoFullSpeed -> "full"
            R.id.radioAutoSlow -> "slow"
            else -> "balanced"
        }

        config.system.manualFilterSpeed = manualSpeed
        config.system.autoFilterSpeed = autoSpeed

        ConfigManager.saveConfig(this, config)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }
}