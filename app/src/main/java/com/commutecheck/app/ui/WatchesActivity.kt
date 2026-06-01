package com.commutecheck.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.commutecheck.app.R
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.Watch
import java.util.Calendar

/**
 * Lists commute watches. Each watch checks travel time to a destination when the
 * car starts on its active days/time/season.
 */
class WatchesActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        supportActionBar?.title = "Commute Watches"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(Button(this).apply {
            text = "Add Watch"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { addWatch() }
        })

        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(listContainer)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        renderList()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun addWatch() {
        if (prefs.getPlaces().isEmpty()) {
            Toast.makeText(this, "Add a place first, then create a watch for it", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, WatchEditorActivity::class.java))
    }

    private fun renderList() {
        listContainer.removeAllViews()
        val watches = prefs.getWatches()
        if (watches.isEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = "No watches yet. Add one for \"To Work\", \"To Home\", \"To Truckee\", etc."
                setPadding(0, dp(16), 0, 0)
                setTextColor(getColor(R.color.text_secondary))
            })
            return
        }

        watches.forEach { watch ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(12) }
                setBackgroundColor(getColor(R.color.primary_container))
            }
            card.addView(TextView(this).apply {
                text = if (watch.enabled) watch.name else "${watch.name} (off)"
                textSize = 18f
                setTextColor(getColor(R.color.on_primary_container))
            })
            card.addView(TextView(this).apply {
                text = describeWatch(watch)
                setTextColor(getColor(R.color.on_primary_container))
            })

            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }
            buttons.addView(Button(this).apply {
                text = "Edit"
                setOnClickListener {
                    startActivity(Intent(this@WatchesActivity, WatchEditorActivity::class.java).apply {
                        putExtra(WatchEditorActivity.EXTRA_WATCH_ID, watch.id)
                    })
                }
            })
            buttons.addView(Button(this).apply {
                text = "Delete"
                setOnClickListener { confirmDelete(watch) }
            })
            card.addView(buttons)
            listContainer.addView(card)
        }
    }

    private fun describeWatch(watch: Watch): String {
        val dest = prefs.getPlace(watch.destinationPlaceId)?.name ?: "(deleted place)"
        return buildString {
            append("→ $dest\n")
            append(daysSummary(watch.enabledDays))
            append("  ·  ")
            append(timeWindow(watch))
            append("\n")
            append(seasonSummary(watch.activeMonths))
        }
    }

    private fun daysSummary(days: Set<Int>): String {
        if (days.size == 7) return "Every day"
        if (days == Watch.WEEKDAYS) return "Weekdays"
        val names = mapOf(
            Calendar.SUNDAY to "Sun", Calendar.MONDAY to "Mon", Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed", Calendar.THURSDAY to "Thu", Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat"
        )
        return days.sorted().mapNotNull { names[it] }.joinToString(", ")
    }

    private fun timeWindow(watch: Watch): String =
        "${format12(watch.startHour, watch.startMinute)}–${format12(watch.endHour, watch.endMinute)}"

    private fun seasonSummary(months: Set<Int>): String {
        if (months.size == 12) return "All year"
        if (months == Watch.SUMMER_MONTHS) return "Summer (May–Oct)"
        val names = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        return "Months: " + months.sorted().joinToString(", ") { names[it] }
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

    private fun confirmDelete(watch: Watch) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${watch.name}?")
            .setPositiveButton("Delete") { _, _ ->
                prefs.deleteWatch(watch.id)
                renderList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
