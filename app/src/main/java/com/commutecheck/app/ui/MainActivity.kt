package com.commutecheck.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.commutecheck.app.R
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.RouteCheckResult
import com.commutecheck.app.service.CommuteCheckService

class MainActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager

    private lateinit var statusView: TextView
    private lateinit var enableSwitch: SwitchCompat
    private lateinit var summaryView: TextView
    private lateinit var placesButton: Button
    private lateinit var watchesButton: Button

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false) ||
            (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false)
        if (granted) requestBackgroundLocationIfNeeded()
        else Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show()
        updateUI()
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        requestNotificationPermissionIfNeeded()
        updateUI()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateUI() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefsManager = PreferencesManager(this)
        setContentView(buildLayout())
        supportActionBar?.title = getString(R.string.app_name)
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun buildLayout(): View {
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_subtitle)
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
        })

        // Enable row
        val enableRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, dp(8))
        }
        enableRow.addView(TextView(this).apply {
            text = getString(R.string.enabled)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        enableSwitch = SwitchCompat(this).apply {
            setOnCheckedChangeListener { _, isChecked ->
                prefsManager.setAppEnabled(isChecked)
                updateUI()
            }
        }
        enableRow.addView(enableSwitch)
        root.addView(enableRow)

        statusView = TextView(this).apply {
            textSize = 16f
            setPadding(0, 0, 0, dp(16))
        }
        root.addView(statusView)

        placesButton = primaryButton("Manage Places") {
            startActivity(Intent(this, PlacesActivity::class.java))
        }
        root.addView(placesButton)

        watchesButton = primaryButton("Manage Commute Watches") {
            startActivity(Intent(this, WatchesActivity::class.java))
        }
        root.addView(watchesButton)

        root.addView(primaryButton(getString(R.string.settings_title)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })

        root.addView(primaryButton(getString(R.string.test_check)) {
            runTestCheck()
        })

        root.addView(TextView(this).apply {
            text = "Latest results"
            textSize = 18f
            setPadding(0, dp(24), 0, dp(8))
            setTextColor(getColor(R.color.text_primary))
        })

        summaryView = TextView(this).apply {
            textSize = 15f
            setTextColor(getColor(R.color.text_secondary))
        }
        root.addView(summaryView)

        return ScrollView(this).apply { addView(root) }
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
            setOnClickListener { onClick() }
        }
    }

    private fun runTestCheck() {
        if (!prefsManager.isConfigured()) {
            Toast.makeText(this, "Add at least one place and one watch first", Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, CommuteCheckService::class.java).apply {
            action = CommuteCheckService.ACTION_CHECK_COMMUTE
        }
        startForegroundService(intent)
        Toast.makeText(this, "Running commute check...", Toast.LENGTH_SHORT).show()
    }

    private fun updateUI() {
        val isEnabled = prefsManager.isAppEnabled()
        enableSwitch.isChecked = isEnabled

        val placeCount = prefsManager.getPlaces().size
        val watchCount = prefsManager.getWatches().size
        placesButton.text = "Manage Places ($placeCount)"
        watchesButton.text = "Manage Commute Watches ($watchCount)"

        val (statusText, statusColor) = when {
            !isEnabled -> "Disabled" to R.color.status_disabled
            !prefsManager.isConfigured() ->
                "Setup required — add places and watches" to R.color.status_warning
            !hasLocationPermission() ->
                "Location permission required" to R.color.status_warning
            else ->
                "Active — waiting for Android Auto connection" to R.color.status_active
        }
        statusView.text = statusText
        statusView.setTextColor(getColor(statusColor))

        summaryView.text = buildSummary()
    }

    private fun buildSummary(): String {
        val results = prefsManager.getLastResults()
        if (results.isEmpty()) {
            return "No checks yet. Tap \"${getString(R.string.test_check)}\" to try it now."
        }
        val place = prefsManager.getLastResultsPlace()
        val time = prefsManager.getLastResultsTime()
        val header = buildString {
            if (place.isNotEmpty()) append("From $place")
            if (time > 0) {
                if (isNotEmpty()) append(" · ")
                append(DateUtils.getRelativeTimeSpanString(time))
            }
        }
        val lines = results.joinToString("\n") { formatResult(it) }
        return if (header.isNotEmpty()) "$header\n\n$lines" else lines
    }

    private fun formatResult(r: RouteCheckResult): String {
        if (r.hasError) return "• ${r.destinationName}: unavailable"
        val distance = if (r.distanceText.isNotBlank()) " · ${r.distanceText}" else ""
        val status = if (r.isDelayed) {
            "🔴 ${r.durationInTrafficText}$distance  (+${r.delayMinutes} min)"
        } else {
            "🟢 ${r.durationInTrafficText}$distance"
        }
        return "• ${r.destinationName}: $status"
    }

    // --- Permissions ---

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

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
