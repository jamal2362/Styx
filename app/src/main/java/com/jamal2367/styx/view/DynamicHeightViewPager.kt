package com.jamal2367.styx.view

import android.content.Context
import android.util.AttributeSet
import androidx.viewpager.widget.ViewPager

/**
 * A pager which can adjust its height according to every page content.
 * Taken from: https://stackoverflow.com/a/50975140/3969362
 */
class DynamicHeightViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewPager(context, attrs) {

    override fun onMeasure(aWidthMeasureSpec: Int, aHeightMeasureSpec: Int) {
        var heightMeasureSpec = aHeightMeasureSpec

        var height = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(aWidthMeasureSpec,
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            val h = child.measuredHeight
            if (h > height) height = h
        }

        if (height != 0) {
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(height + paddingBottom + paddingTop,
                MeasureSpec.EXACTLY)
        }

        if (MeasureSpec.getSize(heightMeasureSpec) < MeasureSpec.getSize(aHeightMeasureSpec)) {
            // Only use our new specs if smaller to avoid break scrolling in dialogs
            super.onMeasure(aWidthMeasureSpec, heightMeasureSpec)
        } else {
            super.onMeasure(aWidthMeasureSpec, aHeightMeasureSpec)
        }
    }
}