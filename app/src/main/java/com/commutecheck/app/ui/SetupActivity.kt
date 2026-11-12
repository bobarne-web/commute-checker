package com.commutecheck.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.commutecheck.app.R
import com.commutecheck.app.data.Place
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.Watch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/**
 * First-run flow: Home, first destination, and a watch whose window covers now
 * so a test check can run immediately. The Maps key comes from the build.
 */
class SetupActivity : AppCompatActivity() {

    private enum class Step { HOME, DESTINATION, WATCH }

    private lateinit var prefs: PreferencesManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var content: LinearLayout
    private var step: Step = Step.HOME

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
        prepareEdgeToEdge()
        super.onCreate(savedInstanceState)
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
        val places = prefs.getPlaces()
        homePlace = places.firstOrNull { it.name.equals("Home", ignoreCase = true) } ?: places.firstOrNull()
        if (homePlace == null) return Step.HOME
        destinationPlace = places.firstOrNull { it.id != homePlace?.id }
        if (destinationPlace == null) return Step.DESTINATION
        return Step.WATCH
    }

    private fun buildShell(): View {
        val scaffold = screenScaffold(title = getString(R.string.setup_title), showBack = false)
        content = scaffold.content
        return scaffold.screen
    }

    private fun renderStep() {
        content.removeAllViews()
        when (step) {
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
        content.addView(filledButton(getString(R.string.setup_pick_map)) {
            val name = nameInput.text.toString().trim().ifEmpty { defaultName }
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.setup_name_required), Toast.LENGTH_SHORT).show()
                return@filledButton
            }
            pendingPlaceName = name
            pendingPlaceIsHome = isHome
            pickerLauncher.launch(Intent(this, LocationPickerActivity::class.java).apply {
                putExtra(LocationPickerActivity.EXTRA_TITLE, "Set $name")
            })
        })
        content.addView(filledButton(getString(R.string.setup_street_address)) {
            val name = nameInput.text.toString().trim().ifEmpty { defaultName }
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.setup_name_required), Toast.LENGTH_SHORT).show()
                return@filledButton
            }
            showAddressDialog(name, isHome)
        })
        content.addView(filledButton(getString(R.string.setup_gps_coords)) {
            val name = nameInput.text.toString().trim().ifEmpty { defaultName }
            if (name.isEmpty()) {
                Toast.makeText(this, getString(R.string.setup_name_required), Toast.LENGTH_SHORT).show()
                return@filledButton
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
        content.addView(filledButton(getString(R.string.setup_finish)) {
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
