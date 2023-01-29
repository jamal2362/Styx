/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.utils

/**
 * An option type, taken from the Arrow library.
 */
sealed class Option<out T> {

    /**
     * A type representing the presences of [some] [T].
     *
     * @param some Some [T].
     */
    data class Some<T>(val some: T) : Option<T>()

    /**
     * A type representing the absence of [T].
     */
    object None : Option<Nothing>()

}

/**
 * Returns the value held by the [Option] as a nullable [T].
 */
fun <T> Option<T>.value(): T? = when (this) {
    is Option.Some -> some
    Option.None -> null
}
