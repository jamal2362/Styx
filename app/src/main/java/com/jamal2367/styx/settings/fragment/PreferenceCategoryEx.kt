/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.settings.fragment

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder

// Used to enable multiple line summary
// See: https://stackoverflow.com/questions/6729484/android-preference-summary-how-to-set-3-lines-in-summary
class PreferenceCategoryEx(ctx: Context, attrs: AttributeSet?) : PreferenceCategory(ctx, attrs) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val summary = holder.findViewById(android.R.id.summary) as TextView
        // Enable multiple line support
        summary.isSingleLine = false
        summary.maxLines = 10 // Just need to be high enough I guess
    }
}
