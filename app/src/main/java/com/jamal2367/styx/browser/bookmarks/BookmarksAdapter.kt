/*
 * The contents of this file are subject to the Common Public Attribution License Version 1.0.
 * (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://github.com/Slion/Fulguris/blob/main/LICENSE.CPAL-1.0.
 * The License is based on the Mozilla Public License Version 1.1, but Sections 14 and 15 have been
 * added to cover use of software over a computer network and provide for limited attribution for
 * the Original Developer. In addition, Exhibit A has been modified to be consistent with Exhibit B.
 *
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * The Original Code is Fulguris.
 *
 * The Original Developer is the Initial Developer.
 * The Initial Developer of the Original Code is Stéphane Lenclud.
 *
 * All portions of the code written by Stéphane Lenclud are Copyright © 2020 Stéphane Lenclud.
 * All Rights Reserved.
 */

package com.jamal2367.styx.browser.bookmarks

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.jamal2367.styx.R
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.controller.UIController
import com.jamal2367.styx.database.Bookmark
import com.jamal2367.styx.database.bookmark.BookmarkRepository
import com.jamal2367.styx.extensions.drawable
import com.jamal2367.styx.extensions.setImageForTheme
import com.jamal2367.styx.favicon.FaviconModel
import com.jamal2367.styx.utils.ItemDragDropSwipeAdapter
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class BookmarksAdapter(
    val context: Context,
    val uiController: UIController,
    private val bookmarksRepository: BookmarkRepository,
    private val faviconModel: FaviconModel,
    private val networkScheduler: Scheduler,
    private val mainScheduler: Scheduler,
    private val databaseScheduler: Scheduler,
    private val iShowBookmarkMenu: (Bookmark) -> Boolean,
    private val iOpenBookmark: (Bookmark) -> Unit,
) : RecyclerView.Adapter<BookmarkViewHolder>(), ItemDragDropSwipeAdapter {

    private var bookmarks: List<BookmarksViewModel> = listOf()
    private val faviconFetchSubscriptions = ConcurrentHashMap<String, Disposable>()
    private val folderIcon = context.drawable(R.drawable.outline_folder_special_24)
    private val webpageIcon = context.drawable(R.drawable.ic_webpage)

    fun itemAt(position: Int): BookmarksViewModel = bookmarks[position]

    fun deleteItem(item: BookmarksViewModel) {
        val newList = bookmarks - item
        updateItems(newList)
    }

    /**
     *
     */
    fun updateItems(newList: List<BookmarksViewModel>) {
        val oldList = bookmarks
        bookmarks = newList

        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = oldList.size

            override fun getNewListSize() = bookmarks.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                oldList[oldItemPosition].bookmark.url == bookmarks[newItemPosition].bookmark.url

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int) =
                oldList[oldItemPosition] == bookmarks[newItemPosition]
        })

        diffResult.dispatchUpdatesTo(this)
    }

    fun cleanupSubscriptions() {
        for (subscription in faviconFetchSubscriptions.values) {
            subscription.dispose()
        }
        faviconFetchSubscriptions.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookmarkViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val itemView = inflater.inflate(R.layout.bookmark_list_item, parent, false)

        return BookmarkViewHolder(itemView, this, iShowBookmarkMenu, iOpenBookmark)
    }

    override fun onBindViewHolder(holder: BookmarkViewHolder, position: Int) {
        holder.itemView.jumpDrawablesToCurrentState()

        val viewModel = bookmarks[position]
        holder.txtTitle.text = viewModel.bookmark.title

        val url = viewModel.bookmark.url
        holder.favicon.tag = url

        viewModel.icon?.let {
            holder.favicon.setImageBitmap(it)
            return
        }

        val imageDrawable = when (viewModel.bookmark) {
            is Bookmark.Folder -> folderIcon
            is Bookmark.Entry -> webpageIcon.also {
                faviconFetchSubscriptions[url]?.dispose()
                faviconFetchSubscriptions[url] = faviconModel
                    .faviconForUrl(url, viewModel.bookmark.title, false)
                    .subscribeOn(networkScheduler)
                    .observeOn(mainScheduler)
                    .subscribeBy(
                        onSuccess = { bitmap ->
                            viewModel.icon = bitmap
                            if (holder.favicon.tag == url) {
                                val ba = context as BrowserActivity
                                holder.favicon.setImageForTheme(bitmap, ba.useDarkTheme)
                            }
                        }
                    )
            }
        }

        holder.favicon.setImageDrawable(imageDrawable)
    }

    override fun getItemCount() = bookmarks.size

    /**
     * Implements [ItemDragDropSwipeAdapter.onItemMove]
     * An item was was moved through drag & drop
     */
    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        val source = bookmarks[fromPosition].bookmark
        val destination = bookmarks[toPosition].bookmark
        // We can only swap bookmark entries not folders
        if (!(source is Bookmark.Entry && destination is Bookmark.Entry)) {
            // Folder are shown last in our list for now so we just can't order them
            return false
        }

        // Swap local list positions
        Collections.swap(bookmarks, fromPosition, toPosition)

        // Due to our database definition we need edit position of each bookmarks in current folder
        // Go through our list and edit position as needed
        var position = 0
        bookmarks.toList().forEach { b ->
            if (b.bookmark is Bookmark.Entry) {
                if (b.bookmark.position != position || position == fromPosition || position == toPosition) {
                    val editedItem = Bookmark.Entry(
                        title = b.bookmark.title,
                        url = b.bookmark.url,
                        folder = b.bookmark.folder,
                        position = position
                    )

                    position++

                    bookmarksRepository.editBookmark(b.bookmark, editedItem)
                        .subscribeOn(databaseScheduler)
                        .observeOn(mainScheduler).let {
                            if (position != bookmarks.count()) {
                                it.subscribe()
                            } else {
                                // Broadcast update only for our last operation
                                // Though I have no idea if our operations are FIFO
                                it.subscribe(uiController::handleBookmarksChange)
                            }
                        }
                }
            }
        }

        // Tell base class an item was moved
        notifyItemMoved(fromPosition, toPosition)

        return true
    }

    /**
     * Implements [ItemDragDropSwipeAdapter.onItemDismiss]
     */
    override fun onItemDismiss(position: Int) {

    }
}
