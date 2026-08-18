package com.commutecheck.app.ui

import android.content.res.ColorStateList
import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import android.text.InputType
import android.view.Gravity

import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowCompat
import com.commutecheck.app.R
import com.commutecheck.app.data.Place
import com.commutecheck.app.data.PreferencesManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Lets the user manage saved places (Home, Work, Truckee, Reno, …). Each place has
 * a friendly name, coordinates (from map / address / manual), and a detection radius.
 */
class PlacesActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var listContainer: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var pendingName: String = ""
    private var pendingRadius: Float = Place.DEFAULT_RADIUS_METERS
    private var editingId: String? = null

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val lat = data.getDoubleExtra(LocationPickerActivity.EXTRA_LATITUDE, 0.0)
            val lng = data.getDoubleExtra(LocationPickerActivity.EXTRA_LONGITUDE, 0.0)
            val address = data.getStringExtra(LocationPickerActivity.EXTRA_ADDRESS) ?: ""
            savePlace(pendingName, lat, lng, address, pendingRadius, editingId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        prefs = PreferencesManager(this)
        supportActionBar?.hide()

        val pad = dp(16)
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
        }

        screen.addView(MaterialToolbar(this).apply {
            title = "Places"
            navigationIcon = AppCompatResources.getDrawable(
                this@PlacesActivity,
                androidx.appcompat.R.drawable.abc_ic_ab_back_material
            )
            setPadding(0, statusBarHeight(), 0, 0)
            setNavigationOnClickListener { finish() }
            setTitleTextColor(getColor(R.color.text_primary))
            setBackgroundColor(getColor(R.color.surface))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(56) + statusBarHeight()
            )
        })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(Button(this).apply {
            text = "Add Place"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { showPlaceDialog(null) }
        })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        screen.addView(ScrollView(this).apply {
            addView(root)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        })

        setContentView(screen)
        renderList()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    // ── List rendering ──────────────────────────────────────────────────

    private fun renderList() {
        listContainer.removeAllViews()
        val places = prefs.getPlaces()
        if (places.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No places yet. Add Home, Work, and any destinations you check often."
                setPadding(0, dp(16), 0, 0)
                setTextColor(getColor(R.color.text_secondary))
            })
            return
        }

        places.forEach { place ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
                setBackgroundColor(getColor(R.color.primary_container))
            }
            card.addView(TextView(this).apply {
                text = place.name
                textSize = 18f
                setTextColor(getColor(R.color.on_primary_container))
            })
            val subtitle = if (place.address.isNotBlank()) {
                "${place.address}\n${formatCoords(place)} · radius ${place.radiusMeters.toInt()}m"
            } else {
                "${formatCoords(place)} · radius ${place.radiusMeters.toInt()}m"
            }
            card.addView(TextView(this).apply {
                text = subtitle
                setTextColor(getColor(R.color.on_primary_container))
            })

            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            buttons.addView(Button(this).apply {
                text = "Edit"
                setOnClickListener { showPlaceDialog(place) }
            })
            buttons.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener { confirmDelete(place) }
            })
            card.addView(buttons)
            listContainer.addView(card)
        }
    }

    private fun formatCoords(place: Place): String =
        String.format("%.4f, %.4f", place.latitude, place.longitude)

    // ── Main place dialog ───────────────────────────────────────────────

    private fun showPlaceDialog(existing: Place?) {
        val pad = dp(16)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        container.addView(TextView(this).apply {
            text = "Name"
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
        })
        val nameInput = EditText(this).apply {
            hint = "e.g. Home, Work, Truckee"
            setText(existing?.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        container.addView(nameInput)

        container.addView(TextView(this).apply {
            text = "Detection radius (meters)"
            textSize = 14f
            setPadding(0, dp(12), 0, 0)
            setTextColor(getColor(R.color.text_secondary))
        })
        val radiusInput = EditText(this).apply {
            hint = "500"
            setText(((existing?.radiusMeters ?: Place.DEFAULT_RADIUS_METERS).toInt()).toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        container.addView(radiusInput)

        container.addView(TextView(this).apply {
            text = "Set location by"
            textSize = 14f
            setPadding(0, dp(18), 0, dp(2))
            setTextColor(getColor(R.color.text_secondary))
        })

        val methodButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val mapBtn = createLocationMethodButton("Pick on map")
        val addrBtn = createLocationMethodButton("Street address")
        val coordsBtn = createLocationMethodButton("GPS coordinates")
        methodButtons.addView(mapBtn)
        methodButtons.addView(addrBtn)
        methodButtons.addView(coordsBtn)
        container.addView(methodButtons)

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New Place" else "Edit ${existing.name}")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .create()

        fun validateName(): String? {
            val name = nameInput.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter a name first", Toast.LENGTH_SHORT).show()
                return null
            }
            return name
        }

        fun readRadius(): Float =
            radiusInput.text.toString().toFloatOrNull() ?: Place.DEFAULT_RADIUS_METERS

        mapBtn.setOnClickListener {
            val name = validateName() ?: return@setOnClickListener
            pendingName = name
            pendingRadius = readRadius()
            editingId = existing?.id
            dialog.dismiss()
            launchPicker(existing)
        }

        addrBtn.setOnClickListener {
            val name = validateName() ?: return@setOnClickListener
            dialog.dismiss()
            showAddressDialog(name, readRadius(), existing?.id)
        }

        coordsBtn.setOnClickListener {
            val name = validateName() ?: return@setOnClickListener
            dialog.dismiss()
            showCoordsDialog(name, readRadius(), existing)
        }

        dialog.show()
    }

    // ── Option 1: Pick on map ───────────────────────────────────────────

    private fun createLocationMethodButton(label: String): MaterialButton =
        MaterialButton(this).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            minHeight = dp(48)
            cornerRadius = dp(6)
            backgroundTintList = ColorStateList.valueOf(getColor(R.color.primary_container))
            setTextColor(getColor(R.color.on_primary_container))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52)
            ).apply {
                topMargin = dp(8)
            }
        }

    private fun launchPicker(existing: Place?) {
        val intent = Intent(this, LocationPickerActivity::class.java).apply {
            putExtra(LocationPickerActivity.EXTRA_TITLE,
                if (existing == null) "Set location" else "Move ${existing.name}")
            if (existing != null) {
                putExtra(LocationPickerActivity.EXTRA_INITIAL_LATITUDE, existing.latitude)
                putExtra(LocationPickerActivity.EXTRA_INITIAL_LONGITUDE, existing.longitude)
            }
        }
        pickerLauncher.launch(intent)
    }

    // ── Option 2: Enter street address ──────────────────────────────────

    private fun showAddressDialog(name: String, radius: Float, existingId: String?) {
        val pad = dp(16)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        container.addView(TextView(this).apply {
            text = "Street address"
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
        })
        val addrInput = EditText(this).apply {
            hint = "e.g. 205 South Sierra Street, Reno NV"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        container.addView(addrInput)

        AlertDialog.Builder(this)
            .setTitle("Enter address for \"$name\"")
            .setView(container)
            .setPositiveButton("Look up") { _, _ ->
                val addr = addrInput.text.toString().trim()
                if (addr.isEmpty()) {
                    Toast.makeText(this, "Enter an address", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                geocodeAddress(name, addr, radius, existingId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun geocodeAddress(name: String, address: String, radius: Float, existingId: String?) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    @Suppress("DEPRECATION")
                    val geocoder = Geocoder(this@PlacesActivity, Locale.getDefault())
                    val results = geocoder.getFromLocationName(address, 1)
                    results?.firstOrNull()
                } catch (e: Exception) {
                    null
                }
            }

            if (result != null) {
                val formattedAddr = buildString {
                    result.getAddressLine(0)?.let { append(it) }
                }
                savePlace(name, result.latitude, result.longitude,
                    formattedAddr.ifBlank { address }, radius, existingId)
            } else {
                Toast.makeText(this@PlacesActivity,
                    "Could not find that address. Try a different spelling or use GPS coords.",
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Option 3: Enter GPS coordinates ─────────────────────────────────

    private fun showCoordsDialog(name: String, radius: Float, existing: Place?) {
        val pad = dp(16)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }

        container.addView(TextView(this).apply {
            text = "Latitude"
            textSize = 14f
            setTextColor(getColor(R.color.text_secondary))
        })
        val latInput = EditText(this).apply {
            hint = "e.g. 39.3280"
            setText(if (existing != null) existing.latitude.toString() else "")
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        container.addView(latInput)

        container.addView(TextView(this).apply {
            text = "Longitude"
            textSize = 14f
            setPadding(0, dp(8), 0, 0)
            setTextColor(getColor(R.color.text_secondary))
        })
        val lngInput = EditText(this).apply {
            hint = "e.g. -120.1833"
            setText(if (existing != null) existing.longitude.toString() else "")
            inputType = InputType.TYPE_CLASS_NUMBER or
                InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        container.addView(lngInput)

        AlertDialog.Builder(this)
            .setTitle("GPS coordinates for \"$name\"")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val lat = latInput.text.toString().toDoubleOrNull()
                val lng = lngInput.text.toString().toDoubleOrNull()
                if (lat == null || lng == null) {
                    Toast.makeText(this, "Enter valid latitude and longitude", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
                    Toast.makeText(this, "Coordinates out of range", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                savePlace(name, lat, lng, "", radius, existing?.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Save + delete ───────────────────────────────────────────────────

    private fun savePlace(
        name: String, lat: Double, lng: Double,
        address: String, radius: Float, existingId: String?
    ) {
        val place = Place(
            id = existingId ?: Place.newId(),
            name = name.ifBlank { "Place" },
            latitude = lat,
            longitude = lng,
            radiusMeters = radius,
            address = address
        )
        prefs.upsertPlace(place)
        renderList()
        Toast.makeText(this, "Saved ${place.name}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(place: Place) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${place.name}?")
            .setMessage("Any commute watches pointing to this place will also be removed.")
            .setPositiveButton("Delete") { _, _ ->
                prefs.deletePlace(place.id)
                renderList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }
}
