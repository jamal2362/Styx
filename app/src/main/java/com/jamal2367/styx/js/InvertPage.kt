/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.js

import com.anthonycr.mezzanine.FileStream

/**
 * Invert the color of the page.
 */
@FileStream("app/src/main/js/InvertPage.js")
interface InvertPage {

    fun provideJs(): String

}