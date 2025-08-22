package com.example.epgutility

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var config: ConfigManager.ConfigData

    private lateinit var switchAutoMode: Switch
    private lateinit var radioGroupAutoInterval: RadioGroup

    private val REQUEST_CODE_PICK_FOLDER = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Load config
        val loadResult = ConfigManager.loadConfig(this)
        config = loadResult.config

        switchAutoMode = findViewById(R.id.switchAutoMode)
        radioGroupAutoInterval = findViewById(R.id.radioGroupAutoInterval)

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

        // Setup Auto Mode Toggle
        switchAutoMode.isChecked = config.system.autoModeEnabled
        switchAutoMode.setOnCheckedChangeListener { _, isChecked ->
            config.system.autoModeEnabled = isChecked  // Update config

            if (isChecked) {
                // ✅ User turned ON → schedule worker
                WorkerScheduler.scheduleWork(this@SettingsActivity)
                Toast.makeText(this, "Auto check enabled", Toast.LENGTH_SHORT).show()
            } else {
                // ✅ User turned OFF → cancel worker
                WorkerScheduler.cancelWork(this@SettingsActivity)
                Toast.makeText(this, "Auto check disabled", Toast.LENGTH_SHORT).show()
            }
        }

// Setup Auto Interval
        when (config.system.autoCheckInterval) {
            "30_min" -> R.id.radioInterval30Min
            "1_hour" -> R.id.radioInterval1Hour
            "6_hours" -> R.id.radioInterval6Hours
            "12_hours" -> R.id.radioInterval12Hours
            else -> R.id.radioInterval24Hours  // default
        }.also { findViewById<RadioButton>(it).isChecked = true }
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

        // ✅ Save Auto Mode settings (before saving to file)
        config.system.autoModeEnabled = switchAutoMode.isChecked
        config.system.autoCheckInterval = when (radioGroupAutoInterval.checkedRadioButtonId) {
            R.id.radioInterval30Min -> "30_min"
            R.id.radioInterval1Hour -> "1_hour"
            R.id.radioInterval6Hours -> "6_hours"
            R.id.radioInterval12Hours -> "12_hours"
            else -> "24_hours"
        }

        // ✅ Now save the entire config to disk
        ConfigManager.saveConfig(this, config)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
    }

}