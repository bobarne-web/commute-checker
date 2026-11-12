package com.commutecheck.app.ui

import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.commutecheck.app.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

/**
 * targetSdk 35 draws edge-to-edge, including under the gesture nav bar.
 * Every phone screen uses this scaffold so the last row and bottom buttons
 * scroll fully above the navigation bar and the IME.
 */
data class ScreenScaffold(
    val screen: LinearLayout,
    val toolbar: MaterialToolbar,
    val scroll: ScrollView,
    val content: LinearLayout
)

fun AppCompatActivity.prepareEdgeToEdge() {
    enableEdgeToEdge()
}

fun AppCompatActivity.screenScaffold(
    title: String,
    showBack: Boolean
): ScreenScaffold {
    val screen = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(getColor(R.color.background))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    val toolbarHeight = dp(56)
    val toolbar = MaterialToolbar(this).apply {
        this.title = title
        setTitleTextColor(getColor(R.color.text_primary))
        setBackgroundColor(getColor(R.color.surface))
        setNavigationIconTint(getColor(R.color.text_primary))
        if (showBack) {
            navigationIcon = AppCompatResources.getDrawable(
                this@screenScaffold,
                androidx.appcompat.R.drawable.abc_ic_ab_back_material
            )
            setNavigationOnClickListener { finish() }
        }
        val initialTop = statusBarHeight()
        setPadding(0, initialTop, 0, 0)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            toolbarHeight + initialTop
        )
    }

    val pad = dp(16)
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(pad, pad, pad, pad)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    val scroll = ScrollView(this).apply {
        isFillViewport = true
        // Padding is inside the scroll range so the last row can sit above the nav bar.
        clipToPadding = true
        isVerticalScrollBarEnabled = true
        addView(content)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
    }

    screen.addView(toolbar)
    screen.addView(scroll)
    toolbar.applyStatusBarInsets(toolbarHeight)
    scroll.applyNavAndImeInsets()
    screen.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            ViewCompat.requestApplyInsets(v)
        }

        override fun onViewDetachedFromWindow(v: View) = Unit
    })
    return ScreenScaffold(screen, toolbar, scroll, content)
}

fun View.applyStatusBarInsets(toolbarHeightPx: Int) {
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val top = insets.getInsets(
            WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
        ).top
        v.setPadding(v.paddingLeft, top, v.paddingRight, v.paddingBottom)
        v.layoutParams = v.layoutParams.apply {
            height = toolbarHeightPx + top
        }
        insets
    }
}

fun View.applyNavAndImeInsets() {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        val bottom = maxOf(bars.bottom, ime.bottom)
        v.setPadding(baseLeft, baseTop, baseRight, baseBottom + bottom)
        if (ime.bottom > 0) {
            val focused = v.rootView.findFocus()
            if (focused != null) {
                v.post { scrollFocusedDescendantIntoView(v, focused) }
            }
        }
        insets
    }
    ViewCompat.requestApplyInsets(this)
}

private fun scrollFocusedDescendantIntoView(container: View, focused: View) {
    if (container !is ViewGroup) return
    val rect = Rect(0, 0, focused.width, focused.height)
    container.offsetDescendantRectToMyCoords(focused, rect)
    val extra = (16 * container.resources.displayMetrics.density).toInt()
    rect.inset(-extra, -extra)
    container.requestRectangleOnScreen(rect, true)
}

fun View.setEnabledTree(enabled: Boolean) {
    isEnabled = enabled
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).setEnabledTree(enabled)
        }
    }
}

fun AppCompatActivity.dp(value: Int): Int =
    (value * resources.displayMetrics.density).toInt()

fun AppCompatActivity.statusBarHeight(): Int {
    val id = resources.getIdentifier("status_bar_height", "dimen", "android")
    return if (id > 0) resources.getDimensionPixelSize(id) else 0
}

fun AppCompatActivity.filledButton(label: String, onClick: () -> Unit): MaterialButton =
    materialButton(
        label = label,
        backgroundColor = R.color.primary,
        textColor = R.color.on_primary,
        matchParent = true,
        onClick = onClick
    )

fun AppCompatActivity.tonalButton(label: String, onClick: () -> Unit): MaterialButton =
    materialButton(
        label = label,
        backgroundColor = R.color.primary_container,
        textColor = R.color.on_primary_container,
        matchParent = true,
        onClick = onClick
    )

fun AppCompatActivity.inlineTonalButton(label: String, onClick: () -> Unit): MaterialButton =
    materialButton(
        label = label,
        backgroundColor = R.color.surface,
        textColor = R.color.primary,
        matchParent = false,
        onClick = onClick
    ).apply {
        strokeWidth = dp(1)
        strokeColor = ColorStateList.valueOf(getColor(R.color.primary))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) }
    }

private fun AppCompatActivity.materialButton(
    label: String,
    backgroundColor: Int,
    textColor: Int,
    matchParent: Boolean,
    onClick: () -> Unit
): MaterialButton = MaterialButton(this).apply {
    text = label
    isAllCaps = false
    backgroundTintList = ColorStateList.valueOf(getColor(backgroundColor))
    setTextColor(getColor(textColor))
    cornerRadius = dp(20)
    insetTop = 0
    insetBottom = 0
    layoutParams = LinearLayout.LayoutParams(
        if (matchParent) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(8) }
    setOnClickListener { onClick() }
}
