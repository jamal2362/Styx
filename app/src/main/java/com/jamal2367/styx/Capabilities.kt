/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx

import android.os.Build

/**
 * Capabilities that are specific to certain API levels.
 */
enum class Capabilities {
    FULL_INCOGNITO,
    WEB_RTC,
    THIRD_PARTY_COOKIE_BLOCKING
}

/**
 * Returns true if the capability is supported, false otherwise.
 */
val Capabilities.isSupported: Boolean
    get() = when (this) {
        Capabilities.FULL_INCOGNITO -> Build.VERSION.SDK_INT >= 28
        Capabilities.WEB_RTC -> true
        Capabilities.THIRD_PARTY_COOKIE_BLOCKING -> true
    }
