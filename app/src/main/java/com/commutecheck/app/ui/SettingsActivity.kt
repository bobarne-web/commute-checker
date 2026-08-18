package com.commutecheck.app.ui

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.commutecheck.app.BuildConfig
import com.commutecheck.app.R
import com.commutecheck.app.data.PreferencesManager

/**
 * App-wide settings: Google Maps API key, delay threshold, and last-resort USB trigger.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var apiKeyInput: EditText
    private lateinit var thresholdInput: EditText
    private lateinit var usbSwitch: SwitchCompat

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* banner on Main will refresh when we return */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        supportActionBar?.hide()

        setContentView(buildLayout())
    }

    private fun buildLayout(): View {
        val pad = dp(16)
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
        }

        screen.addView(MaterialToolbar(this).apply {
            title = getString(R.string.settings_title)
            navigationIcon = AppCompatResources.getDrawable(
                this@SettingsActivity,
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

        root.addView(label(getString(R.string.delay_threshold_title)))
        root.addView(TextView(this).apply {
            text = getString(
                R.string.delay_threshold_description,
                PreferencesManager.DEFAULT_DELAY_THRESHOLD_MIN
            )
            setTextColor(getColor(R.color.text_secondary))
        })
        thresholdInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.getDelayThresholdMinutes().toString())
        }
        root.addView(thresholdInput)

        root.addView(label(getString(R.string.api_key_section)))
        root.addView(TextView(this).apply {
            text = getString(R.string.api_key_description)
            setTextColor(getColor(R.color.text_secondary))
        })
        apiKeyInput = EditText(this).apply {
            hint = getString(R.string.api_key_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getMapsApiKey())
        }
        root.addView(apiKeyInput)

        val buildKeyNote = if (BuildConfig.MAPS_API_KEY.isNotEmpty()) {
            getString(R.string.api_key_build_present)
        } else {
            getString(R.string.api_key_build_missing)
        }
        root.addView(TextView(this).apply {
            text = buildKeyNote
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(4), 0, 0)
        })

        root.addView(label(getString(R.string.usb_trigger_title)))
        root.addView(TextView(this).apply {
            text = getString(R.string.usb_trigger_description)
            setTextColor(getColor(R.color.text_secondary))
        })
        val usbRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        usbRow.addView(TextView(this).apply {
            text = getString(R.string.usb_trigger_switch)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        usbSwitch = SwitchCompat(this).apply {
            isChecked = prefs.isUsbTriggerEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                prefs.setUsbTriggerEnabled(isChecked)
                if (isChecked) maybeExplainBackgroundLocationForUsb()
            }
        }
        usbRow.addView(usbSwitch)
        root.addView(usbRow)

        root.addView(Button(this).apply {
            text = getString(R.string.save)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(24) }
            setOnClickListener { save() }
        })

        screen.addView(ScrollView(this).apply { addView(root) })
        return screen
    }

    private fun maybeExplainBackgroundLocationForUsb() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.background_location_title))
            .setMessage(getString(R.string.background_location_message_usb))
            .setPositiveButton(getString(R.string.background_location_allow)) { _, _ ->
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save() {
        val threshold = thresholdInput.text.toString().toIntOrNull()
            ?: PreferencesManager.DEFAULT_DELAY_THRESHOLD_MIN
        prefs.setDelayThresholdMinutes(threshold.coerceAtLeast(0))
        prefs.saveMapsApiKey(apiKeyInput.text.toString().trim())
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, dp(16), 0, dp(4))
        setTextColor(getColor(R.color.text_primary))
    }

    private fun statusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
