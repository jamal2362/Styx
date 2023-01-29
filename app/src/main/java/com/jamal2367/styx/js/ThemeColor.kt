/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.js

import com.anthonycr.mezzanine.FileStream

/**
 * Reads the theme color from the DOM.
 */
@FileStream("app/src/main/js/ThemeColor.js")
interface ThemeColor {

    fun provideJs(): String

}