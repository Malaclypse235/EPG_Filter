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
    private lateinit var checkboxRemoveNewsWeather: CheckBox

    private lateinit var checkboxFoxNews: CheckBox
    private lateinit var checkboxCNN: CheckBox
    private lateinit var checkboxMSNBC: CheckBox
    private lateinit var checkboxNewsMax: CheckBox
    private lateinit var checkboxCNBC: CheckBox
    private lateinit var checkboxOAN: CheckBox
    private lateinit var checkboxWeatherChannel: CheckBox
    private lateinit var checkboxAccuWeather: CheckBox
    private lateinit var checkboxRemoveDuplicates: CheckBox

    // Current config data
    private lateinit var config: ConfigManager.ConfigData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filters)

        // Link to UI elements
        checkboxRemoveDuplicates = findViewById(R.id.checkboxRemoveDuplicates)
        removeNonLatinCheckBox = findViewById(R.id.checkboxNonLatin)
        removeNonEnglishCheckBox = findViewById(R.id.checkboxNonEnglish)
        buttonConfirm = findViewById(R.id.buttonConfirm)
        buttonCancel = findViewById(R.id.buttonCancel)
        checkboxRemoveNewsWeather = findViewById(R.id.checkboxRemoveNewsWeather)

        checkboxFoxNews = findViewById(R.id.checkboxFoxNews)
        checkboxCNN = findViewById(R.id.checkboxCNN)
        checkboxMSNBC = findViewById(R.id.checkboxMSNBC)
        checkboxNewsMax = findViewById(R.id.checkboxNewsMax)
        checkboxCNBC = findViewById(R.id.checkboxCNBC)
        checkboxOAN = findViewById(R.id.checkboxOAN)
        checkboxWeatherChannel = findViewById(R.id.checkboxWeatherChannel)
        checkboxAccuWeather = findViewById(R.id.checkboxAccuWeather)

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
        // ✅ FIXED: now reads from config.filters
        removeNonLatinCheckBox.isChecked = config.filters.removeNonLatin
        removeNonEnglishCheckBox.isChecked = config.filters.removeNonEnglish
        // Load News & Weather filter states
        checkboxRemoveNewsWeather.isChecked = config.filters.removeNewsAndWeather
        checkboxRemoveDuplicates.isChecked = config.filters.removeDuplicates

        checkboxFoxNews.isChecked = config.filters.removeFoxNews
        checkboxCNN.isChecked = config.filters.removeCNN
        checkboxMSNBC.isChecked = config.filters.removeMSNBC
        checkboxNewsMax.isChecked = config.filters.removeNewsMax
        checkboxCNBC.isChecked = config.filters.removeCNBC
        checkboxOAN.isChecked = config.filters.removeOAN
        checkboxWeatherChannel.isChecked = config.filters.removeWeatherChannel
        checkboxAccuWeather.isChecked = config.filters.removeAccuWeather

        // Add checkbox interaction logic
        setupCheckboxInteractions()

        // Save changes when confirm button pressed
        buttonConfirm.setOnClickListener {
            // Update config from UI
            // ✅ FIXED: now writes to config.filters
            config.filters.removeNonLatin = removeNonLatinCheckBox.isChecked
            config.filters.removeNonEnglish = removeNonEnglishCheckBox.isChecked
            // Save News & Weather filter states
            config.filters.removeNewsAndWeather = checkboxRemoveNewsWeather.isChecked

            config.filters.removeFoxNews = checkboxFoxNews.isChecked
            config.filters.removeCNN = checkboxCNN.isChecked
            config.filters.removeMSNBC = checkboxMSNBC.isChecked
            config.filters.removeNewsMax = checkboxNewsMax.isChecked
            config.filters.removeCNBC = checkboxCNBC.isChecked
            config.filters.removeOAN = checkboxOAN.isChecked
            config.filters.removeWeatherChannel = checkboxWeatherChannel.isChecked
            config.filters.removeAccuWeather = checkboxAccuWeather.isChecked
            config.filters.removeDuplicates = checkboxRemoveDuplicates.isChecked

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