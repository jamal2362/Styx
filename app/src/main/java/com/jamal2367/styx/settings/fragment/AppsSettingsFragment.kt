/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package com.jamal2367.styx.settings.fragment

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.jamal2367.styx.R
import com.jamal2367.styx.di.AppsPrefs
import com.jamal2367.styx.utils.IntentUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppsSettingsFragment : AbstractSettingsFragment() {

    @Inject
    @AppsPrefs
    internal lateinit var aPreferences: SharedPreferences

    override fun providePreferencesXmlResource() = R.xml.preference_apps

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        IntentUtils(activity as AppCompatActivity)

        // Get all our preferences for external app on populate our settings page with theme
        val allEntries: Map<String, *> = aPreferences.all
        for ((key, value) in allEntries) {

            if (key.startsWith(getString(R.string.settings_app_prefix))) {
                val iPreference: Preference? = findPreference("reset")
                val switchPreference = SwitchPreferenceCompat(requireContext())
                switchPreference.title =
                    key.substring(getString(R.string.settings_app_prefix).length)
                switchPreference.key = key
                switchPreference.isChecked = value as Boolean
                switchPreference.icon = ResourcesCompat.getDrawable(
                    resources,
                    R.drawable.ic_website,
                    requireActivity().theme
                )

                preferenceScreen.addPreference(switchPreference)

                if (iPreference != null) {
                    iPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        preferenceScreen.removeAll()

                        val settings = requireContext().getSharedPreferences("apps_settings", 0)
                        settings.edit().clear().apply()

                        (activity as AppCompatActivity).recreate()
                        true
                    }
                }
            }
        }
    }
}
