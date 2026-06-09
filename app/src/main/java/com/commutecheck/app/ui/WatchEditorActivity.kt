package com.commutecheck.app.ui

import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.commutecheck.app.R
import com.commutecheck.app.data.Place
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.Watch
import java.util.Calendar

/**
 * Create/edit a single commute watch: destination, active days, time window,
 * on/off toggle, and seasonal active-months.
 */
class WatchEditorActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var places: List<Place>

    private lateinit var nameInput: EditText
    private lateinit var placeSpinner: Spinner
    private lateinit var enabledSwitch: SwitchCompat
    private lateinit var alwaysCheckBox: CheckBox
    private lateinit var startTimeButton: Button
    private lateinit var endTimeButton: Button
    private lateinit var seasonGroup: RadioGroup
    private val dayToggles = mutableMapOf<Int, ToggleButton>()
    private lateinit var daysContainer: LinearLayout
    private lateinit var dayShortcutsContainer: View
    private lateinit var timeContainer: LinearLayout
    private lateinit var seasonLabel: TextView

    private var existing: Watch? = null
    private var startHour = 6
    private var startMinute = 0
    private var endHour = 9
    private var endMinute = 0
    private var customMonths: MutableSet<Int> = Watch.ALL_MONTHS.toMutableSet()
    private var suppressSeasonListener = false

    private val dayOrder = listOf(
        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    )
    private val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        places = prefs.getPlaces()

        existing = intent.getStringExtra(EXTRA_WATCH_ID)?.let { prefs.getWatch(it) }

        supportActionBar?.title = if (existing == null) "New Watch" else "Edit Watch"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        existing?.let {
            startHour = it.startHour; startMinute = it.startMinute
            endHour = it.endHour; endMinute = it.endMinute
            customMonths = it.activeMonths.toMutableSet()
        }

        setContentView(buildLayout())
        prefillSelections()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }

    private fun buildLayout(): View {
        val pad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(sectionLabel("Name"))
        nameInput = EditText(this).apply {
            hint = "e.g. To Work, To Truckee"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(existing?.name ?: "")
        }
        root.addView(nameInput)

        root.addView(sectionLabel("Destination"))
        placeSpinner = Spinner(this)
        placeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, places.map { it.name }
        )
        root.addView(placeSpinner)

        alwaysCheckBox = CheckBox(this).apply {
            text = "Always check (ignore schedule)"
            isChecked = existing?.alwaysCheck ?: false
            setPadding(0, dp(12), 0, dp(4))
            setOnCheckedChangeListener { _, isChecked -> updateScheduleFieldsEnabled(!isChecked) }
        }
        root.addView(alwaysCheckBox)

        val daysLabel = sectionLabel("Active days")
        root.addView(daysLabel)
        daysContainer = buildDayToggles() as LinearLayout
        root.addView(daysContainer)
        dayShortcutsContainer = buildDayShortcuts()
        root.addView(dayShortcutsContainer)

        val timeSectionLabel = sectionLabel("Time window")
        seasonLabel = timeSectionLabel
        root.addView(timeSectionLabel)
        timeContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val timeRow = timeContainer
        startTimeButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { pickTime(true) }
        }
        endTimeButton = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { pickTime(false) }
        }
        timeRow.addView(startTimeButton)
        timeRow.addView(TextView(this).apply {
            text = "  to  "
            gravity = Gravity.CENTER
        })
        timeRow.addView(endTimeButton)
        root.addView(timeRow)

        val seasonSectionLabel = sectionLabel("Season (active months)")
        root.addView(seasonSectionLabel)
        seasonGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        seasonGroup.addView(RadioButton(this).apply { id = R_ALL_YEAR; text = "All year" })
        seasonGroup.addView(RadioButton(this).apply { id = R_SUMMER; text = "Summer only (May–Oct)" })
        seasonGroup.addView(RadioButton(this).apply { id = R_CUSTOM; text = "Custom months…" })
        seasonGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressSeasonListener) return@setOnCheckedChangeListener
            when (checkedId) {
                R_ALL_YEAR -> customMonths = Watch.ALL_MONTHS.toMutableSet()
                R_SUMMER -> customMonths = Watch.SUMMER_MONTHS.toMutableSet()
                R_CUSTOM -> showMonthPicker()
            }
        }
        root.addView(seasonGroup)

        val enabledRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        enabledRow.addView(TextView(this).apply {
            text = "Watch enabled"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        enabledSwitch = SwitchCompat(this).apply { isChecked = existing?.enabled ?: true }
        enabledRow.addView(enabledSwitch)
        root.addView(enabledRow)

        root.addView(Button(this).apply {
            text = "Save"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
            setOnClickListener { save() }
        })

        return ScrollView(this).apply { addView(root) }
    }

    private fun buildDayToggles(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val checkedBg = getColor(R.color.primary)
        val uncheckedBg = Color.parseColor("#E0E0E0")
        dayOrder.forEachIndexed { index, day ->
            val toggle = ToggleButton(this).apply {
                textOn = dayLabels[index]
                textOff = dayLabels[index]
                text = dayLabels[index]
                isChecked = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setPadding(0, dp(8), 0, dp(8))
                updateToggleColors(this, true, checkedBg, uncheckedBg)
                setOnCheckedChangeListener { _, isChecked ->
                    updateToggleColors(this, isChecked, checkedBg, uncheckedBg)
                }
            }
            dayToggles[day] = toggle
            row.addView(toggle)
        }
        return row
    }

    private fun updateToggleColors(toggle: ToggleButton, isChecked: Boolean, checkedBg: Int, uncheckedBg: Int) {
        toggle.backgroundTintList = ColorStateList.valueOf(if (isChecked) checkedBg else uncheckedBg)
        toggle.setTextColor(if (isChecked) Color.WHITE else Color.parseColor("#333333"))
    }

    private fun updateScheduleFieldsEnabled(enabled: Boolean) {
        val alpha = if (enabled) 1.0f else 0.4f
        daysContainer.alpha = alpha
        dayShortcutsContainer.alpha = alpha
        timeContainer.alpha = alpha
        seasonGroup.alpha = alpha
        for ((_, toggle) in dayToggles) toggle.isEnabled = enabled
        startTimeButton.isEnabled = enabled
        endTimeButton.isEnabled = enabled
        for (i in 0 until seasonGroup.childCount) seasonGroup.getChildAt(i).isEnabled = enabled
    }

    private fun buildDayShortcuts(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Button(this).apply {
            text = "Every day"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { setDays(Watch.ALL_DAYS) }
        })
        row.addView(Button(this).apply {
            text = "Weekdays"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { setDays(Watch.WEEKDAYS) }
        })
        return row
    }

    private fun setDays(days: Set<Int>) {
        dayToggles.forEach { (day, toggle) -> toggle.isChecked = day in days }
    }

    private fun prefillSelections() {
        val watch = existing
        // Destination
        val destIndex = places.indexOfFirst { it.id == watch?.destinationPlaceId }
        if (destIndex >= 0) placeSpinner.setSelection(destIndex)
        // Days
        val days = watch?.enabledDays ?: Watch.ALL_DAYS
        setDays(days)
        // Season radio
        val months = watch?.activeMonths ?: Watch.ALL_MONTHS
        suppressSeasonListener = true
        seasonGroup.check(
            when (months) {
                Watch.ALL_MONTHS -> R_ALL_YEAR
                Watch.SUMMER_MONTHS -> R_SUMMER
                else -> R_CUSTOM
            }
        )
        suppressSeasonListener = false
        updateTimeButtons()
        // Always check
        if (watch?.alwaysCheck == true) {
            alwaysCheckBox.isChecked = true
            updateScheduleFieldsEnabled(false)
        }
    }

    private fun pickTime(isStart: Boolean) {
        val hour = if (isStart) startHour else endHour
        val minute = if (isStart) startMinute else endMinute
        TimePickerDialog(this, { _, h, m ->
            if (isStart) { startHour = h; startMinute = m } else { endHour = h; endMinute = m }
            updateTimeButtons()
        }, hour, minute, false).show()
    }

    private fun updateTimeButtons() {
        startTimeButton.text = format12(startHour, startMinute)
        endTimeButton.text = format12(endHour, endMinute)
    }

    private fun showMonthPicker() {
        val names = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val checked = BooleanArray(12) { (it + 1) in customMonths }
        AlertDialog.Builder(this)
            .setTitle("Active months")
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                val month = which + 1
                if (isChecked) customMonths.add(month) else customMonths.remove(month)
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun save() {
        if (places.isEmpty()) {
            Toast.makeText(this, "Add a place first", Toast.LENGTH_LONG).show()
            return
        }
        val selectedDays = dayToggles.filterValues { it.isChecked }.keys
        if (!alwaysCheckBox.isChecked && selectedDays.isEmpty()) {
            Toast.makeText(this, "Pick at least one day", Toast.LENGTH_SHORT).show()
            return
        }
        if (!alwaysCheckBox.isChecked && customMonths.isEmpty()) {
            Toast.makeText(this, "Pick at least one active month", Toast.LENGTH_SHORT).show()
            return
        }

        val destination = places[placeSpinner.selectedItemPosition]
        val name = nameInput.text.toString().trim().ifEmpty { "To ${destination.name}" }

        val isAlways = alwaysCheckBox.isChecked
        val watch = Watch(
            id = existing?.id ?: Watch.newId(),
            name = name,
            destinationPlaceId = destination.id,
            enabledDays = if (isAlways) Watch.ALL_DAYS else selectedDays,
            startHour = if (isAlways) 0 else startHour,
            startMinute = if (isAlways) 0 else startMinute,
            endHour = if (isAlways) 23 else endHour,
            endMinute = if (isAlways) 59 else endMinute,
            enabled = enabledSwitch.isChecked,
            activeMonths = if (isAlways) Watch.ALL_MONTHS else customMonths,
            alwaysCheck = isAlways
        )
        prefs.upsertWatch(watch)
        Toast.makeText(this, "Saved $name", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, dp(16), 0, dp(4))
        setTextColor(getColor(R.color.text_primary))
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_WATCH_ID = "watch_id"
        private const val R_ALL_YEAR = 1001
        private const val R_SUMMER = 1002
        private const val R_CUSTOM = 1003
    }
}
