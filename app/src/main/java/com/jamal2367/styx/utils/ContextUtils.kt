/*
 * Copyright © 2022 Jamal Rothfuchs
 */

package com.jamal2367.styx.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.viewbinding.BuildConfig

object ContextUtils {

    fun Context.getColorStateListSafe(@ColorRes color: Int): ColorStateList {
        return try {
            unlikelyToBeNull(ContextCompat.getColorStateList(this, color))
        } catch (e: Exception) {
            handleResourceFailure(e,
                getColorSafe(android.R.color.black).stateList)
        }
    }

    @ColorInt
    fun Context.getColorSafe(@ColorRes color: Int): Int {
        return try {
            ContextCompat.getColor(this, color)
        } catch (e: Exception) {
            handleResourceFailure(e, getColorSafe(android.R.color.black))
        }
    }

    fun Context.getDrawableSafe(@DrawableRes drawable: Int): Drawable {
        return try {
            requireNotNull(ContextCompat.getDrawable(this, drawable))
        } catch (e: Exception) {
            handleResourceFailure(e, ColorDrawable(getColorSafe(android.R.color.black)))
        }
    }

    private fun <T> unlikelyToBeNull(value: T?): T {
        return if (BuildConfig.DEBUG) {
            requireNotNull(value)
        } else {
            value!!
        }
    }

    private fun <T> handleResourceFailure(e: Exception, default: T): T {
        Log.d("WHAT", "load failed")
        e.logTraceOrThrow()
        return default
    }

    private fun Throwable.logTraceOrThrow() {
        if (BuildConfig.DEBUG) {
            throw this
        } else {
            Log.e("WHAT", stackTraceToString())
        }
    }

    private val @receiver:ColorRes Int.stateList
        get() = ColorStateList.valueOf(this)

}
