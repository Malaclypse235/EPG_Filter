package com.example.epgutility

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FiltersActivity : AppCompatActivity() {

    private lateinit var removeNonLatinCheckBox: CheckBox
    private lateinit var removeNonEnglishCheckBox: CheckBox
    private lateinit var buttonConfirm: Button
    private lateinit var buttonCancel: Button

    // Current config data
    private lateinit var config: ConfigManager.ConfigData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filters)

        // Link to UI elements
        removeNonLatinCheckBox = findViewById(R.id.checkboxNonLatin)
        removeNonEnglishCheckBox = findViewById(R.id.checkboxNonEnglish)
        buttonConfirm = findViewById(R.id.buttonConfirm)
        buttonCancel = findViewById(R.id.buttonCancel)

        // Load config (create defaults if missing)
        val loadResult = ConfigManager.loadConfig(this)
        config = loadResult.config

        // If config files didn't exist, we now force-save defaults
        if (!configExists()) {
            ConfigManager.saveConfig(this, config)
        }

        // Show toast if we lost file access (but still allow filters)
        if (loadResult.revokedPermissions) {
            Toast.makeText(
                this,
                "⚠️ File permissions were revoked. Please re-select your files on the main screen.",
                Toast.LENGTH_LONG
            ).show()
        }

        // Initialize checkboxes with saved values
        removeNonLatinCheckBox.isChecked = config.removeNonLatin
        removeNonEnglishCheckBox.isChecked = config.removeNonEnglish

        // Add checkbox interaction logic
        setupCheckboxInteractions()

        // Save changes when confirm button pressed
        buttonConfirm.setOnClickListener {
            // Update config from UI
            config.removeNonLatin = removeNonLatinCheckBox.isChecked
            config.removeNonEnglish = removeNonEnglishCheckBox.isChecked

            // Save updated config and backup
            ConfigManager.saveConfig(this, config)

            // Close and go back to main
            Toast.makeText(this, "Filters saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Cancel button - just close
        buttonCancel.setOnClickListener {
            finish()
        }
    }

    /**
     * Setup interactions between the checkboxes to prevent conflicts
     */
    private fun setupCheckboxInteractions() {
        // When Non-English is checked, suggest unchecking Non-Latin (since Non-English is stricter)
        removeNonEnglishCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && removeNonLatinCheckBox.isChecked) {
                Toast.makeText(
                    this,
                    "💡 Non-English filter is stricter than Non-Latin. Non-Latin filter disabled.",
                    Toast.LENGTH_SHORT
                ).show()
                removeNonLatinCheckBox.isChecked = false
            }
        }

        // When Non-Latin is checked and Non-English is already checked, warn user
        removeNonLatinCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && removeNonEnglishCheckBox.isChecked) {
                Toast.makeText(
                    this,
                    "💡 Non-English filter is already active (stricter than Non-Latin).",
                    Toast.LENGTH_SHORT
                ).show()
                removeNonLatinCheckBox.isChecked = false
            }
        }
    }

    /**
     * Helper to check if config.json or backup.json exists.
     */
    private fun configExists(): Boolean {
        val configFile = getFileStreamPath("config.json")
        val backupFile = getFileStreamPath("config_backup.json")
        return configFile.exists() || backupFile.exists()
    }
}