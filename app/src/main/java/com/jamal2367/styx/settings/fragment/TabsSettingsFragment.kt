/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.settings.fragment

import android.os.Bundle
import com.jamal2367.styx.R
import com.jamal2367.styx.preference.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The extension settings of the app.
 */
@AndroidEntryPoint
class TabsSettingsFragment : AbstractSettingsFragment() {

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_tabs

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preference_tabs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        switchPreference(
            preference = SETTINGS_LAST_TAB,
            isChecked = userPreferences.closeOnLastTab,
            onCheckChange = { userPreferences.closeOnLastTab = it }
        )

    }

    companion object {
        private const val SETTINGS_LAST_TAB = "last_tab"
    }
}
