/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import com.jamal2367.styx.di.HiltEntryPoint
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.utils.ThemeUtils
import dagger.hilt.android.EntryPointAccessors

abstract class ThemedActivity : AppCompatActivity() {

    private val hiltEntryPoint =
        EntryPointAccessors.fromApplication(BrowserApp.instance.applicationContext,
            HiltEntryPoint::class.java)
    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences

    protected var accentId: AccentTheme = userPreferences.useAccent
    protected var themeId: AppTheme = userPreferences.useTheme
    private var isDarkTheme: Boolean = false
    val useDarkTheme get() = isDarkTheme

    /**
     * Override this to provide an alternate theme that should be set for every instance of this
     * activity regardless of the user's preference.
     */
    protected open fun provideThemeOverride(): AppTheme? = null

    protected open fun provideAccentThemeOverride(): AccentTheme? = null

    /**
     * Called after the activity is resumed
     * and the UI becomes visible to the user.
     * Called by onWindowFocusChanged only if
     * onResume has been called.
     */
    protected open fun onWindowVisibleToUserAfterResume() = Unit

    /**
     * Implement this to provide themes resource style ids.
     */
    @StyleRes
    fun themeStyle(aTheme: AppTheme): Int {
        return when (aTheme) {
            AppTheme.DEFAULT -> R.style.Theme_App_DayNight
            AppTheme.LIGHT -> R.style.Theme_App_Light
            AppTheme.DARK -> R.style.Theme_App_Dark
            AppTheme.BLACK -> R.style.Theme_App_Black
        }
    }

    @StyleRes
    fun accentStyle(accentTheme: AccentTheme): Int? {
        return when (accentTheme) {
            AccentTheme.DEFAULT_ACCENT -> null
            AccentTheme.PINK -> R.style.Accent_Pink
            AccentTheme.PURPLE -> R.style.Accent_Puple
            AccentTheme.DEEP_PURPLE -> R.style.Accent_Deep_Purple
            AccentTheme.INDIGO -> R.style.Accent_Indigo
            AccentTheme.BLUE -> R.style.Accent_Blue
            AccentTheme.LIGHT_BLUE -> R.style.Accent_Light_Blue
            AccentTheme.CYAN -> R.style.Accent_Cyan
            AccentTheme.TEAL -> R.style.Accent_Teal
            AccentTheme.GREEN -> R.style.Accent_Green
            AccentTheme.LIGHT_GREEN -> R.style.Accent_Light_Green
            AccentTheme.LIME -> R.style.Accent_Lime
            AccentTheme.YELLOW -> R.style.Accent_Yellow
            AccentTheme.AMBER -> R.style.Accent_Amber
            AccentTheme.ORANGE -> R.style.Accent_Orange
            AccentTheme.DEEP_ORANGE -> R.style.Accent_Deep_Orange
            AccentTheme.BROWN -> R.style.Accent_Brown
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        // set the theme
        applyTheme(provideThemeOverride() ?: themeId)
        applyAccent()

        super.onCreate(savedInstanceState)
        resetPreferences()
    }

    /**
     *
     */
    protected fun resetPreferences() {
        if (userPreferences.useBlackStatusBar) {
            window.statusBarColor = Color.BLACK
        } else {
            window.statusBarColor = ThemeUtils.getStatusBarColor(this)
        }
    }

    /**
     * Private because one should use [provideThemeOverride] to set our theme.
     * Changing it during the lifetime of the activity or after super.[onCreate] call is not working properly.
     */
    private fun applyTheme(themeId: AppTheme) {
        setTheme(themeStyle(themeId))
        // Check if we have a dark theme
        isDarkTheme = isDarkTheme(themeId)
    }


    /**
     * Tells if the given [themeId] is dark. Takes into account current system theme if needed.
     * Works even before calling supper.[onCreate].
     */
    protected fun isDarkTheme(themeId: AppTheme): Boolean {
        val mode = resources?.configuration?.uiMode?.and(Configuration.UI_MODE_NIGHT_MASK)
        return themeId == AppTheme.BLACK // Black qualifies as dark theme
                || themeId == AppTheme.DARK // Dark is indeed a dark theme
                // Check if we are using system default theme and it is currently set to dark
                || (themeId == AppTheme.DEFAULT && mode == Configuration.UI_MODE_NIGHT_YES)

    }

    /**
     *
     */
    private fun applyAccent() {
        accentStyle(accentId)?.let {
            setTheme(it)
        }
    }

    /**
     * Using this instead of recreate() because it does not work when handling resource changes I guess.
     */
    protected fun restart() {
        finish()
        startActivity(Intent(this, javaClass))
    }

}
