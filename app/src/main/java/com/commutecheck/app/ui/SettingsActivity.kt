package com.commutecheck.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.commutecheck.app.R
import com.commutecheck.app.data.PreferencesManager

/**
 * App-wide settings: delay threshold and last-resort USB trigger.
 * The Maps key is compiled in; this screen does not ask for one.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var thresholdInput: EditText
    private lateinit var usbSwitch: SwitchCompat

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* banner on Main will refresh when we return */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        prepareEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)
        supportActionBar?.hide()
        setContentView(buildLayout())
    }

    private fun buildLayout(): LinearLayout {
        val scaffold = screenScaffold(title = getString(R.string.settings_title), showBack = true)
        val root = scaffold.content

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
            setTextColor(getColor(R.color.text_primary))
        }
        root.addView(thresholdInput)

        root.addView(label(getString(R.string.usb_trigger_title)))
        root.addView(TextView(this).apply {
            text = getString(R.string.usb_trigger_description)
            setTextColor(getColor(R.color.text_secondary))
        })
        val usbRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        usbRow.addView(TextView(this).apply {
            text = getString(R.string.usb_trigger_switch)
            textSize = 16f
            setTextColor(getColor(R.color.text_primary))
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

        root.addView(filledButton(getString(R.string.save)) { save() })
        return scaffold.screen
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
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun label(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, dp(16), 0, dp(4))
        setTextColor(getColor(R.color.text_primary))
    }
}
