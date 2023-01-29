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

package com.jamal2367.styx.utils

import android.app.DownloadManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.R
import com.jamal2367.styx.database.HistoryEntry
import com.jamal2367.styx.dialog.BrowserDialog.setDialogSize
import com.jamal2367.styx.extensions.canScrollVertically
import com.jamal2367.styx.extensions.snackbar
import com.jamal2367.styx.preference.UserPreferences
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.lang.reflect.Method
import java.net.URI
import java.net.URISyntaxException
import java.text.SimpleDateFormat
import java.util.*

object Utils {
    private const val TAG = "Utils"

    lateinit var userPreferences: UserPreferences

    /**
     * Creates a new intent that can launch the email
     * app with a subject, address, body, and cc. It
     * is used to handle mail:to links.
     *
     * @param address the address to send the email to.
     * @param subject the subject of the email.
     * @param body    the body of the email.
     * @param cc      extra addresses to CC.
     * @return a valid intent.
     */
    fun newEmailIntent(
        address: String, subject: String?,
        body: String?, cc: String?,
    ): Intent {
        val intent = Intent(Intent.ACTION_SEND)
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        intent.putExtra(Intent.EXTRA_TEXT, body)
        intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        intent.putExtra(Intent.EXTRA_CC, cc)
        intent.type = "message/rfc822"
        return intent
    }

    /**
     * Workaround reversed layout bug: https://github.com/Slion/Fulguris/issues/212
     */
    fun fixScrollBug(aList: RecyclerView): Boolean {
        val lm = (aList.layoutManager as LinearLayoutManager)
        // Can't change stackFromEnd when computing layout or scrolling otherwise it throws an exception
        if (aList.isComputingLayout) {
            if (userPreferences.toolbarsBottom) {
                // Workaround reversed layout bug: https://github.com/Slion/Fulguris/issues/212
                if (lm.stackFromEnd != aList.canScrollVertically()) {
                    lm.stackFromEnd = !lm.stackFromEnd
                    return true
                }
            } else {
                // Make sure this is set properly when not using bottom toolbars
                // No need to check if the value is already set properly as this is already done internally
                lm.stackFromEnd = false
            }
        }

        return false
    }

    /**
     * Creates a dialog with only a title, message, and okay button.
     *
     * @param activity the activity needed to create a dialog.
     * @param title    the title of the dialog.
     * @param message  the message of the dialog.
     */
    fun createInformativeDialog(
        activity: AppCompatActivity,
        @StringRes title: Int,
        @StringRes message: Int,
    ) {
        val builder = MaterialAlertDialogBuilder(activity)
        builder.setTitle(title)
        builder.setMessage(message)
            .setCancelable(true)
            .setPositiveButton(activity.resources.getString(R.string.action_ok)
            ) { _: DialogInterface?, _: Int -> }
        val alert = builder.create()
        alert.show()
        setDialogSize(activity, alert)
    }

    /**
     * Converts Density Pixels (DP) to Pixels (PX).
     *
     * @param dp the number of density pixels to convert.
     * @return the number of pixels that the conversion generates.
     */
    @JvmStatic
    fun dpToPx(dp: Float): Int {
        val metrics = Resources.getSystem().displayMetrics
        return (dp * metrics.density + 0.5f).toInt()
    }

    /**
     * Extracts the domain name from a URL.
     * NOTE: Should be used for display only.
     *
     * @param url the URL to extract the domain from.
     * @return the domain name, or the URL if the domain
     * could not be extracted. The domain name may include
     * HTTPS if the URL is an SSL supported URL.
     */
    fun getDisplayDomainName(url: String?): String {
        var urls = url
        if (url == null || url.isEmpty()) return ""
        val index = url.indexOf('/', 8)
        if (index != -1) {
            urls = url.substring(0, index)
        }
        val uri: URI
        var domain: String?
        try {
            uri = URI(urls)
            domain = uri.host
        } catch (e: URISyntaxException) {
            Log.e(TAG, "Unable to parse URI", e)
            domain = null
        }
        if (domain == null || domain.isEmpty()) {
            return url
        }
        return if (domain.startsWith("www.")) domain.substring(4) else domain
    }

    /**
     * Extracts the domain name from `url`
     * by means of String manipulation
     * rather than using the [URI] class.
     *
     * @param url is non-null.
     * @return the domain name within `url`.
     */
    fun trimmedProtocolFromURL(url: String): String {
        var domainName = url
        val index = domainName.indexOf("://")
        if (index != -1) {
            // keep everything after the "://"
            domainName = domainName.substring(index + 3)
        }

        // check for and remove a preceding 'www'
        // followed by any sequence of characters (non-greedy)
        // followed by a '.'
        // from the beginning of the string
        domainName = domainName.replaceFirst("^www.*?\\.".toRegex(), "")
        return domainName
    }

