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
import com.commutecheck.app.data.PreferencesManager
import com.commutecheck.app.data.RouteCheckResult
import com.commutecheck.app.domain.CommuteEngine
import com.commutecheck.app.domain.DelayMath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Auto car screen. Shows cached results immediately, then refreshes.
 * Delayed vs on-time is labeled in text (not color-only).
 */
class CommuteScreen(carContext: CarContext) : Screen(carContext) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val prefs = PreferencesManager(carContext)
    private val engine = CommuteEngine(carContext)

    private var isLoading = true
    private var isRefreshing = false
    private var statusMessage: String? = null
    private var currentPlaceName: String? = null
    private var results: List<RouteCheckResult> = emptyList()
    private var showingCached = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
        restoreCachedResults()
        runChecks()
    }

    private fun restoreCachedResults() {
        val cached = prefs.getLastResults()
        val cachedPlace = prefs.getLastResultsPlace()
        val cachedTime = prefs.getLastResultsTime()
        if (cachedTime <= 0L && cached.isEmpty()) return

        results = cached
        currentPlaceName = cachedPlace.ifEmpty { null }
        isLoading = false
        showingCached = true
        if (cached.isEmpty()) {
            statusMessage = "No watches matched last time — refreshing…"
        }
    }

    override fun onGetTemplate(): Template {
        val listBuilder = ItemList.Builder()

        if (!isLoading) {
            if (statusMessage != null && results.isEmpty()) {
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
                if (showingCached || isRefreshing) {
                    listBuilder.addItem(
                        Row.Builder()
                            .setTitle("Last saved times")
                            .addText("Refreshing live traffic…")
                            .build()
                    )
                }
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
                                isRefreshing = true
                                statusMessage = null
                                if (results.isEmpty()) {
                                    isLoading = true
                                }
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
                .addText("UNAVAILABLE")
                .build()
        }

        val statusLabel = DelayMath.statusText(result.isDelayed, result.delayMinutes)
        val color = statusCarColor(result.isDelayed)
        val title = "$statusLabel  ·  ${result.destinationName}  ·  ${result.durationInTrafficText}"

        val builder = Row.Builder().setTitle(title)

        val span = ForegroundCarColorSpan.create(color)
        val styled = android.text.SpannableString(statusLabel)
        styled.setSpan(span, 0, statusLabel.length, android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        builder.addText(CarText.create(styled))

        val glance = result.glanceLine?.takeIf { it.isNotBlank() }
        val detail = buildString {
            if (result.distanceText.isNotEmpty()) append(result.distanceText)
            if (result.summary.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("via ${result.summary}")
            }
        }
        val secondLine = when {
            glance != null && detail.isNotEmpty() -> "$glance · $detail"
            glance != null -> glance
            else -> detail
        }
        if (secondLine.isNotEmpty()) builder.addText(secondLine)

        return builder.build()
    }

    /**
     * Day and night colors stay high-contrast. The row still leads with
     * ON TIME / DELAY text so status is never color-only.
     */
    private fun statusCarColor(isDelayed: Boolean): CarColor {
        return if (isDelayed) {
            CarColor.createCustom(DAY_DELAY, NIGHT_DELAY)
        } else {
            CarColor.createCustom(DAY_ON_TIME, NIGHT_ON_TIME)
        }
    }

    private fun runChecks() {
        scope.launch {
            if (!prefs.isConfigured()) {
                statusMessage = "Open the phone app to add places and watches"
                isLoading = false
                isRefreshing = false
                showingCached = false
                invalidate()
                return@launch
            }

            when (val result = engine.runChecks()) {
                is CommuteEngine.EngineResult.Success -> {
                    currentPlaceName = result.currentPlaceName
                    results = result.results
                    statusMessage = null
                    showingCached = false
                }
                is CommuteEngine.EngineResult.Failure -> {
                    if (results.isEmpty()) {
                        statusMessage = result.message
                    }
                }
            }
            isLoading = false
            isRefreshing = false
            invalidate()
        }
    }

    companion object {
        private const val DAY_ON_TIME = 0xFF1B7A3D.toInt()
        private const val NIGHT_ON_TIME = 0xFF7DDA8A.toInt()
        private const val DAY_DELAY = 0xFFB00020.toInt()
        private const val NIGHT_DELAY = 0xFFFF8A80.toInt()
    }
}
