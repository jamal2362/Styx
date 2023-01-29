/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.settings.fragment

import androidx.preference.Preference

/**
 * A command that updates the summary of a preference.
 */
class SummaryUpdater(private val preference: Preference) {

    /**
     * Updates the summary of the preference.
     *
     * @param text the text to display in the summary.
     */
    fun updateSummary(text: String) {
        preference.summary = text
    }

}