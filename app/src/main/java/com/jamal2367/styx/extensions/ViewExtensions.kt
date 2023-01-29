/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.extensions

import android.annotation.SuppressLint
import android.graphics.*
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.databinding.BindingAdapter
import androidx.palette.graphics.Palette
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.jamal2367.styx.utils.getFilteredColor
import java.lang.reflect.Method

/**
 * Tells if this view can scroll vertically.
 * This view may still contain children who can scroll.
 */
fun View.canScrollVertically() = this.let {
    it.canScrollVertically(-1) || it.canScrollVertically(1)
}

/**
 * Removes a view from its parent if it has one.
 */
@Suppress("IMPLICIT_NOTHING_TYPE_ARGUMENT_AGAINST_NOT_NOTHING_EXPECTED_TYPE")
fun View?.removeFromParent(): ViewGroup? = this?.let {
    val parent = it.parent
    (parent as? ViewGroup)?.let { vg ->
        vg.removeView(it)
        return vg
    }
    // Assuming you don't need to explicitly return null in Kotlin
}

/**
 * Performs an action when the view is laid out.
 *
 * @param runnable the runnable to run when the view is laid out.
 */
inline fun View?.doOnLayout(crossinline runnable: () -> Unit) = this?.let {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            runnable()
        }
    })
}


/**
 * Performs an action whenever this view is loosing focus.
 *
 * @param runnable the runnable to run.
 */
inline fun View?.onFocusLost(crossinline runnable: () -> Unit) = this?.let {
    it.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
        if (!hasFocus) {
            runnable()
        }
    }
}

/**
 * Performs an action whenever this view is loosing focus.
 *
 * @param runnable the runnable to run.
 */
inline fun View?.onFocusGained(crossinline runnable: () -> Unit) = this?.let {
    it.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            runnable()
        }
    }
}

/**
 * Performs an action whenever view layout is changing.
 *
 * @param runnable the runnable to run.
 */
inline fun View?.onLayoutChange(crossinline runnable: () -> Unit) = this?.apply {
    addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> runnable() }
}

/**
 * Performs an action whenever view layout size is changing.
 *
 * @param runnable the runnable to run.
 */
inline fun View?.onSizeChange(crossinline runnable: () -> Unit) = this?.apply {
    addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val rect = Rect(left, top, right, bottom)
        val oldRect = Rect(oldLeft, oldTop, oldRight, oldBottom)
        if (rect.width() != oldRect.width() || rect.height() != oldRect.height()) {
            runnable()
        }
    }
}


/**
 * Performs an action once next time a recycler view goes idle.
 *
 * @param runnable the runnable to run.
 */
inline fun RecyclerView?.onceOnScrollStateIdle(crossinline runnable: () -> Unit) = this?.apply {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                runnable(); removeOnScrollListener(this)
            }
        }
    })
}

/**
 * Reset Swipe Refresh Layout target.
 * This is needed if you are changing the child scrollable view during the lifetime of your layout.
 * So whenever we change tab we need to do that.
 */
fun SwipeRefreshLayout?.resetTarget() {
    // Get that mTarget private data member and set it as accessible
    val field = SwipeRefreshLayout::class.java.getDeclaredField("mTarget")
    field.isAccessible = true
    // Then reset it
    field.set(this, null)
    // Next time this is doing a layout ensureTarget() will be called and the target set properly again
}


/**
 * Analyse the given bitmap and apply a filter if it is too dark for the given theme before loading it in this ImageView
 * Basically turns icons which are too dark for dark theme to white.
 */
fun ImageView.setImageForTheme(bitmap: Bitmap, isDarkTheme: Boolean) {
    // Remove any existing filter
    clearColorFilter()

    if (isDarkTheme) {
        Palette.from(bitmap).generate { palette ->
            // OR with opaque black to remove transparency glitches
            val filteredColor =
                Color.BLACK or getFilteredColor(bitmap) // OR with opaque black to remove transparency glitches
            val filteredLuminance = ColorUtils.calculateLuminance(filteredColor)
            //val color = Color.BLACK or (it.getVibrantColor(it.getLightVibrantColor(it.getDominantColor(Color.BLACK))))
            val color = Color.BLACK or (palette?.getDominantColor(Color.BLACK) ?: Color.BLACK)
            val luminance = ColorUtils.calculateLuminance(color)
            // Lowered threshold from 0.025 to 0.02 for it to work with bbc.com/future
            // At 0.015 it does not kick in for GitHub
            val threshold = 0.02
            // Use white filter on darkest favicons
            // Filtered luminance  works well enough for theregister.co.uk and github.com while not impacting bbc.c.uk
            // Luminance from dominant color was added to prevent toytowngermany.com from being filtered
            if (luminance < threshold && filteredLuminance < threshold
                // Needed to exclude white favicon variant provided by GitHub dark web theme
                && palette?.dominantSwatch != null
            ) {
                // Mostly black icon
                //setColorFilter(Color.WHITE)
                // Invert its colors
                // See: https://stackoverflow.com/a/17871384/3969362
                val matrix = floatArrayOf(-1.0f,
                    0f,
                    0f,
                    0f,
                    255f,
                    0f,
                    -1.0f,
                    0f,
                    0f,
                    255f,
                    0f,
                    0f,
                    -1.0f,
                    0f,
                    255f,
                    0f,
                    0f,
                    0f,
                    1.0f,
                    0f)
                colorFilter = ColorMatrixColorFilter(matrix)
            }
        }
    }

    setImageBitmap(bitmap)
}

/**
 * To be able to have tooltips working before API level 26
 * See: https://stackoverflow.com/a/61873888
 */
@BindingAdapter("tooltipText")
fun View.bindTooltipText(tooltipText: String) {
    TooltipCompat.setTooltipText(this, tooltipText)
}

/**
 * Crazy workaround to get the virtual keyboard to show, Android FFS
 * See: https://stackoverflow.com/a/7784904/3969362
 */
fun View.simulateTap(x: Float = 0F, y: Float = 0F) {
    dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
        SystemClock.uptimeMillis(),
        MotionEvent.ACTION_DOWN,
        x,
        y,
        0))
    dispatchTouchEvent(MotionEvent.obtain(SystemClock.uptimeMillis(),
        SystemClock.uptimeMillis(),
        MotionEvent.ACTION_UP,
        x,
        y,
        0))
}

fun RectF.scale(factor: Float) {
    val oldWidth = width()
    val oldHeight = height()
    val newWidth = width() * factor
    val newHeight = height() * factor
    left += (oldWidth - newWidth) / 2f
    right -= (oldWidth - newWidth) / 2f
    top += (oldHeight - newHeight) / 2f
    bottom -= (oldHeight - newHeight) / 2f
}

/**
 * Tells if the virtual keyboard is shown.
 * Solution taken from https://stackoverflow.com/a/52171843/3969362
 * Android is silly like this.
 */
@SuppressLint("DiscouragedPrivateApi")
fun InputMethodManager.isVirtualKeyboardVisible(): Boolean {
    return try {
        // Use reflection to access the hidden API we need.
        val method: Method =
            InputMethodManager::class.java.getDeclaredMethod("getInputMethodWindowVisibleHeight")
        // Assuming if the virtual keyboard height is above zero it is currently being shown.
        ((method.invoke(this) as Int) > 0)
    } catch (ex: Exception) {
        // Something went wrong, let's pretend the virtual keyboard is not showing then.
        // This is defensive and should never happen.
        false
    }
}
