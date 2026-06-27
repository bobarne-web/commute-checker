package com.commutecheck.app.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import com.commutecheck.app.BuildConfig
import com.commutecheck.app.R
import com.commutecheck.app.data.PreferencesManager

/**
 * App-wide settings: Google Maps API key and the traffic delay threshold used to
 * decide when a route is shown red (delayed) vs green (on time).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var apiKeyInput: EditText
    private lateinit var thresholdInput: EditText

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

        root.addView(label("Traffic delay threshold (minutes)"))
        root.addView(TextView(this).apply {
            text = "Routes delayed by more than this show in red with the extra minutes; otherwise green."
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
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getMapsApiKey())
        }
        root.addView(apiKeyInput)

        val buildKeyNote = if (BuildConfig.MAPS_API_KEY.isNotEmpty()) {
            "A build-time API key is present and used if this field is left blank."
        } else {
            "No build-time API key found — enter one here."
        }
        root.addView(TextView(this).apply {
            text = buildKeyNote
            setTextColor(getColor(R.color.text_secondary))
            setPadding(0, dp(4), 0, 0)
        })

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