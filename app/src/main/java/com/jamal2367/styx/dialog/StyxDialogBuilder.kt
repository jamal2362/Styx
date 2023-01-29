/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.dialog

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.view.Gravity
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.styx.MainActivity
import com.jamal2367.styx.R
import com.jamal2367.styx.controller.UIController
import com.jamal2367.styx.database.Bookmark
import com.jamal2367.styx.database.asFolder
import com.jamal2367.styx.database.bookmark.BookmarkRepository
import com.jamal2367.styx.database.downloads.DownloadsRepository
import com.jamal2367.styx.database.history.HistoryRepository
import com.jamal2367.styx.di.DatabaseScheduler
import com.jamal2367.styx.di.MainScheduler
import com.jamal2367.styx.download.DownloadHandler
import com.jamal2367.styx.extensions.*
import com.jamal2367.styx.html.bookmark.BookmarkPageFactory
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.utils.IntentUtils
import com.jamal2367.styx.utils.isBookmarkUrl
import dagger.Reusable
import io.reactivex.Scheduler
import io.reactivex.rxkotlin.subscribeBy
import java.util.*
import javax.inject.Inject

/**
 * A builder of various dialogs.
 */
@Reusable
class StyxDialogBuilder @Inject constructor(
    private val bookmarkManager: BookmarkRepository,
    private val downloadsModel: DownloadsRepository,
    private val historyModel: HistoryRepository,
    private val userPreferences: UserPreferences,
    private val downloadHandler: DownloadHandler,
    private val clipboardManager: ClipboardManager,
    @DatabaseScheduler private val databaseScheduler: Scheduler,
    @MainScheduler private val mainScheduler: Scheduler,
) {

    enum class NewTab {
        FOREGROUND,
        BACKGROUND,
        INCOGNITO
    }

    /**
     * Show bookmark context menu.
     */
    @SuppressLint("CheckResult")
    fun showLongPressedDialogForBookmarkUrl(
        activity: AppCompatActivity,
        uiController: UIController,
        url: String,
    ) {
        if (url.isBookmarkUrl()) {
            val uri = url.toUri()
            val filename =
                requireNotNull(uri.lastPathSegment) { "Last segment should always exist for bookmark file" }
            val folderTitle =
                filename.substring(0, filename.length - BookmarkPageFactory.FILENAME.length - 1)
            showBookmarkFolderLongPressedDialog(activity, uiController, folderTitle.asFolder())
        } else {
            bookmarkManager.findBookmarkForUrl(url)
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribe { historyItem ->
                    showLongPressedDialogForBookmarkUrl(activity, uiController, historyItem)
                }
        }
    }

    @SuppressLint("CheckResult")
    fun showLongPressedDialogForBookmarkUrl(
        activity: AppCompatActivity,
        uiController: UIController,
        entry: Bookmark.Entry,
    ) = BrowserDialog.show(activity,
        null,
        "",
        false,
        DialogTab(show = true,
            icon = R.drawable.ic_bookmark_border,
            title = R.string.action_bookmarks,
            items = arrayOf(
                DialogItem(title = R.string.dialog_open_new_tab) {
                    uiController.handleNewTab(NewTab.FOREGROUND, entry.url)
                },
                DialogItem(title = R.string.dialog_open_background_tab) {
                    uiController.handleNewTab(NewTab.BACKGROUND, entry.url)
                },
                DialogItem(
                    title = R.string.dialog_open_incognito_tab,
                    show = activity is MainActivity
                ) {
                    uiController.handleNewTab(NewTab.INCOGNITO, entry.url)
                },
                DialogItem(title = R.string.action_share) {
                    IntentUtils(activity).shareUrl(entry.url, entry.title)
                },
                DialogItem(title = R.string.dialog_copy_link) {
                    clipboardManager.copyToClipboard(entry.url)
                    (activity).snackbar(R.string.message_link_copied,
                        if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                },
                DialogItem(title = R.string.dialog_remove_bookmark) {
                    bookmarkManager.deleteBookmark(entry)
                        .subscribeOn(databaseScheduler)
                        .observeOn(mainScheduler)
                        .subscribe { success ->
                            if (success) {
                                uiController.handleBookmarkDeleted(entry)
                                (activity).snackbar(R.string.action_remove_bookmark,
                                    if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                            }
                        }
                },
                DialogItem(title = R.string.dialog_edit_bookmark) {
                    showEditBookmarkDialog(activity, uiController, entry)
                })))

    /**
     * Show the appropriated dialog for the long pressed link.
     *
     * @param activity used to show the dialog
     */
    @SuppressLint("CheckResult")
    fun showLongPressedDialogForDownloadUrl(
        activity: AppCompatActivity,
        uiController: UIController,
    ) = BrowserDialog.show(activity, R.string.action_downloads,
        DialogItem(title = R.string.dialog_delete_all_downloads) {
            downloadsModel.deleteAllDownloads()
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribe(uiController::handleDownloadDeleted)
        })

    /**
     * Show the add bookmark dialog. Shows a dialog with the title and URL pre-populated.
     */
    @SuppressLint("CheckResult")
    fun showAddBookmarkDialog(
        activity: AppCompatActivity,
        uiController: UIController,
        entry: Bookmark.Entry,
    ) {
        val editBookmarkDialog = MaterialAlertDialogBuilder(activity)
        editBookmarkDialog.setTitle(R.string.action_add_bookmark)
        val layout = View.inflate(activity, R.layout.dialog_edit_bookmark, null)
        val getTitle = layout.findViewById<EditText>(R.id.bookmark_title)
        getTitle.setText(entry.title)
        val getUrl = layout.findViewById<EditText>(R.id.bookmark_url)
        getUrl.setText(entry.url)
        val getFolder = layout.findViewById<AutoCompleteTextView>(R.id.bookmark_folder)
        getFolder.setHint(R.string.folder)
        getFolder.setText(entry.folder.title)

        bookmarkManager.getFolderNames()
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe { folders ->
                val suggestionsAdapter = ArrayAdapter(activity,
                    android.R.layout.simple_dropdown_item_1line, folders)
                getFolder.threshold = 1
                getFolder.onFocusGained { getFolder.showDropDown(); mainScheduler.scheduleDirect { getFolder.selectAll() } }
                getFolder.setAdapter(suggestionsAdapter)
                editBookmarkDialog.setView(layout)
                editBookmarkDialog.setPositiveButton(activity.getString(R.string.action_ok)) { _, _ ->
                    val folder = getFolder.text.toString().asFolder()
                    // We need to query bookmarks in destination folder to be able to count them and set our new bookmark position
                    bookmarkManager.getBookmarksFromFolderSorted(folder.title).subscribeBy(
                        onSuccess = {
                            val editedItem = Bookmark.Entry(
                                title = getTitle.text.toString(),
                                url = getUrl.text.toString(),
                                folder = folder,
                                // Append new bookmark to existing ones by setting its position properly
                                position = it.count()
                            )
                            bookmarkManager.addBookmarkIfNotExists(editedItem)
                                .subscribeOn(databaseScheduler)
                                .observeOn(mainScheduler)
                                .subscribeBy(
                                    onSuccess = { success ->
                                        if (success) {
                                            uiController.handleBookmarksChange()
                                            (activity).snackbar(R.string.message_bookmark_added,
                                                if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                                        } else {
                                            (activity).snackbar(R.string.message_bookmark_not_added,
                                                if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                                        }
                                    }
                                )
                        }
                    )
                }
                editBookmarkDialog.setNegativeButton(R.string.action_cancel) { _, _ -> }
                val dialog = editBookmarkDialog.resizeAndShow()
                // Discard it on screen rotation as it's broken anyway
                layout.onLayoutChange { layout.onSizeChange { dialog.dismiss() } }
            }
    }

    @SuppressLint("CheckResult")
    private fun showEditBookmarkDialog(
        activity: AppCompatActivity,
        uiController: UIController,
        entry: Bookmark.Entry,
    ) {
        val editBookmarkDialog = MaterialAlertDialogBuilder(activity)
        editBookmarkDialog.setTitle(R.string.title_edit_bookmark)
        val dialogLayout = View.inflate(activity, R.layout.dialog_edit_bookmark, null)
        val getTitle = dialogLayout.findViewById<EditText>(R.id.bookmark_title)
        getTitle.setText(entry.title)
        val getUrl = dialogLayout.findViewById<EditText>(R.id.bookmark_url)
        getUrl.setText(entry.url)
        val getFolder = dialogLayout.findViewById<AutoCompleteTextView>(R.id.bookmark_folder)
        getFolder.setHint(R.string.folder)
        getFolder.setText(entry.folder.title)

        bookmarkManager.getFolderNames()
            .subscribeOn(databaseScheduler)
            .observeOn(mainScheduler)
            .subscribe { folders ->
                val suggestionsAdapter = ArrayAdapter(activity,
                    android.R.layout.simple_dropdown_item_1line, folders)
                getFolder.threshold = 1
                getFolder.onFocusGained { getFolder.showDropDown(); mainScheduler.scheduleDirect { getFolder.selectAll() } }
                getFolder.setAdapter(suggestionsAdapter)
                editBookmarkDialog.setView(dialogLayout)
                editBookmarkDialog.setPositiveButton(activity.getString(R.string.action_ok)) { _, _ ->
                    val folder = getFolder.text.toString().asFolder()
                    if (folder.title != entry.folder.title) {
                        // We moved to a new folder we need to adjust our position then
                        bookmarkManager.getBookmarksFromFolderSorted(folder.title).subscribeBy(
                            onSuccess = {
                                val editedItem = Bookmark.Entry(
                                    title = getTitle.text.toString(),
                                    url = getUrl.text.toString(),
                                    folder = folder,
                                    position = it.count()
                                )
                                bookmarkManager.editBookmark(entry, editedItem)
                                    .subscribeOn(databaseScheduler)
                                    .observeOn(mainScheduler)
                                    .subscribe(uiController::handleBookmarksChange)
                            }
                        )
                    } else {
                        // We remain in the same folder just use existing position then
                        val editedItem = Bookmark.Entry(
                            title = getTitle.text.toString(),
                            url = getUrl.text.toString(),
                            folder = folder,
                            position = entry.position
                        )
                        bookmarkManager.editBookmark(entry, editedItem)
                            .subscribeOn(databaseScheduler)
                            .observeOn(mainScheduler)
                            .subscribe(uiController::handleBookmarksChange)
                    }
                    (activity).snackbar(R.string.action_bookmark_edited,
                        if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                }
                editBookmarkDialog.resizeAndShow()
            }
    }

    @SuppressLint("CheckResult")
    fun showBookmarkFolderLongPressedDialog(
        activity: AppCompatActivity,
        uiController: UIController,
        folder: Bookmark.Folder,
    ) = BrowserDialog.show(activity,
        null,
        "",
        false,
        DialogTab(show = true,
            icon = R.drawable.ic_folder,
            title = R.string.action_folder,
            items = arrayOf(
                DialogItem(title = R.string.dialog_rename_folder) {
                    showRenameFolderDialog(activity, uiController, folder)
                },
                DialogItem(title = R.string.dialog_remove_folder) {
                    bookmarkManager.deleteFolder(folder.title)
                        .subscribeOn(databaseScheduler)
                        .observeOn(mainScheduler)
                        .subscribe {
                            uiController.handleBookmarkDeleted(folder)
                        }
                })))

    @SuppressLint("CheckResult")
    private fun showRenameFolderDialog(
        activity: AppCompatActivity,
        uiController: UIController,
        folder: Bookmark.Folder,
    ) = BrowserDialog.showEditText(activity,
        R.string.title_rename_folder,
        R.string.hint_title,
        folder.title,
        R.string.action_ok) { text ->
        if (text.isNotBlank()) {
            val oldTitle = folder.title
            bookmarkManager.renameFolder(oldTitle, text)
                .subscribeOn(databaseScheduler)
                .observeOn(mainScheduler)
                .subscribe(uiController::handleBookmarksChange)
        }
    }

    /**
     * Menu shown when doing a long press on an history list item.
     */
    @SuppressLint("CheckResult")
    fun showLongPressedHistoryLinkDialog(
        activity: AppCompatActivity,
        uiController: UIController,
        url: String,
    ) = BrowserDialog.show(activity,
        null,
        "",
        false,
        DialogTab(show = true,
            icon = R.drawable.ic_history,
            title = R.string.action_history,
            items = arrayOf(
                DialogItem(title = R.string.dialog_open_new_tab) {
                    uiController.handleNewTab(NewTab.FOREGROUND, url)
                },
                DialogItem(title = R.string.dialog_open_background_tab) {
                    uiController.handleNewTab(NewTab.BACKGROUND, url)
                },
                DialogItem(
                    title = R.string.dialog_open_incognito_tab,
                    show = activity is MainActivity
                ) {
                    uiController.handleNewTab(NewTab.INCOGNITO, url)
                },
                DialogItem(title = R.string.action_share) {
                    IntentUtils(activity).shareUrl(url, null)
                },
                DialogItem(title = R.string.dialog_copy_link) {
                    clipboardManager.copyToClipboard(url)
                    (activity).snackbar(R.string.message_link_copied,
                        if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                },
                DialogItem(title = R.string.dialog_remove_from_history) {
                    historyModel.deleteHistoryEntry(url)
                        .subscribeOn(databaseScheduler)
                        .observeOn(mainScheduler)
                        .subscribe(uiController::handleHistoryChange)
                    (activity).snackbar(R.string.dialog_removed_from_history,
                        if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                })))

    /**
     * Show a dialog allowing the user to action either a link or an image.
     */
    fun showLongPressLinkImageDialog(
        activity: AppCompatActivity,
        uiController: UIController,
        linkUrl: String,
        imageUrl: String,
        text: String?,
        userAgent: String,
        showLinkTab: Boolean,
        showImageTab: Boolean,
    ) = BrowserDialog.show(
        activity, null, "", false,
        //Link tab
        DialogTab(show = showLinkTab,
            icon = R.drawable.ic_link,
            title = R.string.button_link,
            items = arrayOf(DialogItem(title = R.string.dialog_open_new_tab) {
                uiController.handleNewTab(NewTab.FOREGROUND, linkUrl)
            },
                DialogItem(title = R.string.dialog_open_background_tab) {
                    uiController.handleNewTab(NewTab.BACKGROUND, linkUrl)
                },
                DialogItem(
                    title = R.string.dialog_open_incognito_tab,
                    show = activity is MainActivity
                ) {
                    uiController.handleNewTab(NewTab.INCOGNITO, linkUrl)
                },
                DialogItem(title = R.string.action_share) {
                    IntentUtils(activity).shareUrl(linkUrl, null)
                },
                // Show copy text dialog item if we have some text
                DialogItem(title = R.string.dialog_copy_text, show = !text.isNullOrEmpty()) {
                    if (!text.isNullOrEmpty()) {
                        clipboardManager.copyToClipboard(text)
                        activity.snackbar(R.string.message_text_copied,
                            if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                    }
                },
                // Show copy link URL last
                DialogItem(title = R.string.dialog_copy_link, text = linkUrl) {
                    clipboardManager.copyToClipboard(linkUrl)
                    activity.snackbar(R.string.message_link_copied,
                        if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                }
            )),
        // Image tab
        DialogTab(show = showImageTab, icon = R.drawable.ic_image, title = R.string.button_image,
            items = arrayOf(DialogItem(title = R.string.dialog_open_new_tab) {
                uiController.handleNewTab(NewTab.FOREGROUND, imageUrl)
            },
                DialogItem(title = R.string.dialog_open_background_tab) {
                    uiController.handleNewTab(NewTab.BACKGROUND, imageUrl)
                },
                DialogItem(
                    title = R.string.dialog_open_incognito_tab,
                    show = activity is MainActivity
                ) {
                    uiController.handleNewTab(NewTab.INCOGNITO, imageUrl)
                },
                DialogItem(title = R.string.action_share) {
                    IntentUtils(activity).shareUrl(imageUrl, null)
                },
                DialogItem(title = R.string.dialog_download_image,
                    // Do not show download option for data URL as we don't support that for now
                    show = !URLUtil.isDataUrl(imageUrl)) {
                    val mimeType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(imageUrl)
                            .lowercase(Locale.ROOT))
                    downloadHandler.onDownloadStartNoStream(activity,
                        userPreferences,
                        imageUrl,
                        userAgent,
                        "attachment",
                        mimeType ?: "image/png")
                },
                DialogItem(title = R.string.dialog_copy_link, text = imageUrl) {
                    clipboardManager.copyToClipboard(imageUrl)
                    activity.snackbar(R.string.message_link_copied,
                        if (userPreferences.toolbarsBottom || userPreferences.navbar) Gravity.TOP else Gravity.BOTTOM)
                }
            )),
    )
}
