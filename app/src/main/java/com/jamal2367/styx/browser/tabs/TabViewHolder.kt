/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser.tabs

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.jamal2367.styx.R
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.controller.UIController
import com.jamal2367.styx.utils.ItemDragDropSwipeViewHolder

/**
 * The [RecyclerView.ViewHolder] for both vertical and horizontal tabs.
 * That represents an item in our list, basically one tab.
 */
class TabViewHolder(
    view: View,
    private val uiController: UIController,
) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnLongClickListener,
    ItemDragDropSwipeViewHolder {

    // Using view binding won't give us much
    val txtTitle: TextView = view.findViewById(R.id.textTab)
    val favicon: ImageView = view.findViewById(R.id.faviconTab)
    val exitButton: View = view.findViewById(R.id.deleteAction)
    val iCardView: MaterialCardView = view.findViewById(R.id.tab_item_background)

    // Keep a copy of our tab data to be able to understand what was changed on update
    var tab: TabViewState = TabViewState()

    init {
        exitButton.setOnClickListener(this)
        iCardView.setOnClickListener(this)
        iCardView.setOnLongClickListener(this)
        // Is that the best way to access our preferences?
        // If not showing horizontal desktop tab bar, this one always shows close button.
        // Apply settings preference for showing close button on tabs.
        exitButton.visibility =
            if (!(view.context as BrowserActivity).userPreferences.verticalTabBar
                || (view.context as BrowserActivity).userPreferences.showCloseTabButton
            ) View.VISIBLE else View.GONE
    }

    override fun onClick(v: View) {
        if (v === exitButton) {
            uiController.tabCloseClicked(adapterPosition)
        } else if (v === iCardView) {
            uiController.tabClicked(adapterPosition)
        }
    }

    override fun onLongClick(v: View): Boolean {
        //uiController.showCloseDialog(adapterPosition)
        //return true
        return false
    }

    // From ItemTouchHelperViewHolder
    // Start dragging
    override fun onItemOperationStart() {
        iCardView.isDragged = true
    }

    // From ItemTouchHelperViewHolder
    // Stopped dragging
    override fun onItemOperationStop() {
        iCardView.isDragged = false
    }


}
