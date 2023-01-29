package com.jamal2367.styx.dialog

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes


/**
 * Define a tab in our dialog
 *
 * @param icon Drawable resource identifier. Will be used to set this tab icon.
 * @param title This tab text title.
 * @param show Tells if this tab should be visible.
 * @param items List of items used to populate this tab content view.
 */
class DialogTab(
    @DrawableRes
    val icon: Int = 0,
    @param:StringRes
    val title: Int = 0,
    val show: Boolean = true,
    vararg items: DialogItem,
) {
    // Apparently that's needed for variable argument list
    val iItems = items
}