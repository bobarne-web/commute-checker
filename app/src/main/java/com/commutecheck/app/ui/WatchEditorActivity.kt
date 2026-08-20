package com.commutecheck.app.ui

import android.app.TimePickerDialog
import android.content.res.ColorStateList
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
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
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
    private var customDays: MutableSet<Int> = Watch.ALL_DAYS.toMutableSet()
    private var customMonths: MutableSet<Int> = Watch.ALL_MONTHS.toMutableSet()
    private var suppressSeasonListener = false

    private val dayOrder = listOf(
        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    )
    private val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        prefs = PreferencesManager(this)
        places = prefs.getPlaces()

        existing = intent.getStringExtra(EXTRA_WATCH_ID)?.let { prefs.getWatch(it) }

        supportActionBar?.hide()

        existing?.let {
            startHour = it.startHour; startMinute = it.startMinute
            endHour = it.endHour; endMinute = it.endMinute
            customDays = it.enabledDays.toMutableSet()
            customMonths = it.activeMonths.toMutableSet()
        }

        setContentView(buildLayout())
        prefillSelections()
    }

    private fun buildLayout(): View {
        val pad = dp(16)
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
        }

        val title = if (existing == null) "New Watch" else "Edit Watch"
        screen.addView(MaterialToolbar(this).apply {
            this.title = title
            navigationIcon = AppCompatResources.getDrawable(
                this@WatchEditorActivity,
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
            setTextColor(getColor(R.color.text_primary))
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
            setTextColor(getColor(R.color.text_primary))
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

        screen.addView(ScrollView(this).apply { addView(root) })
        return screen
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
                minWidth = 0
                layoutParams = LinearLayout.LayoutParams(
                    dp(48), dp(40)
                ).apply { rightMargin = dp(4) }
                setOnCheckedChangeListener { button, isChecked ->
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
            "Weekends" to listOf(Calendar.SATURDAY, Calendar.SUNDAY)
        )
        shortcuts.forEach { (label, days) ->
            val btn = Button(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { rightMargin = dp(8) }
                setOnClickListener {
                    customDays.clear()
                    customDays.addAll(days)
                    updateDayToggles()
                }
            }
            container.addView(btn)
        }
        return container
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
        if (checked) {
            toggle.backgroundTintList = ColorStateList.valueOf(getColor(R.color.primary))
            toggle.setTextColor(getColor(R.color.on_primary))
        } else {
            toggle.backgroundTintList = ColorStateList.valueOf(getColor(R.color.day_toggle_off))
            toggle.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun pickTime(isStart: Boolean) {
        val (hour, minute) = if (isStart) startHour to startMinute else endHour to endMinute
        TimePickerDialog(this, { _, h, m ->
            if (isStart) { startHour = h; startMinute = m } else { endHour = h; endMinute = m }
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
                updateDayToggles()
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
        daysContainer.isEnabled = enabled
        dayShortcutsContainer.isEnabled = enabled
        timeContainer.isEnabled = enabled
        seasonGroup.isEnabled = enabled
        seasonLabel.isEnabled = enabled
        val alpha = if (enabled) 1f else 0.5f
        daysContainer.alpha = alpha
        dayShortcutsContainer.alpha = alpha
        timeContainer.alpha = alpha
        seasonGroup.alpha = alpha
        seasonLabel.alpha = alpha
    }

    private fun prefillSelections() {
        updateDayToggles()
        updateTimeButtons()
        existing?.let {
            placeSpinner.setSelection(places.indexOfFirst { place -> place.id == it.destinationPlaceId })
            alwaysCheckBox.isChecked = it.alwaysCheck
            updateScheduleFieldsEnabled(!it.alwaysCheck)
            when (it.activeMonths) {
                Watch.ALL_MONTHS -> seasonGroup.check(R_ALL_YEAR)
                Watch.SUMMER_MONTHS -> seasonGroup.check(R_SUMMER)
                else -> {
                    seasonGroup.check(R_CUSTOM)
                    customMonths = it.activeMonths.toMutableSet()
                }
            }
        }
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show()
            return
        }

        val place = places[placeSpinner.selectedItemPosition]
        val watch = Watch(
            id = existing?.id ?: Watch.newId(),
            name = name,
            destinationPlaceId = place.id,
            enabledDays = if (alwaysCheckBox.isChecked) Watch.ALL_DAYS else customDays,
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            enabled = enabledSwitch.isChecked,
            activeMonths = if (alwaysCheckBox.isChecked) Watch.ALL_MONTHS else customMonths,
            alwaysCheck = alwaysCheckBox.isChecked
        )

        prefs.upsertWatch(watch)
        finish()
    }

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_WATCH_ID = "watch_id"
        private const val R_ALL_YEAR = 1001
        private const val R_SUMMER = 1002
        private const val R_CUSTOM = 1003
    }
}