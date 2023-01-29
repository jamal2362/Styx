/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.view

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.TransitionDrawable
import androidx.annotation.AttrRes
import com.jamal2367.styx.R
import com.jamal2367.styx.utils.ThemeUtils

/**
 * Create a new transition drawable with the specified list of layers. At least
 * 2 layers are required for this drawable to work properly.
 */
class BackgroundDrawable(
    context: Context,
    @AttrRes first: Int = android.R.attr.colorBackground,
    @AttrRes second: Int = R.attr.colorSurface,
) : TransitionDrawable(
    arrayOf<Drawable>(
        ColorDrawable(ThemeUtils.getColor(context, first)),
        ColorDrawable(ThemeUtils.getColor(context, second))
    )
) {

    private var isSelected: Boolean = false

    override fun startTransition(durationMillis: Int) {
        if (!isSelected) {
            super.startTransition(durationMillis)
        }
        isSelected = true
    }

    override fun reverseTransition(duration: Int) {
        if (isSelected) {
            super.reverseTransition(duration)
        }
        isSelected = false
    }

}
