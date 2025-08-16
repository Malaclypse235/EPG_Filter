package com.example.epgutility

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var config: ConfigManager.ConfigData

    private val REQUEST_CODE_PICK_FOLDER = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Load config
        val loadResult = ConfigManager.loadConfig(this)
        config = loadResult.config

        // Restore UI from config
        setupOutputLocationRadioButtons()
        setupSpeedRadioButtons()

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

    private fun setupOutputLocationRadioButtons() {
        findViewById<RadioButton>(
            when (config.system.outputLocation) {
                "Downloads" -> R.id.radioDownloads
                else -> R.id.radioDocuments  // default
            }
        ).isChecked = true
    }

    private fun setupSpeedRadioButtons() {
        val manualSpeed = config.system.manualFilterSpeed
        findViewById<RadioButton>(
            when (manualSpeed) {
                "full" -> R.id.radioManualFullSpeed
                "slow" -> R.id.radioManualSlow
                else -> R.id.radioManualBalanced
            }
        ).isChecked = true

        val autoSpeed = config.system.autoFilterSpeed
        findViewById<RadioButton>(
            when (autoSpeed) {
                "full" -> R.id.radioAutoFullSpeed
                "slow" -> R.id.radioAutoSlow
                else -> R.id.radioAutoBalanced
            }
        ).isChecked = true
    }

    private fun saveSettings() {
        // Save output location
        val outputId = findViewById<RadioGroup>(R.id.radioGroupOutputLocation).checkedRadioButtonId
        config.system.outputLocation = when (outputId) {
            R.id.radioDownloads -> "Downloads"
            else -> "Documents"
        }

        // Save filter speeds
        val manualId = findViewById<RadioGroup>(R.id.radioGroupManualSpeed).checkedRadioButtonId
        config.system.manualFilterSpeed = when (manualId) {
            R.id.radioManualFullSpeed -> "full"
            R.id.radioManualSlow -> "slow"
            else -> "balanced"
        }

        val autoId = findViewById<RadioGroup>(R.id.radioGroupAutoSpeed).checkedRadioButtonId
        config.system.autoFilterSpeed = when (autoId) {
            R.id.radioAutoFullSpeed -> "full"
            R.id.radioAutoSlow -> "slow"
            else -> "balanced"
        }

        // Save to file
        ConfigManager.saveConfig(this, config)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }
}