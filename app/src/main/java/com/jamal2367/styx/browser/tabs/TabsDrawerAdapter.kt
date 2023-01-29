/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser.tabs

import android.graphics.Bitmap
import android.view.ViewGroup
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.jamal2367.styx.R
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.controller.UIController
import com.jamal2367.styx.extensions.dimen
import com.jamal2367.styx.extensions.inflater
import com.jamal2367.styx.extensions.setImageForTheme

/**
 * The adapter for vertical mobile style browser tabs.
 */
class TabsDrawerAdapter(
    uiController: UIController,
) : TabsAdapter(uiController) {

    /**
     * From [RecyclerView.Adapter]
     */
    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TabViewHolder {
        val view = viewGroup.context.inflater.inflate(R.layout.tab_list_item, viewGroup, false)
        return TabViewHolder(view, uiController) //.apply { setIsRecyclable(false) }
    }

    /**
     * From [RecyclerView.Adapter]
     */
    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        holder.exitButton.tag = position

        val tab = tabList[position]

        holder.txtTitle.text = tab.title
        updateViewHolderAppearance(holder, tab)
        updateViewHolderFavicon(holder, tab.favicon)
        updateViewHolderBackground(holder, tab.isForeground)
        // Update our copy so that we can check for changes then
        holder.tab = tab.copy()
    }

    private fun updateViewHolderFavicon(viewHolder: TabViewHolder, favicon: Bitmap?) {
        // Apply filter to favicon if needed
        favicon?.let {
            val ba = uiController as BrowserActivity
            viewHolder.favicon.setImageForTheme(it, ba.useDarkTheme)
        } ?: viewHolder.favicon.setImageResource(R.drawable.ic_webpage)
    }

    private fun updateViewHolderBackground(viewHolder: TabViewHolder, isForeground: Boolean) {
        viewHolder.iCardView.apply {
            isChecked = isForeground
            // Adjust tab item height depending of foreground state
            val params = layoutParams
            params.height =
                context.dimen(if (isForeground) R.dimen.material_grid_touch_large else R.dimen.material_grid_touch_medium)
            layoutParams = params
        }

    }

    private fun updateViewHolderAppearance(viewHolder: TabViewHolder, tab: TabViewState) {
        if (tab.isForeground) {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.boldText)
        } else {
            TextViewCompat.setTextAppearance(viewHolder.txtTitle, R.style.italicText)
        }
    }

}
