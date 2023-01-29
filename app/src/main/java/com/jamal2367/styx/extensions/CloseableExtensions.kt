/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.extensions

import android.util.Log
import java.io.Closeable

/**
 * Close a [Closeable] and absorb any exceptions within [block], logging them when they occur.
 */
inline fun <T : Closeable, R> T.safeUse(block: (T) -> R): R? {
    return try {
        this.use(block)
    } catch (throwable: Throwable) {
        Log.e("Closeable", "Unable to parse results", throwable)
        null
    }
}
