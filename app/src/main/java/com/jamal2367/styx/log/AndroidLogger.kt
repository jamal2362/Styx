/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.log

import android.util.Log
import dagger.Reusable
import javax.inject.Inject

/**
 * A logger that utilizes the [Log] class.
 */
@Reusable
class AndroidLogger @Inject constructor() : Logger {

    override fun log(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun log(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }

}
