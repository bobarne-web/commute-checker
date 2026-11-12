package com.commutecheck.app.ui

import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
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
import com.google.android.material.button.MaterialButton
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
    private lateinit var startTimeButton: MaterialButton
    private lateinit var endTimeButton: MaterialButton
    private lateinit var seasonGroup: RadioGroup
    private lateinit var scheduleBlock: LinearLayout
    private val dayToggles = mutableMapOf<Int, ToggleButton>()

    private var existing: Watch? = null
    private var startHour = 6
    private var startMinute = 0
    private var endHour = 9
    private var endMinute = 0
    private var customDays: MutableSet<Int> = Watch.ALL_DAYS.toMutableSet()
    private var customMonths: MutableSet<Int> = Watch.ALL_MONTHS.toMutableSet()
    private var suppressSeasonListener = false

    private val dayOrder = listOf(
        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    )
    private val dayLabels = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        places = prefs.getPlaces()

        existing = intent.getStringExtra(EXTRA_WATCH_ID)?.let { prefs.getWatch(it) }
        supportActionBar?.hide()

        existing?.let {
            startHour = it.startHour
            startMinute = it.startMinute
            endHour = it.endHour
            endMinute = it.endMinute
            customDays = it.enabledDays.toMutableSet()
            customMonths = it.activeMonths.toMutableSet()
        }

        setContentView(buildLayout())
        prefillSelections()
    }

    private fun buildLayout(): View {
        val title = if (existing == null) "New Watch" else "Edit Watch"
        val scaffold = screenScaffold(title = title, showBack = true)
        val root = scaffold.content

        root.addView(sectionLabel("Name"))
        nameInput = EditText(this).apply {
            hint = "e.g. To Work, To Truckee"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(existing?.name ?: "")
            setTextColor(getColor(R.color.text_primary))
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
            setTextColor(getColor(R.color.text_primary))
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(alwaysCheckBox)

        scheduleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scheduleBlock.addView(sectionLabel("Active days"))
        scheduleBlock.addView(buildDayToggles())
        scheduleBlock.addView(buildDayShortcuts())
        scheduleBlock.addView(sectionLabel("Time window"))
        scheduleBlock.addView(buildTimeRow())
        scheduleBlock.addView(sectionLabel("Season (active months)"))
        seasonGroup = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        seasonGroup.addView(RadioButton(this).apply {
            id = R_ALL_YEAR
            text = "All year"
            setTextColor(getColor(R.color.text_primary))
        })
        seasonGroup.addView(RadioButton(this).apply {
            id = R_SUMMER
            text = "Summer only (May–Oct)"
            setTextColor(getColor(R.color.text_primary))
        })
        seasonGroup.addView(RadioButton(this).apply {
            id = R_CUSTOM
            text = "Custom months…"
            setTextColor(getColor(R.color.text_primary))
        })
        seasonGroup.setOnCheckedChangeListener { _, checkedId ->
            if (suppressSeasonListener) return@setOnCheckedChangeListener
            when (checkedId) {
                R_ALL_YEAR -> customMonths = Watch.ALL_MONTHS.toMutableSet()
                R_SUMMER -> customMonths = Watch.SUMMER_MONTHS.toMutableSet()
                R_CUSTOM -> showMonthPicker()
            }
        }
        scheduleBlock.addView(seasonGroup)
        root.addView(scheduleBlock)

        alwaysCheckBox.setOnCheckedChangeListener { _, isChecked ->
            updateScheduleFieldsEnabled(!isChecked)
        }

        val enabledRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        enabledRow.addView(TextView(this).apply {
            text = "Watch enabled"
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        enabledSwitch = SwitchCompat(this).apply { isChecked = existing?.enabled ?: true }
        enabledRow.addView(enabledSwitch)
        root.addView(enabledRow)

        root.addView(filledButton("Save") { save() })
        return scaffold.screen
    }

    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, dp(16), 0, dp(4))
        setTextColor(getColor(R.color.text_primary))
    }

    private fun buildDayToggles(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(8))
        }
        dayOrder.forEachIndexed { index, day ->
            val toggle = ToggleButton(this).apply {
                textOn = dayLabels[index]
                textOff = dayLabels[index]
                text = dayLabels[index]
                textSize = 12f
                isAllCaps = false
                transformationMethod = null
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                setPadding(0, 0, 0, 0)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (index < dayOrder.lastIndex) marginEnd = dp(4)
                }
                setOnCheckedChangeListener { button, isChecked ->
                    if (!button.isEnabled) return@setOnCheckedChangeListener
                    if (isChecked) customDays.add(day) else customDays.remove(day)
                    styleDayToggle(button as ToggleButton, isChecked)
                }
            }
            styleDayToggle(toggle, false)
            dayToggles[day] = toggle
            container.addView(toggle)
        }
        return container
    }

    private fun buildDayShortcuts(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        }
        val shortcuts = listOf(
            "All" to Watch.ALL_DAYS,
            "Weekdays" to Watch.WEEKDAYS,
            "Weekends" to setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
        shortcuts.forEach { (label, days) ->
            container.addView(inlineTonalButton(label) {
                if (!scheduleBlock.isEnabled) return@inlineTonalButton
                customDays.clear()
                customDays.addAll(days)
                updateDayToggles()
            })
        }
        return container
    }

    private fun buildTimeRow(): LinearLayout {
        val timeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startTimeButton = timeButton { pickTime(true) }
        endTimeButton = timeButton { pickTime(false) }
        timeRow.addView(startTimeButton)
        timeRow.addView(TextView(this).apply {
            text = "  to  "
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_primary))
        })
        timeRow.addView(endTimeButton)
        return timeRow
    }

    private fun timeButton(onClick: () -> Unit): MaterialButton = MaterialButton(this).apply {
        isAllCaps = false
        backgroundTintList = ColorStateList.valueOf(getColor(R.color.primary_container))
        setTextColor(getColor(R.color.on_primary_container))
        cornerRadius = dp(12)
        insetTop = 0
        insetBottom = 0
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { onClick() }
    }

    private fun updateDayToggles() {
        dayOrder.forEach { day ->
            val checked = customDays.contains(day)
            dayToggles[day]?.let { toggle ->
                if (toggle.isChecked != checked) {
                    toggle.isChecked = checked
                } else {
                    styleDayToggle(toggle, checked)
                }
            }
        }
    }

    private fun styleDayToggle(toggle: ToggleButton, checked: Boolean) {
        toggle.isAllCaps = false
        toggle.transformationMethod = null
        if (checked) {
            toggle.backgroundTintList = ColorStateList.valueOf(getColor(R.color.primary))
            toggle.setTextColor(getColor(R.color.on_primary))
        } else {
            toggle.backgroundTintList = ColorStateList.valueOf(getColor(R.color.day_toggle_off))
            toggle.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun pickTime(isStart: Boolean) {
        if (!scheduleBlock.isEnabled) return
        val (hour, minute) = if (isStart) startHour to startMinute else endHour to endMinute
        TimePickerDialog(this, { _, h, m ->
            if (isStart) {
                startHour = h
                startMinute = m
            } else {
                endHour = h
                endMinute = m
            }
            updateTimeButtons()
        }, hour, minute, false).show()
    }

    private fun updateTimeButtons() {
        startTimeButton.text = formatTime(startHour, startMinute)
        endTimeButton.text = formatTime(endHour, endMinute)
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val amPm = if (hour >= 12) "PM" else "AM"
        var h = hour % 12
        if (h == 0) h = 12
        return String.format("%d:%02d %s", h, minute, amPm)
    }

    private fun showMonthPicker() {
        suppressSeasonListener = true
        val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val checked = months.mapIndexed { index, _ -> customMonths.contains(index + 1) }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle("Select active months")
            .setMultiChoiceItems(months.toTypedArray(), checked) { _, which, isChecked ->
                if (isChecked) customMonths.add(which + 1) else customMonths.remove(which + 1)
            }
            .setPositiveButton("OK") { _, _ ->
                suppressSeasonListener = false
                seasonGroup.check(R_CUSTOM)
            }
            .setNegativeButton("Cancel") { _, _ ->
                suppressSeasonListener = false
                seasonGroup.check(if (customMonths == Watch.SUMMER_MONTHS) R_SUMMER else R_ALL_YEAR)
            }
            .show()
    }

    private fun updateScheduleFieldsEnabled(enabled: Boolean) {
        scheduleBlock.setEnabledTree(enabled)
        scheduleBlock.alpha = if (enabled) 1f else 0.5f
    }

    private fun prefillSelections() {
        updateDayToggles()
        updateTimeButtons()
        suppressSeasonListener = true
        existing?.let {
            val index = places.indexOfFirst { place -> place.id == it.destinationPlaceId }
            if (index >= 0) placeSpinner.setSelection(index)
            when (it.activeMonths) {
                Watch.ALL_MONTHS -> seasonGroup.check(R_ALL_YEAR)
                Watch.SUMMER_MONTHS -> seasonGroup.check(R_SUMMER)
                else -> {
                    seasonGroup.check(R_CUSTOM)
                    customMonths = it.activeMonths.toMutableSet()
                }
            }
            alwaysCheckBox.isChecked = it.alwaysCheck
        } ?: seasonGroup.check(R_ALL_YEAR)
        suppressSeasonListener = false
        updateScheduleFieldsEnabled(!alwaysCheckBox.isChecked)
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
            return
        }
        if (places.isEmpty()) {
            Toast.makeText(this, "Add a place first", Toast.LENGTH_SHORT).show()
            return
        }

        val place = places[placeSpinner.selectedItemPosition]
        val watch = Watch(
            id = existing?.id ?: Watch.newId(),
            name = name,
            destinationPlaceId = place.id,
            enabledDays = customDays,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            enabled = enabledSwitch.isChecked,
            activeMonths = customMonths,
            alwaysCheck = alwaysCheckBox.isChecked
        )

        prefs.upsertWatch(watch)
        finish()
    }

    companion object {
        const val EXTRA_WATCH_ID = "watch_id"
        private const val R_ALL_YEAR = 1001
        private const val R_SUMMER = 1002
        private const val R_CUSTOM = 1003
    }
}
