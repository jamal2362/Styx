/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.settings.fragment

import android.os.Bundle
import android.view.View
import androidx.annotation.XmlRes
import androidx.preference.*
import androidx.recyclerview.widget.RecyclerView

/**
 * An abstract settings fragment which performs wiring for an instance of [PreferenceFragment].
 */
abstract class AbstractSettingsFragment : PreferenceFragmentCompat() {

    /**
     * Provide the XML resource which holds the preferences.
     */
    @XmlRes
    protected abstract fun providePreferencesXmlResource(): Int

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(providePreferencesXmlResource(), rootKey)
    }

    /**
     * Called by the framework once our view has been created from its XML definition.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Enable fading edge when scrolling settings, looks much better
        view.findViewById<RecyclerView>(R.id.recycler_view)?.apply {
            isVerticalFadingEdgeEnabled = true
        }
    }

    /**
     * Creates a simple [Preference] which reacts to clicks with the provided options and listener.
     *
     * @param preference the preference to create.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param summary the summary to display. Defaults to null, which results in no summary.
     * @param onClick the function that should be called when the preference is clicked.
     */
    protected fun clickablePreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: (() -> Unit)? = null,
    ): Preference = clickableDynamicPreference(
        preference = preference,
        isEnabled = isEnabled,
        summary = summary,
        onClick = onClick?.let { { _: SummaryUpdater -> it.invoke() } }
    )

    /**
     * Creates a simple [Preference] which reacts to clicks with the provided options and listener.
     * It also allows its summary to be updated when clicked.
     *
     * @param preference the preference to create.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param summary the summary to display. Defaults to null, which results in no summary.
     * @param onClick the function that should be called when the preference is clicked. The
     * function is supplied with a [SummaryUpdater] object so that it can update the summary if
     * desired.
     */
    protected fun clickableDynamicPreference(
        preference: String,
        isEnabled: Boolean = true,
        summary: String? = null,
        onClick: ((SummaryUpdater) -> Unit)?,
    ): Preference = (findPreference<Preference>(preference) as Preference).apply {
        this.isEnabled = isEnabled
        summary?.let {
            this.summary = summary
        }

        if (onClick != null) {
            val summaryUpdate = SummaryUpdater(this)
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                onClick(summaryUpdate)
                true
            }
        }
    }

    /**
     * Creates a [SwitchPreference] with the provided options and listener.
     *
     * @param preference the preference to create.
     * @param isChecked true if it should be initialized as checked, false otherwise.
     * @param isEnabled true if the preference should be enabled, false otherwise. Defaults to true.
     * @param onCheckChange the function that should be called when the toggle is toggled.
     */
    protected fun switchPreference(
        preference: String,
        isChecked: Boolean,
        isEnabled: Boolean = true,
        isVisible: Boolean = true,
        summary: String? = null,
        onCheckChange: (Boolean) -> Unit,
    ): SwitchPreferenceCompat =
        (findPreference<SwitchPreferenceCompat>(preference) as SwitchPreferenceCompat).apply {
            this.isChecked = isChecked
            this.isEnabled = isEnabled
            this.isVisible = isVisible
            summary?.let {
                this.summary = summary
            }
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, any: Any ->
                onCheckChange(any as Boolean)
                true
            }
        }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (preference is ListPreference) {
            showListPreferenceDialog(preference)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

}
