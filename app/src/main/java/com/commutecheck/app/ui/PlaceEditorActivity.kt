package com.commutecheck.app.ui

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.commutecheck.app.R
import com.commutecheck.app.data.Place
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.domain.PlaceText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * One screen for a place's name, detection radius, and location.
 * Save keeps the current pin when only the name or radius changed.
 */
class PlaceEditorActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var existingId: String? = null
    private var pendingAddress: String = ""
    private var addressMatchesCoords: Boolean = true
    private var suppressCoordWatchers: Boolean = false

    private lateinit var nameInput: EditText
    private lateinit var radiusInput: EditText
    private lateinit var radiusPreview: TextView
    private lateinit var locationSummary: TextView
    private lateinit var addressInput: EditText
    private lateinit var latInput: EditText
    private lateinit var lngInput: EditText

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val lat = data.getDoubleExtra(LocationPickerActivity.EXTRA_LATITUDE, Double.NaN)
        val lng = data.getDoubleExtra(LocationPickerActivity.EXTRA_LONGITUDE, Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return@registerForActivityResult
        val address = data.getStringExtra(LocationPickerActivity.EXTRA_ADDRESS).orEmpty()
        if (address.isNotBlank()) {
            applyLocation(lat, lng, address, matchesCoords = true)
            addressInput.setText(address)
        } else {
            applyLocation(lat, lng, "", matchesCoords = false)
            addressInput.setText("")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        supportActionBar?.hide()

        existingId = intent.getStringExtra(EXTRA_PLACE_ID)
        val existing = existingId?.let { prefs.getPlace(it) }
        pendingAddress = existing?.address.orEmpty()

        val scaffold = screenScaffold(
            title = if (existing == null) "New Place" else "Edit Place",
            showBack = true
        )
        val root = scaffold.content

        root.addView(label("Name"))
        nameInput = EditText(this).apply {
            hint = "e.g. Home, Work, Truckee"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(existing?.name ?: "")
            setTextColor(getColor(R.color.text_primary))
        }
        root.addView(nameInput)

        root.addView(label("Detection radius (meters)"))
        radiusInput = EditText(this).apply {
            hint = "500"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(((existing?.radiusMeters ?: Place.DEFAULT_RADIUS_METERS).toInt()).toString())
            setTextColor(getColor(R.color.text_primary))
            addTextChangedListener(simpleWatcher { updateRadiusPreview() })
        }
        root.addView(radiusInput)
        radiusPreview = TextView(this).apply {
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(4), 0, 0)
        }
        root.addView(radiusPreview)
        updateRadiusPreview()

        root.addView(label("Location"))
        locationSummary = TextView(this).apply {
            textSize = 15f
            setTextColor(getColor(R.color.text_primary))
        }
        root.addView(locationSummary)

        root.addView(filledButton("Pick on map") {
            launchPicker()
        })

        root.addView(label("Street address"))
        addressInput = EditText(this).apply {
            hint = "e.g. 205 South Sierra Street, Reno NV"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(existing?.address.orEmpty())
            setTextColor(getColor(R.color.text_primary))
        }
        root.addView(addressInput)
        root.addView(tonalButton("Look up address") {
            val typed = addressInput.text.toString().trim()
            if (typed.isEmpty()) {
                Toast.makeText(this, "Enter an address", Toast.LENGTH_SHORT).show()
            } else {
                geocodeAddress(typed)
            }
        })

        root.addView(label("Latitude"))
        latInput = EditText(this).apply {
            hint = "e.g. 39.3280"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setTextColor(getColor(R.color.text_primary))
            addTextChangedListener(simpleWatcher { onCoordsEdited() })
        }
        root.addView(latInput)

        root.addView(label("Longitude"))
        lngInput = EditText(this).apply {
            hint = "e.g. -120.1833"
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setTextColor(getColor(R.color.text_primary))
            addTextChangedListener(simpleWatcher { onCoordsEdited() })
        }
        root.addView(lngInput)

        if (existing != null) {
            applyLocation(existing.latitude, existing.longitude, existing.address, matchesCoords = true)
        } else {
            refreshSummary()
        }

        root.addView(filledButton(getString(R.string.save)) { save() })
        setContentView(scaffold.screen)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun launchPicker() {
        val name = nameInput.text.toString().trim().ifBlank { "Place" }
        val intent = Intent(this, LocationPickerActivity::class.java).apply {
            putExtra(LocationPickerActivity.EXTRA_TITLE, "Set $name")
            val lat = latInput.text.toString().toDoubleOrNull()
            val lng = lngInput.text.toString().toDoubleOrNull()
            if (lat != null && lng != null) {
                putExtra(LocationPickerActivity.EXTRA_INITIAL_LATITUDE, lat)
                putExtra(LocationPickerActivity.EXTRA_INITIAL_LONGITUDE, lng)
            }
        }
        pickerLauncher.launch(intent)
    }

    private fun geocodeAddress(address: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    Geocoder(this@PlaceEditorActivity, Locale.getDefault())
                        .getFromLocationName(address, 1)
                        ?.firstOrNull()
                } catch (_: Exception) {
                    null
                }
            }
            if (result == null) {
                Toast.makeText(
                    this@PlaceEditorActivity,
                    "Could not find that address. Try a different spelling or GPS coordinates.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }
            val formatted = result.getAddressLine(0)?.takeIf { it.isNotBlank() } ?: address
            applyLocation(result.latitude, result.longitude, formatted, matchesCoords = true)
            addressInput.setText(formatted)
        }
    }

    private fun applyLocation(lat: Double, lng: Double, address: String, matchesCoords: Boolean) {
        suppressCoordWatchers = true
        latInput.setText(lat.toString())
        lngInput.setText(lng.toString())
        suppressCoordWatchers = false
        pendingAddress = address
        addressMatchesCoords = matchesCoords
        refreshSummary()
    }

    private fun onCoordsEdited() {
        if (suppressCoordWatchers) return
        addressMatchesCoords = false
        refreshSummary()
    }

    private fun updateRadiusPreview() {
        val radius = radiusInput.text.toString().toFloatOrNull() ?: Place.DEFAULT_RADIUS_METERS
        radiusPreview.text = PlaceText.radiusPhrase(radius)
    }

    private fun refreshSummary() {
        val lat = latInput.text.toString().toDoubleOrNull()
        val lng = lngInput.text.toString().toDoubleOrNull()
        locationSummary.text = when {
            lat == null || lng == null -> "Location not set"
            pendingAddress.isNotBlank() && addressMatchesCoords -> pendingAddress
            else -> "Location set"
        }
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show()
            return
        }
        val radius = radiusInput.text.toString().toFloatOrNull()
        if (radius == null || radius <= 0f) {
            Toast.makeText(this, "Enter a radius in meters", Toast.LENGTH_SHORT).show()
            return
        }
        val lat = latInput.text.toString().toDoubleOrNull()
        val lng = lngInput.text.toString().toDoubleOrNull()
        if (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0) {
            Toast.makeText(this, "Set a location before saving", Toast.LENGTH_SHORT).show()
            return
        }
        val address = if (addressMatchesCoords) addressInput.text.toString().trim() else ""
        val place = Place(
            id = existingId ?: Place.newId(),
            name = name,
            latitude = lat,
            longitude = lng,
            radiusMeters = radius,
            address = address
        )
        prefs.upsertPlace(place)
        Toast.makeText(this, "Saved ${place.name}", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setPadding(0, dp(12), 0, dp(4))
        setTextColor(getColor(R.color.text_secondary))
    }

    private fun simpleWatcher(onChange: () -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            onChange()
        }
    }

    companion object {
        const val EXTRA_PLACE_ID = "place_id"
    }
}
