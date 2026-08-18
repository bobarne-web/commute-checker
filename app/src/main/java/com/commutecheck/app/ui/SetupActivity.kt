package com.commutecheck.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.commutecheck.app.R
import com.commutecheck.app.data.Place
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.Watch
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/**
 * First-run flow: API key (if needed), Home, first destination, and a watch
 * whose window covers now so a test check can run immediately.
 */
class SetupActivity : AppCompatActivity() {

    private enum class Step { API_KEY, HOME, DESTINATION, WATCH }

    private lateinit var prefs: PreferencesManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var content: LinearLayout
    private var step: Step = Step.API_KEY

    private var homePlace: Place? = null
    private var destinationPlace: Place? = null
    private var pendingPlaceName: String = ""
    private var pendingPlaceIsHome: Boolean = true

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* picker / geocode will re-check */ }

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val lat = data.getDoubleExtra(LocationPickerActivity.EXTRA_LATITUDE, 0.0)
        val lng = data.getDoubleExtra(LocationPickerActivity.EXTRA_LONGITUDE, 0.0)
        val address = data.getStringExtra(LocationPickerActivity.EXTRA_ADDRESS) ?: ""
        savePendingPlace(lat, lng, address)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        prefs = PreferencesManager(this)
        supportActionBar?.hide()

        if (!prefs.needsSetup()) {
            finish()
            return
        }

        step = firstIncompleteStep()
        setContentView(buildShell())
        renderStep()
        requestForegroundLocationIfNeeded()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun firstIncompleteStep(): Step {
        if (!prefs.hasUsableApiKey()) return Step.API_KEY
        val places = prefs.getPlaces()
        homePlace = places.firstOrNull { it.name.equals("Home", ignoreCase = true) } ?: places.firstOrNull()
        if (homePlace == null) return Step.HOME
        destinationPlace = places.firstOrNull { it.id != homePlace?.id }
        if (destinationPlace == null) return Step.DESTINATION
        if (prefs.getWatches().isEmpty()) return Step.WATCH
        return Step.API_KEY
    }

    private fun buildShell(): View {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
        }
        screen.addView(MaterialToolbar(this).apply {
            title = getString(R.string.setup_title)
            setPadding(0, statusBarHeight(), 0, 0)
            setTitleTextColor(getColor(R.color.text_primary))
            setBackgroundColor(getColor(R.color.surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56) + statusBarHeight()
            )
        })
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        screen.addView(ScrollView(this).apply {
            addView(content)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        })
        return screen
    }

    private fun renderStep() {
        content.removeAllViews()
        when (step) {
            Step.API_KEY -> renderApiKey()
            Step.HOME -> renderPlaceStep(
                title = getString(R.string.setup_home_title),
                body = getString(R.string.setup_home_body),
                defaultName = "Home",
                isHome = true
            )
            Step.DESTINATION -> renderPlaceStep(
                title = getString(R.string.setup_destination_title),
                body = getString(R.string.setup_destination_body),
                defaultName = "",
                isHome = false
            )
            Step.WATCH -> renderWatch()
        }
    }

    private fun renderApiKey() {
        content.addView(heading(getString(R.string.setup_api_title)))
        content.addView(body(getString(R.string.api_key_description)))
        val input = EditText(this).apply {
            hint = getString(R.string.api_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getMapsApiKey())
        }
        content.addView(input)
        content.addView(primaryButton(getString(R.string.setup_continue)) {
            val key = input.text.toString().trim()
            if (key.isEmpty() && !prefs.hasUsableApiKey()) {
                Toast.makeText(this, getString(R.string.setup_api_required), Toast.LENGTH_LONG).show()
                return@primaryButton
            }
            if (key.isNotEmpty()) prefs.saveMapsApiKey(key)
            advanceFromApiKey()
        })
    }

    private fun advanceFromApiKey() {
        val places = prefs.getPlaces()
        homePlace = homePlace
            ?: places.firstOrNull { it.name.equals("Home", ignoreCase = true) }
            ?: places.firstOrNull()
        destinationPlace = destinationPlace
            ?: places.firstOrNull { it.id != homePlace?.id }
        step = when {
            homePlace == null -> Step.HOME
            destinationPlace == null -> Step.DESTINATION
            prefs.getWatches().isEmpty() -> Step.WATCH
            else -> {
                finishSetup()
                return
            }
        }
        renderStep()
    }

    private fun renderPlaceStep(
        title: String,
        body: String,
        defaultName: String,
        isHome: Boolean
    ) {
        content.addView(heading(title))
        content.addView(body(body))
        content.addView(label(getString(R.string.setup_place_name)))
        val nameInput = EditText(this).apply {
            hint = if (isHome) "Home" else "e.g. Work, Truckee"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(defaultName)
        }
        content.addView(nameInput)
        content.addView(body(getString(R.string.setup_place_location)))
        content.addView(primaryButton(getString(R.string.setup_pick_map)) {
            val name = nameInput.text.toString().trim().ifEmpty { defaultName }
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.setup_name_required), Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            pendingPlaceName = name
            pendingPlaceIsHome = isHome
            pickerLauncher.launch(Intent(this, LocationPickerActivity::class.java).apply {
                putExtra(LocationPickerActivity.EXTRA_TITLE, "Set $name")
            })
        })
        content.addView(primaryButton(getString(R.string.setup_street_address)) {
            val name = nameInput.text.toString().trim().ifEmpty { defaultName }
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.setup_name_required), Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            showAddressDialog(name, isHome)
        })
        content.addView(primaryButton(getString(R.string.setup_gps_coords)) {
            val name = nameInput.text.toString().trim().ifEmpty { defaultName }
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.setup_name_required), Toast.LENGTH_SHORT).show()
                return@primaryButton
            }
            showCoordsDialog(name, isHome)
        })
    }

    private fun showAddressDialog(name: String, isHome: Boolean) {
        val input = EditText(this).apply {
            hint = "e.g. 205 South Sierra Street, Reno NV"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setup_street_address))
            .setView(input)
            .setPositiveButton(getString(R.string.setup_lookup)) { _, _ ->
                val addr = input.text.toString().trim()
                if (addr.isEmpty()) {
                    Toast.makeText(this, getString(R.string.setup_address_required), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                pendingPlaceName = name
                pendingPlaceIsHome = isHome
                geocodeAddress(name, addr, isHome)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCoordsDialog(name: String, isHome: Boolean) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        val latInput = EditText(this).apply {
            hint = "Latitude"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        val lngInput = EditText(this).apply {
            hint = "Longitude"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        container.addView(latInput)
        container.addView(lngInput)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.setup_gps_coords))
            .setView(container)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val lat = latInput.text.toString().toDoubleOrNull()
                val lng = lngInput.text.toString().toDoubleOrNull()
                if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                    Toast.makeText(this, getString(R.string.setup_coords_invalid), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                pendingPlaceName = name
                pendingPlaceIsHome = isHome
                savePendingPlace(lat, lng, "")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun geocodeAddress(name: String, address: String, isHome: Boolean) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    Geocoder(this@SetupActivity, Locale.getDefault())
                        .getFromLocationName(address, 1)
                        ?.firstOrNull()
                } catch (_: Exception) {
                    null
                }
            }
            if (result == null) {
                Toast.makeText(this@SetupActivity, getString(R.string.setup_address_not_found), Toast.LENGTH_LONG).show()
                return@launch
            }
            pendingPlaceName = name
            pendingPlaceIsHome = isHome
            savePendingPlace(result.latitude, result.longitude, result.getAddressLine(0) ?: address)
        }
    }

    private fun savePendingPlace(lat: Double, lng: Double, address: String) {
        val place = Place(
            id = Place.newId(),
            name = pendingPlaceName.ifBlank { if (pendingPlaceIsHome) "Home" else "Place" },
            latitude = lat,
            longitude = lng,
            address = address
        )
        prefs.upsertPlace(place)
        if (pendingPlaceIsHome) {
            homePlace = place
            destinationPlace = prefs.getPlaces().firstOrNull { it.id != place.id }
            step = if (destinationPlace == null) Step.DESTINATION else Step.WATCH
        } else {
            destinationPlace = place
            step = Step.WATCH
        }
        renderStep()
    }

    private fun renderWatch() {
        val dest = destinationPlace ?: run {
            step = Step.DESTINATION
            renderStep()
            return
        }
        val preview = Watch.coveringNow(
            name = "To ${dest.name}",
            destinationPlaceId = dest.id,
            now = Calendar.getInstance()
        )
        content.addView(heading(getString(R.string.setup_watch_title)))
        content.addView(body(getString(R.string.setup_watch_body, dest.name, formatWindow(preview))))
        content.addView(primaryButton(getString(R.string.setup_finish)) {
            prefs.upsertWatch(preview)
            finishSetup()
        })
    }

    private fun finishSetup() {
        Toast.makeText(this, getString(R.string.setup_done), Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    private fun formatWindow(watch: Watch): String {
        return "${format12(watch.startHour, watch.startMinute)}–${format12(watch.endHour, watch.endMinute)}"
    }

    private fun format12(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format("%d:%02d %s", h, minute, amPm)
    }

    private fun requestForegroundLocationIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun heading(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 20f
        setTextColor(getColor(R.color.text_primary))
        setPadding(0, 0, 0, dp(8))
    }

    private fun body(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(getColor(R.color.text_secondary))
        setPadding(0, 0, 0, dp(12))
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(getColor(R.color.text_secondary))
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun primaryButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }
}
