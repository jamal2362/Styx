/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.ssl

import android.net.http.SslError

/**
 * Representing the SSL state of the browser.
 */
sealed class SslState {

    /**
     * No SSL.
     */
    object None : SslState()

    /**
     * Valid SSL connection.
     */
    object Valid : SslState()

    /**
     * Broken SSL connection.
     *
     * @param sslError The error that is causing the invalid SSL state.
     */
    class Invalid(private val sslError: SslError) : SslState()

}
