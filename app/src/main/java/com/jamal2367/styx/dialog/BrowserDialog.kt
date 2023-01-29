/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.jamal2367.styx.R
import com.jamal2367.styx.extensions.*
import com.jamal2367.styx.list.RecyclerViewDialogItemAdapter
import com.jamal2367.styx.list.RecyclerViewStringAdapter
import com.jamal2367.styx.utils.DeviceUtils

object BrowserDialog {

    @JvmStatic
    fun show(
        aContext: Context,
        @StringRes title: Int,
        vararg items: DialogItem,
    ) = show(aContext, aContext.getString(title), *items)

    fun showWithIcons(context: Context, title: String?, vararg items: DialogItem) {
        val builder = MaterialAlertDialogBuilder(context)

        val layout = context.inflater.inflate(R.layout.list_dialog, null)

        val titleView = layout.findViewById<TextView>(R.id.dialog_title)
        val recyclerView = layout.findViewById<RecyclerView>(R.id.dialog_list)

        val itemList = items.filter(DialogItem::show)

        val adapter = RecyclerViewDialogItemAdapter(itemList)

        if (title?.isNotEmpty() == true) {
            titleView.text = title
        }

        recyclerView.apply {
            this.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            this.adapter = adapter
            setHasFixedSize(true)
        }

        builder.setView(layout)

        val dialog = builder.resizeAndShow()

        adapter.onItemClickListener = { item ->
            item.onClick()
            dialog.dismiss()
        }
    }

    @JvmStatic
    fun show(aContext: Context, title: String?, vararg items: DialogItem) {
        val builder = MaterialAlertDialogBuilder(aContext)

        val layout = aContext.inflater.inflate(R.layout.list_dialog, null)

        val titleView = layout.findViewById<TextView>(R.id.dialog_title)
        val recyclerView = layout.findViewById<RecyclerView>(R.id.dialog_list)

        val itemList = items.filter(DialogItem::show)

        val adapter = RecyclerViewStringAdapter(itemList,
            getTitle = { aContext.getString(this.title) },
            getText = { this.text })

        if (title?.isNotEmpty() == true) {
            titleView.text = title
        }

        recyclerView.apply {
            this.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
            this.adapter = adapter
            setHasFixedSize(true)
        }

        builder.setView(layout)

        val dialog = builder.resizeAndShow()

        adapter.onItemClickListener = { item ->
            item.onClick()
            dialog.dismiss()
        }
    }

    /**
     * Build and show a tabbed dialog based on the provided parameters.
     *
     * @param aContext The aContext requesting that dialog.
     * @param aTitle The dialog title string resource id.
     * @param aHideSingleTab Set to true to hide tab layout when a single tab is visible.
     * @param aTabs Define our dialog's tabs.
     */
    @JvmStatic
    fun show(
        aContext: Context,
        @StringRes aTitle: Int,
        aHideSingleTab: Boolean,
        vararg aTabs: DialogTab,
    ) {
        show(aContext, null, aContext.getString(aTitle), aHideSingleTab, *aTabs)
    }

