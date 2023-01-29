/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

@file:JvmName("ActivityExtensions")

package com.jamal2367.styx.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.view.Gravity
import android.view.View
import android.view.Window
import androidx.annotation.StringRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.jamal2367.styx.R
import com.jamal2367.styx.utils.Utils

// Define our snackbar popup duration
const val KDuration: Int = 4000 // Snackbar.LENGTH_LONG
const val KCDuration: Int = 10000 // Snackbar.LENGTH_LONG

/**
 * Displays a snackbar to the user with a [StringRes] message.
 *
 * NOTE: If there is an accessibility manager enabled on
 * the device, such as LastPass, then the snackbar animations
 * will not work.
 *
 * @param resource the string resource to display to the user.
 */
fun Activity.snackbar(@StringRes resource: Int, aGravity: Int = Gravity.BOTTOM) {
    makeSnackbar(getString(resource), KDuration, aGravity).show()
}

/**
 * Display a snackbar to the user with a [String] message.
 *
 * @param message the message to display to the user.
 * @see snackbar
 */
fun Activity.snackbar(message: String, aGravity: Int = Gravity.BOTTOM) {
    makeSnackbar(message, KDuration, aGravity).show()
}

/**
 *
 */
@SuppressLint("WrongConstant")
fun Activity.makeSnackbar(message: String, aDuration: Int, aGravity: Int): Snackbar {
    var view = findViewById<View>(R.id.coordinator_layout)
    if (view == null) {
        // We won't use gravity and we provide compatibility with previous implementation
        view = findViewById(android.R.id.content)
        return Snackbar.make(view, message, aDuration)
    } else {
        // Apply specified gravity before showing snackbar
        val snackbar = Snackbar.make(view, message, KDuration)
        val params = snackbar.view.layoutParams as CoordinatorLayout.LayoutParams
        params.gravity = aGravity
        if (aGravity == Gravity.TOP) {
            // Move snackbar away from status bar
            // That one works well it seems
            params.topMargin = Utils.dpToPx(30F)
        } else {
            // Make sure it is above rounded corner
            // Ain't working on F(x)tec Pro1, weird...
            params.bottomMargin = Utils.dpToPx(30F)
        }
        snackbar.view.layoutParams = params
        snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE
        snackbar.show()
        return snackbar
    }
}

@SuppressLint("WrongConstant")
fun Activity.makeCSnackbar(message: String, aDuration: Int, aGravity: Int): Snackbar {
    var view = findViewById<View>(R.id.coordinator_layout)
    if (view == null) {
        // We won't use gravity and we provide compatibility with previous implementation
        view = findViewById(android.R.id.content)
        return Snackbar.make(view, message, aDuration)
    } else {
        // Apply specified gravity before showing snackbar
        val snackbar = Snackbar.make(view, message, KCDuration)
        val params = snackbar.view.layoutParams as CoordinatorLayout.LayoutParams
        params.gravity = aGravity
        if (aGravity == Gravity.TOP) {
            // Move snackbar away from status bar
            // That one works well it seems
            params.topMargin = Utils.dpToPx(30F)
        } else {
            // Make sure it is above rounded corner
            // Ain't working on F(x)tec Pro1, weird...
            params.bottomMargin = Utils.dpToPx(30F)
        }
        snackbar.view.layoutParams = params
        snackbar.animationMode = BaseTransientBottomBar.ANIMATION_MODE_FADE
        snackbar.show()
        return snackbar
    }
}

/**
 *
 */
@Suppress("DEPRECATION")
fun Window.setStatusBarIconsColor(dark: Boolean) {
    if (dark) {
        decorView.systemUiVisibility =
            decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    } else {
        decorView.systemUiVisibility =
            decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
    }
}
