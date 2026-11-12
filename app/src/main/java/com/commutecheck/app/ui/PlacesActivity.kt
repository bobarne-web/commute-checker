package com.commutecheck.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.commutecheck.app.R
import com.commutecheck.app.data.Place
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.domain.PlaceText

/**
 * Saved places. Coordinates stay off this list; each row shows the address
 * and a friendly radius such as "within 500 m".
 */
class PlacesActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        supportActionBar?.hide()

        val scaffold = screenScaffold(title = "Places", showBack = true)
        scaffold.content.addView(filledButton("Add Place") {
            startActivity(Intent(this, PlaceEditorActivity::class.java))
        })
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scaffold.content.addView(listContainer)
        setContentView(scaffold.screen)
    }

    override fun onResume() {
        super.onResume()
        renderList()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
                setBackgroundColor(getColor(R.color.primary_container))
            }
            card.addView(TextView(this).apply {
                text = place.name
                textSize = 18f
                setTextColor(getColor(R.color.on_primary_container))
            })
            card.addView(TextView(this).apply {
                text = placeSubtitle(place)
                setPadding(0, dp(4), 0, 0)
                setTextColor(getColor(R.color.on_primary_container))
            })

            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            buttons.addView(inlineTonalButton("Edit") {
                startActivity(Intent(this, PlaceEditorActivity::class.java).apply {
                    putExtra(PlaceEditorActivity.EXTRA_PLACE_ID, place.id)
                })
            })
            buttons.addView(inlineTonalButton("Delete") {
                confirmDelete(place)
            })
            card.addView(buttons)
            listContainer.addView(card)
        }
    }

    private fun placeSubtitle(place: Place): String {
        val radius = PlaceText.radiusPhrase(place.radiusMeters)
        return if (place.address.isNotBlank()) {
            "${place.address}\n$radius"
        } else {
            radius
        }
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
}