    /**
     * Build and show a tabbed dialog based on the provided parameters.
     *
     * @param aContext The aContext requesting that dialog.
     * @param aTitle The dialog title.
     * @param aHideSingleTab Set to true to hide tab layout when a single tab is visible.
     * @param aTabs Define our dialog's tabs.
     */
    @JvmStatic
    fun show(
        aContext: Context,
        aIcon: Drawable?,
        aTitle: String?,
        aHideSingleTab: Boolean,
        vararg aTabs: DialogTab,
    ) {
        val builder = MaterialAlertDialogBuilder(aContext)

        // Inflate our layout
        val layout = aContext.inflater.inflate(R.layout.dialog_tabs, null)
        // Fetch the view we will need to use
        val titleView = layout.findViewById<TextView>(R.id.dialog_title)
        val tabLayout = layout.findViewById<TabLayout>(R.id.dialog_tab_layout)
        val pager = layout.findViewById<ViewPager>(R.id.dialog_viewpager)

        // Filter out invisible tabs
        val tabList = aTabs.filter(DialogTab::show)
        // Hide our tab layout out if needed
        tabLayout.isVisible = !(aHideSingleTab && tabList.count() == 1)
        // Create our dialog now as our adapter needs it
        val dialog = builder.create()
        // Set dialog title
        if (aTitle?.isNotEmpty() == true) {
            if (titleView != null) {
                // Use custom title if provided in our layout
                titleView.text = aTitle
            } else {
                // Otherwise you standard dialog title
                dialog.setTitle(aTitle)
            }
        } else {
            titleView?.isVisible = false
        }
        // Create our adapter which will be creating our tabs content
        pager.adapter = TabsPagerAdapter(aContext, dialog, tabList)
        // Hook-in our adapter with our tab layout
        tabLayout.setupWithViewPager(pager)
        // Add icons to our tabs
        var i = 0
        tabList.forEach {
            if (it.icon != 0) {
                tabLayout.getTabAt(i)?.setIcon(it.icon)
            }
            i++
        }
        // Our layout is setup, just hook it to our dialog
        dialog.setView(layout)
        setDialogSize(aContext, dialog)
        dialog.setIcon(aIcon)
        dialog.show()
        //builder.resizeAndShow()

        // We want our dialog to close after a configuration change since the resizing is not working properly.
        // We use a bit of magic there to achieve that.
        // After the initial layout we will be closing that dialog next time its size is changed.
        layout.onLayoutChange { layout.onSizeChange { dialog.dismiss() } }
    }

    @JvmStatic
    fun showPositiveNegativeDialog(
        aContext: Context,
        @StringRes title: Int,
        @StringRes message: Int,
        messageArguments: Array<Any>? = null,
        positiveButton: DialogItem,
        negativeButton: DialogItem,
        onCancel: () -> Unit,
    ) {
        val messageValue = if (messageArguments != null) {
            aContext.getString(message, *messageArguments)
        } else {
            aContext.getString(message)
        }
        MaterialAlertDialogBuilder(aContext).apply {
            setTitle(title)
            setMessage(messageValue)
            setOnCancelListener { onCancel() }
            setPositiveButton(positiveButton.title) { _, _ -> positiveButton.onClick() }
            setNegativeButton(negativeButton.title) { _, _ -> negativeButton.onClick() }
        }.resizeAndShow()
    }

    @JvmStatic
    fun showEditText(
        aContext: Context,
        @StringRes title: Int,
        @StringRes hint: Int,
        currentText: String?,
        @StringRes action: Int,
        textInputListener: (String) -> Unit,
    ) {
        val layout = LayoutInflater.from(aContext).inflate(R.layout.dialog_edit_text, null)
        val editText = layout.findViewById<EditText>(R.id.dialog_edit_text)

        editText.setHint(hint)
        if (currentText != null) {
            editText.setText(currentText)
        }

        val dialog = MaterialAlertDialogBuilder(aContext)
            .setTitle(title)
            .setView(layout)
            .setPositiveButton(action
            ) { _, _ -> textInputListener(editText.text.toString()) }
            .resizeAndShow()

        // Discard it on screen rotation as it's broken anyway
        layout.onLayoutChange { layout.onSizeChange { dialog.dismiss() } }
    }

    @JvmStatic
    fun setDialogSize(context: Context, dialog: Dialog) {
        var maxWidth = context.dimen(R.dimen.dialog_max_size)
        val padding = context.dimen(R.dimen.dialog_padding)
        val screenSize = DeviceUtils.getScreenWidth(context)
        if (maxWidth > screenSize - 2 * padding) {
            maxWidth = screenSize - 2 * padding
        }
        dialog.window?.setLayout(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * Show the custom dialog with the custom builder arguments applied.
     */
    fun showCustomDialog(
        aContext: Context,
        block: MaterialAlertDialogBuilder.(Context) -> Unit,
    ): Dialog {
        MaterialAlertDialogBuilder(aContext).apply {
            block(aContext)
            return resizeAndShow()
        }
    }

}
