/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.log

/**
 * A logger.
 */
interface Logger {

    /**
     * Log the [message] for the provided [tag].
     */
    fun log(tag: String, message: String)

    /**
     * Log the [message] and [throwable] for the provided [tag].
     */
    fun log(tag: String, message: String, throwable: Throwable)

}
