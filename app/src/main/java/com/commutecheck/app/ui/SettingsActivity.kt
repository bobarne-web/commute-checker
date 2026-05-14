package com.commutecheck.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.commutecheck.app.R
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.SavedLocation
import com.commutecheck.app.data.ScheduleConfig
import com.commutecheck.app.databinding.ActivitySettingsBinding
import java.util.Calendar

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefsManager: PreferencesManager

    private var currentSchedule: ScheduleConfig = ScheduleConfig()

    companion object {
        const val EXTRA_LOCATION_TYPE = "location_type"
        const val EXTRA_LOCATION_NAME = "location_name"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val LOCATION_TYPE_HOME = "home"
        const val LOCATION_TYPE_WORK = "work"
    }

    private val homeLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val name = data.getStringExtra(EXTRA_LOCATION_NAME) ?: "Home"
            val lat = data.getDoubleExtra(EXTRA_LATITUDE, 0.0)
            val lng = data.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
            prefsManager.saveHomeLocation(SavedLocation(name, lat, lng))
            updateUI()
            Toast.makeText(this, "Home location saved", Toast.LENGTH_SHORT).show()
        }
    }

    private val workLocationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val name = data.getStringExtra(EXTRA_LOCATION_NAME) ?: "Work"
            val lat = data.getDoubleExtra(EXTRA_LATITUDE, 0.0)
            val lng = data.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
            prefsManager.saveWorkLocation(SavedLocation(name, lat, lng))
            updateUI()
            Toast.makeText(this, "Work location saved", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        prefsManager = PreferencesManager(this)
        currentSchedule = prefsManager.getScheduleConfig()

        setupListeners()
        updateUI()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupListeners() {
        // Location pickers
        binding.btnSetHome.setOnClickListener {
            val intent = Intent(this, LocationPickerActivity::class.java).apply {
                putExtra(EXTRA_LOCATION_TYPE, LOCATION_TYPE_HOME)
            }
            homeLocationLauncher.launch(intent)
        }

        binding.btnSetWork.setOnClickListener {
            val intent = Intent(this, LocationPickerActivity::class.java).apply {
                putExtra(EXTRA_LOCATION_TYPE, LOCATION_TYPE_WORK)
            }
            workLocationLauncher.launch(intent)
        }

        // Day toggles
        binding.toggleSunday.setOnCheckedChangeListener { _, isChecked ->
            updateDay(Calendar.SUNDAY, isChecked)
        }
        binding.toggleMonday.setOnCheckedChangeListener { _, isChecked ->
            updateDay(Calendar.MONDAY, isChecked)
        }
        binding.toggleTuesday.setOnCheckedChangeListener { _, isChecked ->
            updateDay(Calendar.TUESDAY, isChecked)
        }
        binding.toggleWednesday.setOnCheckedChangeListener { _, isChecked ->
            updateDay(Calendar.WEDNESDAY, isChecked)
        }
        binding.toggleThursday.setOnCheckedChangeListener { _, isChecked ->
            updateDay(Calendar.THURSDAY, isChecked)
        }
        binding.toggleFriday.setOnCheckedChangeListener { _, isChecked ->
            updateDay(Calendar.FRIDAY, isChecked)
        }
        binding.toggleSaturday.setOnCheckedChangeListener { _, isChecked ->
            updateDay(Calendar.SATURDAY, isChecked)
        }

        // Time pickers
        binding.btnStartTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    currentSchedule = currentSchedule.copy(startHour = hour, startMinute = minute)
                    prefsManager.saveScheduleConfig(currentSchedule)
                    updateUI()
                },
                currentSchedule.startHour,
                currentSchedule.startMinute,
                true
            ).show()
        }

        binding.btnEndTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    currentSchedule = currentSchedule.copy(endHour = hour, endMinute = minute)
                    prefsManager.saveScheduleConfig(currentSchedule)
                    updateUI()
                },
                currentSchedule.endHour,
                currentSchedule.endMinute,
                true
            ).show()
        }

        // Geofence radius
        binding.seekbarRadius.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val radius = (progress + 1) * 100f // 100m to 2000m
                binding.tvRadiusValue.text = getString(R.string.radius_format, radius.toInt())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val radius = ((seekBar?.progress ?: 4) + 1) * 100f
                prefsManager.saveGeofenceRadius(radius)
            }
        })

        // Work always check
        binding.switchWorkAlwaysCheck.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setWorkAlwaysCheck(isChecked)
        }

        // API Key
        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            if (key.isNotEmpty()) {
                prefsManager.saveMapsApiKey(key)
                Toast.makeText(this, "API key saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDay(day: Int, enabled: Boolean) {
        val days = currentSchedule.enabledDays.toMutableSet()
        if (enabled) days.add(day) else days.remove(day)
        currentSchedule = currentSchedule.copy(enabledDays = days)
        prefsManager.saveScheduleConfig(currentSchedule)
    }

    private fun updateUI() {
        // Locations
        val home = prefsManager.getHomeLocation()
        val work = prefsManager.getWorkLocation()

        binding.tvHomeLocationValue.text = home?.name ?: getString(R.string.not_set)
        binding.tvWorkLocationValue.text = work?.name ?: getString(R.string.not_set)

        // Schedule days
        binding.toggleSunday.isChecked = Calendar.SUNDAY in currentSchedule.enabledDays
        binding.toggleMonday.isChecked = Calendar.MONDAY in currentSchedule.enabledDays
        binding.toggleTuesday.isChecked = Calendar.TUESDAY in currentSchedule.enabledDays
        binding.toggleWednesday.isChecked = Calendar.WEDNESDAY in currentSchedule.enabledDays
        binding.toggleThursday.isChecked = Calendar.THURSDAY in currentSchedule.enabledDays
        binding.toggleFriday.isChecked = Calendar.FRIDAY in currentSchedule.enabledDays
        binding.toggleSaturday.isChecked = Calendar.SATURDAY in currentSchedule.enabledDays

        // Time display
        binding.btnStartTime.text = String.format(
            "%02d:%02d", currentSchedule.startHour, currentSchedule.startMinute
        )
        binding.btnEndTime.text = String.format(
            "%02d:%02d", currentSchedule.endHour, currentSchedule.endMinute
        )

        // Geofence radius
        val radius = prefsManager.getGeofenceRadius()
        binding.seekbarRadius.progress = (radius / 100f).toInt() - 1
        binding.tvRadiusValue.text = getString(R.string.radius_format, radius.toInt())

        // Work always check
        binding.switchWorkAlwaysCheck.isChecked = prefsManager.isWorkAlwaysCheck()

        // API Key
        val apiKey = prefsManager.getMapsApiKey()
        if (apiKey.isNotEmpty()) {
            binding.etApiKey.setText(apiKey)
        }
    }
}
