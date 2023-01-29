/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser.tabs

import android.graphics.Bitmap
import android.graphics.Color
import com.jamal2367.styx.view.StyxView

/**
 * @param id The unique id of the tab.
 * @param title The title of the tab.
 * @param favicon The favicon of the tab, may be null.
 */
data class TabViewState(
    val id: Int = 0,
    val title: String = "",
    val favicon: Bitmap? = null,
    val isForeground: Boolean = false,
    val themeColor: Int = Color.TRANSPARENT,
)

/**
 * Converts a [StyxView] to a [TabViewState].
 */
fun StyxView.asTabViewState() = TabViewState(
    id = id,
    title = title,
    favicon = favicon,
    isForeground = isForeground,
    themeColor = htmlMetaThemeColor
)
