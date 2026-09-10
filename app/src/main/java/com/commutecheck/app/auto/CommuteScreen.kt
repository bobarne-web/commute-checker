package com.commutecheck.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarText
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import android.graphics.Color
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.RouteCheckResult
import com.commutecheck.app.domain.CommuteEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Auto car screen. Shows a color-coded dashboard of all relevant commute
 * times for the current location/time.
 */
class CommuteScreen(carContext: CarContext) : Screen(carContext) {

    private val normalTravelTimeColor = CarColor.createCustom(Color.WHITE, Color.WHITE)
    private val abnormalTravelTimeColor = CarColor.createCustom(
        Color.rgb(255, 64, 129),
        Color.rgb(255, 64, 129)
    )
    private val fasterRouteColor = CarColor.createCustom(
        Color.rgb(105, 240, 174),
        Color.rgb(105, 240, 174)
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = PreferencesManager(carContext)
    private val engine = CommuteEngine(carContext)

    private var isLoading = true
    private var statusMessage: String? = null
    private var currentPlaceName: String? = null
    private var results: List<RouteCheckResult> = emptyList()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        runChecks()
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (!isLoading) {
            if (statusMessage != null) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("Commute Checker")
                        .addText(statusMessage!!)
                        .build()
                )
            } else if (results.isEmpty()) {
                listBuilder.addItem(
                    Row.Builder()
                        .setTitle("No checks right now")
                        .addText("No watches are scheduled for this time/location")
                        .build()
                )
            } else {
                results.forEach { result -> listBuilder.addItem(buildRow(result)) }
            }
        }

        val title = currentPlaceName?.let { "Commute from $it" } ?: "Commute Checker"

        val templateBuilder = ListTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(
                androidx.car.app.model.ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Refresh")
                            .setOnClickListener {
                                isLoading = true
                                statusMessage = null
                                results = emptyList()
                                invalidate()
                                runChecks()
                            }
                            .build()
                    )
                    .build()
            )

        if (isLoading) {
            templateBuilder.setLoading(true)
        } else {
            templateBuilder.setSingleList(listBuilder.build())
        }

        return templateBuilder.build()
    }

    private fun buildRow(result: RouteCheckResult): Row {
        if (result.hasError) {
            return Row.Builder()
                .setTitle(result.destinationName)
                .addText("Unavailable")
                .build()
        }

        val color = if (result.isDelayed) abnormalTravelTimeColor else normalTravelTimeColor
        val statusLabel = if (result.isDelayed) {
            "DELAY  +${result.delayMinutes} min"
        } else {
            "ON TIME"
        }

        val builder = Row.Builder().setTitle(
            "${result.destinationName}  ${result.durationInTrafficText}"
        )

        // Keep normal travel neutral and make abnormal delays pop on the car screen.
        val span = ForegroundCarColorSpan.create(color)
        val styled = android.text.SpannableString(statusLabel)
        styled.setSpan(span, 0, statusLabel.length, android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        builder.addText(CarText.create(styled))

        val detail = buildString {
            if (result.distanceText.isNotEmpty()) append(result.distanceText)
            if (result.summary.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("via ${result.summary}")
            }
        }
        if (detail.isNotEmpty()) builder.addText(detail)

        if (result.hasFasterAlternative) {
            val altLabel = "Faster: ${result.fasterRouteDurationText} via " +
                "${result.fasterRouteSummary ?: "alternate"} (−${result.fasterRouteSavedMinutes} min)"
            val altSpan = ForegroundCarColorSpan.create(fasterRouteColor)
            val altStyled = android.text.SpannableString(altLabel)
            altStyled.setSpan(
                altSpan, 0, altLabel.length, android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
            builder.addText(CarText.create(altStyled))
        }

        return builder.build()
    }

    private fun runChecks() {
        scope.launch {
            if (!prefs.isConfigured()) {
                statusMessage = "Open the phone app to add places and watches"
                isLoading = false
                invalidate()
                return@launch
            }

            when (val result = engine.runChecks()) {
                is CommuteEngine.EngineResult.Success -> {
                    currentPlaceName = result.currentPlaceName
                    results = result.results
                    statusMessage = null
                }
                is CommuteEngine.EngineResult.Failure -> {
                    statusMessage = result.message
                }
            }
            isLoading = false
            invalidate()
        }
    }
}
