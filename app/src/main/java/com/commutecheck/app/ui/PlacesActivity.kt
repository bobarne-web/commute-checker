package com.commutecheck.app.ui

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
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
import com.commutecheck.app.R
import com.commutecheck.app.data.Place
import com.commutecheck.app.data.PreferencesManager

/**
 * Lets the user manage saved places (Home, Work, Truckee, Reno, …). Each place has
 * a name, coordinates (picked on a map), and a detection radius.
 */
class PlacesActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var listContainer: LinearLayout

    // Holds the pending name/radius while the map picker is open.
    private var pendingName: String = ""
    private var pendingRadius: Float = Place.DEFAULT_RADIUS_METERS
    private var editingId: String? = null

    private val pickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val name = data.getStringExtra(LocationPickerActivity.EXTRA_LOCATION_NAME)
                ?.ifBlank { pendingName } ?: pendingName
            val lat = data.getDoubleExtra(LocationPickerActivity.EXTRA_LATITUDE, 0.0)
            val lng = data.getDoubleExtra(LocationPickerActivity.EXTRA_LONGITUDE, 0.0)
            val place = Place(
                id = editingId ?: Place.newId(),
                name = name.ifBlank { "Place" },
                latitude = lat,
                longitude = lng,
                radiusMeters = pendingRadius
            )
            prefs.upsertPlace(place)
            renderList()
            Toast.makeText(this, "Saved ${place.name}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        supportActionBar?.title = "Places"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(Button(this).apply {
            text = "Add Place"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { showNameRadiusDialog(null) }
        })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        setContentView(ScrollView(this).apply { addView(root) })
        renderList()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

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
            card.addView(TextView(this).apply {
                text = String.format(
                    "%.4f, %.4f · radius %dm",
                    place.latitude, place.longitude, place.radiusMeters.toInt()
                )
                setTextColor(getColor(R.color.on_primary_container))
            })

            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            buttons.addView(Button(this).apply {
                text = "Edit"
                setOnClickListener { showNameRadiusDialog(place) }
            })
            buttons.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener { confirmDelete(place) }
            })
            card.addView(buttons)
            listContainer.addView(card)
        }
    }

    private fun showNameRadiusDialog(existing: Place?) {
        val pad = dp(16)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val nameInput = EditText(this).apply {
            hint = "Name (e.g. Truckee)"
            setText(existing?.name ?: "")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        val radiusInput = EditText(this).apply {
            hint = "Detection radius (meters)"
            setText(((existing?.radiusMeters ?: Place.DEFAULT_RADIUS_METERS).toInt()).toString())
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        container.addView(nameInput)
        container.addView(radiusInput)

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "New Place" else "Edit ${existing.name}")
            .setView(container)
            .setPositiveButton("Pick on map") { _, _ ->
                pendingName = nameInput.text.toString().trim()
                pendingRadius = radiusInput.text.toString().toFloatOrNull()
                    ?: Place.DEFAULT_RADIUS_METERS
                editingId = existing?.id
                launchPicker(existing, pendingName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchPicker(existing: Place?, name: String) {
        val intent = Intent(this, LocationPickerActivity::class.java).apply {
            putExtra(LocationPickerActivity.EXTRA_TITLE, if (existing == null) "Set location" else "Move ${existing.name}")
            putExtra(LocationPickerActivity.EXTRA_INITIAL_NAME, name)
        }
        pickerLauncher.launch(intent)
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
}
