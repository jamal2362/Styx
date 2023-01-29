/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser.tabs

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jamal2367.styx.R
import com.jamal2367.styx.browser.TabsView
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.controller.UIController
import com.jamal2367.styx.databinding.TabDesktopViewBinding
import com.jamal2367.styx.extensions.inflater
import com.jamal2367.styx.utils.ItemDragDropSwipeHelper
import com.jamal2367.styx.view.StyxView

/**
 * A view which displays browser tabs in a horizontal [RecyclerView].
 * TODO: Rename to horizontal?
 */
class TabsDesktopView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr), TabsView {

    private val uiController = context as UIController
    private val tabsAdapter: TabsDesktopAdapter
    private val tabList: RecyclerView
    private var iItemTouchHelper: ItemTouchHelper? = null

    // Inflate our layout with binding support
    private val iBinding: TabDesktopViewBinding =
        TabDesktopViewBinding.inflate(context.inflater, this, true)

    init {
        // Provide UI controller
        iBinding.uiController = uiController

        val layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)

        tabsAdapter = TabsDesktopAdapter(uiController)

        tabList = findViewById<RecyclerView>(R.id.tabs_list).apply {
            setLayerType(View.LAYER_TYPE_NONE, null)
            // We don't want that morphing animation for now
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
            this.layoutManager = layoutManager
            adapter = tabsAdapter
            setHasFixedSize(true)
        }

        // Enable drag & drop but not swipe
        val callback: ItemTouchHelper.Callback = ItemDragDropSwipeHelper(tabsAdapter,
            aLongPressDragEnabled = true,
            aSwipeEnabled = false,
            aDragFlags = ItemTouchHelper.END or ItemTouchHelper.START
        )
        iItemTouchHelper = ItemTouchHelper(callback)
        iItemTouchHelper?.attachToRecyclerView(iBinding.tabsList)
    }

    /**
     * Enable tool bar buttons according to current state of things
     */
    private fun updateTabActionButtons() {
        // If we have at least one tab in our closed tabs list enable restore page button
        iBinding.actionRestorePage.isEnabled =
            (uiController as BrowserActivity).presenter.closedTabs.bundleStack.isNotEmpty()
        // No sessions in incognito mode
        if (uiController.isIncognito()) {
            iBinding.actionSessions.visibility = View.GONE
        }
    }


    override fun tabAdded() {
        displayTabs()
        updateTabActionButtons()
    }

    override fun tabRemoved(position: Int) {
        displayTabs()
        updateTabActionButtons()
    }

    override fun tabChanged(position: Int) {
        displayTabs()
        // Needed for the foreground tab color to update.
        // However sometimes it throws an illegal state exception so make sure we catch it.
        try {
            tabsAdapter.notifyItemChanged(position)
        } catch (_: Exception) {
        }

    }

    private fun displayTabs() {
        tabsAdapter.showTabs(uiController.getTabModel().allTabs.map(StyxView::asTabViewState))
    }

    override fun tabsInitialized() {
        tabsAdapter.notifyDataSetChanged()
        updateTabActionButtons()
    }

    override fun setGoBackEnabled(isEnabled: Boolean) = Unit

    override fun setGoForwardEnabled(isEnabled: Boolean) = Unit

}
