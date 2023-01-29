/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.ssl

/**
 * An interface to remember the chosen browsing behavior when handling SSL warnings.
 */
interface SslWarningPreferences {

    enum class Behavior {
        PROCEED,
        CANCEL
    }

    /**
     * Remember the provided [behavior] for the given [url]. The behavior will be assigned to the
     * domain of the URL.
     */
    fun rememberBehaviorForDomain(url: String, behavior: Behavior)

    /**
     * Recall the [Behavior] for the provided [url]. If there was no behavior to be remembered, then
     * this function will return `null`.
     */
    fun recallBehaviorForDomain(url: String?): Behavior?
}