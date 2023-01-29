/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.dialog

import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.annotation.StringRes


/**
 * An item representing a list item in a list dialog. The item has an [icon], [title], an [onClick]
 * function to be invoked when the item is clicked, and a boolean condition [show] which
 * defaults to true and allows the consumer to control the visibility of the item in the list.
 */
class DialogItem(
    val icon: Drawable? = null,
    @param:ColorInt
    val colorTint: Int? = null,
    @param:StringRes
    val title: Int,
    val text: String? = null,
    val show: Boolean = true,
    private val onClick: () -> Unit,
) {
    fun onClick() = onClick.invoke()
}