    @Throws(IOException::class)
    fun createImageFile(): File {
        // Create an image file name
        val timeStamp = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val imageFileName = "JPEG_" + timeStamp + '_'
        val storageDir =
            BrowserApp.instance.applicationContext.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(imageFileName, ".jpg", storageDir)
    }

    /**
     * Quietly closes a closeable object like an InputStream or OutputStream without
     * throwing any errors or requiring you do do any checks.
     *
     * @param closeable the object to close
     */
    @JvmStatic
    fun close(closeable: Closeable?) {
        if (closeable == null) {
            return
        }
        try {
            closeable.close()
        } catch (e: IOException) {
            Log.e(TAG, "Unable to close closeable", e)
        }
    }

    /**
     * Creates a shortcut on the homescreen using the
     * [HistoryEntry] information that opens the
     * browser. The icon, URL, and title are used in
     * the creation of the shortcut.
     *
     * @param activity the activity needed to create
     * the intent and show a snackbar message
     * @param historyEntry     the HistoryEntity to create the shortcut from
     */
    @Suppress("DEPRECATION")
    fun createShortcut(activity: AppCompatActivity, historyEntry: HistoryEntry, favicon: Bitmap) {
        val shortcutIntent = Intent(Intent.ACTION_VIEW)
        shortcutIntent.data = Uri.parse(historyEntry.url)
        shortcutIntent.setPackage(activity.packageName)
        val title =
            if (TextUtils.isEmpty(historyEntry.title)) activity.getString(R.string.untitled) else historyEntry.title
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val addIntent = Intent()
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title)
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, favicon)
            addIntent.action = "com.android.launcher.action.INSTALL_SHORTCUT"
            activity.sendBroadcast(addIntent)
        } else {
            val shortcutManager = activity.getSystemService(ShortcutManager::class.java)
            if (shortcutManager.isRequestPinShortcutSupported) {
                val pinShortcutInfo = ShortcutInfo.Builder(activity,
                    "browser-shortcut-" + historyEntry.url.hashCode())
                    .setIntent(shortcutIntent)
                    .setIcon(Icon.createWithBitmap(favicon))
                    .setShortLabel(title)
                    .build()
                shortcutManager.requestPinShortcut(pinShortcutInfo, null)
            } else {
                activity.snackbar(R.string.shortcut_message_failed_to_add, Gravity.BOTTOM)
            }
        }
    }

    @JvmStatic
    fun guessFileExtension(filename: String): String? {
        val lastIndex = filename.indexOf('.') + 1
        return if (lastIndex > 0 && filename.length > lastIndex) {
            filename.substring(lastIndex)
        } else null
    }

    /**
     * Construct an intent to display downloads folder either by using a file browser application
     * or using system download manager.
     *
     * @param aContext
     * @param aDownloadFolder
     * @return
     */
    @JvmStatic
    fun getIntentForDownloads(aContext: Context, aDownloadFolder: String?): Intent {
        // This is the solution from there: https://stackoverflow.com/a/26651827/3969362
        // Build an intent to open our download folder in a file explorer app
        val intent =
            Intent(Intent.ACTION_VIEW).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.setDataAndType(Uri.parse(aDownloadFolder), "resource/folder")
        // Check that there is an app activity handling that intent on our system
        return if (intent.resolveActivityInfo(aContext.packageManager, 0) != null) {
            // Yes there is one use it
            intent
        } else {
            // Just launch system download manager activity if no custom file explorer found
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    }

    fun adjustBottomSheet(aDialog: BottomSheetDialog) {
        // Get our private class
        val classEdgeToEdgeCallback =
            Class.forName("com.google.android.material.bottomsheet.BottomSheetDialog\$EdgeToEdgeCallback")
        // Get our private method
        val methodSetPaddingForPosition: Method =
            classEdgeToEdgeCallback.getDeclaredMethod("setPaddingForPosition", View::class.java)
        methodSetPaddingForPosition.isAccessible = true
        // Get private field containing our EdgeToEdgeCallback instance
        val fieldEdgeToEdgeCallback =
            BottomSheetDialog::class.java.getDeclaredField("edgeToEdgeCallback")
        fieldEdgeToEdgeCallback.isAccessible = true
        // Get our bottom sheet view field
        val fieldBottomField = BottomSheetDialog::class.java.getDeclaredField("bottomSheet")
        fieldBottomField.isAccessible = true
        // Eventually call setPaddingForPosition from EdgeToEdgeCallback instance passing bottom sheet view as parameter
        methodSetPaddingForPosition.invoke(fieldEdgeToEdgeCallback.get(aDialog),
            fieldBottomField.get(aDialog))
    }
}
