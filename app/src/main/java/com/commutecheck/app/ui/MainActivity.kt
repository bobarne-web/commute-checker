package com.commutecheck.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.commutecheck.app.R
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.databinding.ActivityMainBinding
import com.commutecheck.app.service.CommuteCheckService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefsManager: PreferencesManager

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            requestBackgroundLocationIfNeeded()
        } else {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
        }
        updateUI()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Background location is needed for automatic checks",
                Toast.LENGTH_LONG
            ).show()
        }
        requestNotificationPermissionIfNeeded()
        updateUI()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "Notifications needed for travel time alerts", Toast.LENGTH_LONG)
                .show()
        }
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefsManager = PreferencesManager(this)

        setupListeners()
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupListeners() {
        binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefsManager.setAppEnabled(isChecked)
            updateUI()
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnTestCheck.setOnClickListener {
            if (!prefsManager.isConfigured()) {
                Toast.makeText(this, "Set home and work locations first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val serviceIntent = Intent(this, CommuteCheckService::class.java).apply {
                action = CommuteCheckService.ACTION_CHECK_COMMUTE
            }
            startForegroundService(serviceIntent)
            Toast.makeText(this, "Running commute check...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateUI() {
        val isEnabled = prefsManager.isAppEnabled()
        binding.switchEnabled.isChecked = isEnabled

        val homeLocation = prefsManager.getHomeLocation()
        val workLocation = prefsManager.getWorkLocation()
        val schedule = prefsManager.getScheduleConfig()

        binding.tvHomeLocation.text = if (homeLocation != null) {
            getString(R.string.location_set_format, homeLocation.name)
        } else {
            getString(R.string.location_not_set)
        }

        binding.tvWorkLocation.text = if (workLocation != null) {
            getString(R.string.location_set_format, workLocation.name)
        } else {
            getString(R.string.location_not_set)
        }

        val dayNames = mapOf(
            java.util.Calendar.SUNDAY to "Sun",
            java.util.Calendar.MONDAY to "Mon",
            java.util.Calendar.TUESDAY to "Tue",
            java.util.Calendar.WEDNESDAY to "Wed",
            java.util.Calendar.THURSDAY to "Thu",
            java.util.Calendar.FRIDAY to "Fri",
            java.util.Calendar.SATURDAY to "Sat"
        )

        val enabledDayStr = schedule.enabledDays
            .sorted()
            .mapNotNull { dayNames[it] }
            .joinToString(", ")

        val scheduleStr = buildString {
            append("Home→Work: $enabledDayStr\n")
            append(
                String.format(
                    "%02d:%02d – %02d:%02d",
                    schedule.startHour, schedule.startMinute,
                    schedule.endHour, schedule.endMinute
                )
            )
            append("\nWork→Home: Always")
        }
        binding.tvScheduleSummary.text = scheduleStr

        val statusText = when {
            !isEnabled -> "Disabled"
            !prefsManager.isConfigured() -> "Setup required — set home & work locations"
            !hasLocationPermission() -> "Location permission required"
            else -> "Active — waiting for Android Auto connection"
        }
        binding.tvStatus.text = statusText

        val statusColor = when {
            !isEnabled -> getColor(R.color.status_disabled)
            !prefsManager.isConfigured() || !hasLocationPermission() -> getColor(R.color.status_warning)
            else -> getColor(R.color.status_active)
        }
        binding.tvStatus.setTextColor(statusColor)
    }

    private fun requestPermissions() {
        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            requestBackgroundLocationIfNeeded()
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            requestNotificationPermissionIfNeeded()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
