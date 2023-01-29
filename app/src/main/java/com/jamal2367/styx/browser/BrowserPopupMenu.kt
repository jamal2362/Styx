/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.browser

import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.PopupWindow
import androidx.core.view.isVisible
import com.jamal2367.styx.R
import com.jamal2367.styx.adblock.AbpUserRules
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.database.bookmark.BookmarkRepository
import com.jamal2367.styx.databinding.PopupMenuBrowserBinding
import com.jamal2367.styx.di.HiltEntryPoint
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.utils.Utils
import com.jamal2367.styx.utils.isAppScheme
import com.jamal2367.styx.utils.isSpecialUrl
import dagger.hilt.android.EntryPointAccessors

class BrowserPopupMenu(
    layoutInflater: LayoutInflater,
    aBinding: PopupMenuBrowserBinding = inflate(layoutInflater),
) : PopupWindow(aBinding.root, WRAP_CONTENT, WRAP_CONTENT, true) {

    var iBinding: PopupMenuBrowserBinding = aBinding
    private var iIsIncognito = false

    private val bookmarkModel: BookmarkRepository
    val userPreferences: UserPreferences
    val abpUserRules: AbpUserRules

    init {
        elevation = 100F

        animationStyle = R.style.AnimationMenu

        setBackgroundDrawable(ColorDrawable())

        // Hide incognito menu item if we are already incognito
        iIsIncognito = (aBinding.root.context as BrowserActivity).isIncognito()
        if (iIsIncognito) {
            aBinding.menuItemIncognito.isVisible = false
            // No sessions in incognito mode
            aBinding.menuItemSessions.isVisible = false
            // No settings in incognito mode
            aBinding.menuItemSettings.isVisible = false
        }

        val hiltEntryPoint =
            EntryPointAccessors.fromApplication(iBinding.root.context.applicationContext,
                HiltEntryPoint::class.java)
        bookmarkModel = hiltEntryPoint.bookmarkRepository
        userPreferences = hiltEntryPoint.userPreferences
        abpUserRules = hiltEntryPoint.abpUserRules
    }

    fun onMenuItemClicked(menuView: View, onClick: () -> Unit) {
        menuView.setOnClickListener {
            onClick()
            dismiss()
        }
    }

    fun show(aAnchor: View) {

        (contentView.context as BrowserActivity).tabsManager.let { it ->
            // Set desktop mode checkbox according to current tab
            iBinding.menuItemDesktopMode.isChecked = it.currentTab?.desktopMode ?: false

            // Same with dark mode
            iBinding.menuItemDarkMode.isChecked = it.currentTab?.darkMode ?: false

            // And ad block
            iBinding.menuItemAdBlock.isChecked =
                it.currentTab?.url?.let { url -> !abpUserRules.isAllowed(Uri.parse(url)) } ?: false

            (contentView.context as BrowserActivity).tabsManager.let { tm ->
                tm.currentTab?.let { tab ->
                    (!(tab.url.isSpecialUrl() || tab.url.isAppScheme())).let {
                        // Those menu items won't be displayed for special URLs
                        iBinding.menuItemAddToHome.isVisible = it
                        iBinding.menuItemShare.isVisible = it
                        iBinding.menuItemCopyLink.isVisible = it
                        iBinding.menuItemPrint.isVisible = it
                        iBinding.menuItemFind.isVisible = it
                        iBinding.menuItemTranslate.isVisible = it
                        iBinding.menuItemReaderMode.isVisible = it
                        iBinding.menuItemDesktopMode.isVisible = it
                        iBinding.menuItemDarkMode.isVisible = it
                        iBinding.menuItemAdBlock.isVisible = it && userPreferences.adBlockEnabled
                        iBinding.menuItemAddBookmark.isVisible = it
                        iBinding.menuItemExit.isVisible =
                            userPreferences.menuShowExit || iIsIncognito
                        iBinding.divider2.isVisible = it
                        iBinding.divider3.isVisible = it
                        iBinding.divider4.isVisible = it
                    }
                }
            }

            if (userPreferences.navbar) {
                iBinding.header.visibility = GONE
                iBinding.divider1.visibility = GONE
                iBinding.menuShortcutRefresh.visibility = GONE
                iBinding.menuShortcutForward.visibility = GONE
                iBinding.menuShortcutBack.visibility = GONE
                iBinding.menuShortcutBookmarks.visibility = GONE
            }
        }

        // Get our anchor location
        val anchorLoc = IntArray(2)
        aAnchor.getLocationInWindow(anchorLoc)

        // Show our popup menu from the right side of the screen below our anchor
        val gravity =
            if (userPreferences.toolbarsBottom) Gravity.BOTTOM or Gravity.END else Gravity.TOP or Gravity.END
        val yOffset =
            if (userPreferences.toolbarsBottom) (contentView.context as BrowserActivity).iBinding.root.height - anchorLoc[1] - aAnchor.height else anchorLoc[1]
        showAtLocation(aAnchor, gravity,
            // Offset from the right screen edge
            Utils.dpToPx(10F),
            // Above our anchor
            yOffset)
    }

    companion object {

        fun inflate(layoutInflater: LayoutInflater): PopupMenuBrowserBinding {
            return PopupMenuBrowserBinding.inflate(layoutInflater)
        }

    }
}
