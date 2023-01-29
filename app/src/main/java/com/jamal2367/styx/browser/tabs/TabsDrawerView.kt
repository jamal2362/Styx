/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser.tabs

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jamal2367.styx.browser.TabsView
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.controller.UIController
import com.jamal2367.styx.databinding.TabDrawerViewBinding
import com.jamal2367.styx.di.HiltEntryPoint
import com.jamal2367.styx.extensions.inflater
import com.jamal2367.styx.utils.ItemDragDropSwipeHelper
import com.jamal2367.styx.utils.Utils.fixScrollBug
import com.jamal2367.styx.view.StyxView
import dagger.hilt.android.EntryPointAccessors

/**
 * A view which displays tabs in a vertical [RecyclerView].
 */
class TabsDrawerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr), TabsView {

    private val hiltEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, HiltEntryPoint::class.java)
    val userPreferences = hiltEntryPoint.userPreferences

    private val uiController = context as UIController
    private val tabsAdapter: TabsDrawerAdapter

    private var mItemTouchHelper: ItemTouchHelper? = null

    var iBinding: TabDrawerViewBinding

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true

        // Inflate our layout with binding support, provide UI controller
        iBinding = TabDrawerViewBinding.inflate(context.inflater, this, true)
        iBinding.uiController = uiController

        tabsAdapter = TabsDrawerAdapter(uiController)

        iBinding.tabsList.apply {
            //setLayerType(View.LAYER_TYPE_NONE, null)
            // We don't want that morphing animation for now
            (itemAnimator as DefaultItemAnimator).supportsChangeAnimations = false
            // Reverse layout if using bottom tool bars
            // LinearLayoutManager.setReverseLayout is also adjusted from BrowserActivity.setupToolBar
            val lm =
                LinearLayoutManager(context, RecyclerView.VERTICAL, userPreferences.toolbarsBottom)
            // Though that should not be needed as it is taken care of by [fixScrollBug]
            // See: https://github.com/Slion/Fulguris/issues/212
            lm.stackFromEnd = userPreferences.toolbarsBottom
            layoutManager = lm
            adapter = tabsAdapter
            setHasFixedSize(false)
        }

        val callback: ItemTouchHelper.Callback = ItemDragDropSwipeHelper(tabsAdapter)

        mItemTouchHelper = ItemTouchHelper(callback)
        mItemTouchHelper?.attachToRecyclerView(iBinding.tabsList)

    }

    /**
     * Enable tool bar buttons according to current state of things
     */
    private fun updateTabActionButtons() {
        // If more than one tab, enable close all tabs button
        iBinding.actionCloseAllTabs.isEnabled = uiController.getTabModel().allTabs.count() > 1
        // If we have more than one tab in our closed tabs list enable restore all pages button
        iBinding.actionRestoreAllPages.isEnabled =
            (uiController as BrowserActivity).presenter.closedTabs.bundleStack.count() > 1
        // If we have at least one tab in our closed tabs list enable restore page button
        iBinding.actionRestorePage.isEnabled =
            uiController.presenter.closedTabs.bundleStack.isNotEmpty()
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
        //tabsAdapter.notifyItemRemoved(position)
        updateTabActionButtons()
    }

    override fun tabChanged(position: Int) {
        displayTabs()
        //tabsAdapter.notifyItemChanged(position)
    }

    /**
     * TODO: this is called way to often for my taste and should be optimized somehow.
     */
    private fun displayTabs() {
        tabsAdapter.showTabs(uiController.getTabModel().allTabs.map(StyxView::asTabViewState))

        if (fixScrollBug(iBinding.tabsList)) {
            // Scroll bug was fixed trigger a scroll to current item then
            (context as BrowserActivity).apply {
                mainHandler.postDelayed({ scrollToCurrentTab() }, 0)
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun tabsInitialized() {
        tabsAdapter.notifyDataSetChanged()
        updateTabActionButtons()
    }

    override fun setGoBackEnabled(isEnabled: Boolean) {
        //actionBack.isEnabled = isEnabled
    }

    override fun setGoForwardEnabled(isEnabled: Boolean) {
        //actionForward.isEnabled = isEnabled
    }

}
