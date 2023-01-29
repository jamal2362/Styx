/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.extensions

import java.util.*

/**
 * If the [Stack] is empty, null is returned, otherwise the item at the top of the stack is
 * returned.
 */
fun <T> Stack<T>.popIfNotEmpty(): T? {
    return if (empty()) {
        null
    } else {
        pop()
    }
}
