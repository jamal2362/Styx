/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.log

import dagger.Reusable
import javax.inject.Inject

/**
 * A logger that doesn't log.
 */
@Reusable
class NoOpLogger @Inject constructor() : Logger {

    override fun log(tag: String, message: String) = Unit

    override fun log(tag: String, message: String, throwable: Throwable) = Unit

}
