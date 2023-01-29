/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.download

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.DialogInterface
import android.os.Build
import android.text.format.Formatter
import android.webkit.DownloadListener
import androidx.appcompat.app.AppCompatActivity
import com.anthonycr.grant.PermissionsManager
import com.anthonycr.grant.PermissionsResultAction
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.styx.BrowserApp
import com.jamal2367.styx.R
import com.jamal2367.styx.browser.activity.BrowserActivity
import com.jamal2367.styx.di.HiltEntryPoint
import com.jamal2367.styx.dialog.BrowserDialog.setDialogSize
import com.jamal2367.styx.log.Logger
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.utils.DownloadUtils.guessFileName
import dagger.hilt.android.EntryPointAccessors


class StyxDownloadListener(context: Activity) : DownloadListener {
    private val mActivity: Activity

    // Could not get injection working in broadcast receiver
    private val hiltEntryPoint =
        EntryPointAccessors.fromApplication(BrowserApp.instance.applicationContext,
            HiltEntryPoint::class.java)

    val userPreferences: UserPreferences = hiltEntryPoint.userPreferences
    val downloadHandler: DownloadHandler = hiltEntryPoint.downloadHandler
    val logger: Logger = hiltEntryPoint.logger

    override fun onDownloadStart(
        url: String, userAgent: String,
        contentDisposition: String, mimetype: String, contentLength: Long,
    ) {
        if (Build.VERSION.SDK_INT <= 28) {
            PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(mActivity,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE),
                object : PermissionsResultAction() {
                    override fun onGranted() {
                        val fileName = guessFileName(contentDisposition, null, url, mimetype)
                        val downloadSize: String = if (contentLength > 0) {
                            Formatter.formatFileSize(mActivity, contentLength)
                        } else {
                            mActivity.getString(R.string.unknown_size)
                        }

                        val dialogClickListener =
                            DialogInterface.OnClickListener { _: DialogInterface?, which: Int ->
                                when (which) {
                                    DialogInterface.BUTTON_POSITIVE ->
                                        downloadHandler.onDownloadStartNoStream(mActivity as AppCompatActivity,
                                            userPreferences,
                                            url,
                                            userAgent,
                                            contentDisposition,
                                            mimetype)
                                    DialogInterface.BUTTON_NEGATIVE -> {
                                    }
                                }
                            }

                        if (userPreferences.showDownloadConfirmation) {
                            val builder = MaterialAlertDialogBuilder(mActivity) // dialog
                            val message =
                                mActivity.getString(R.string.dialog_download, downloadSize)
                            val dialog: Dialog = builder.setTitle(fileName)
                                .setMessage(message)
                                .setPositiveButton(mActivity.resources.getString(R.string.action_download),
                                    dialogClickListener)
                                .setNegativeButton(mActivity.resources.getString(R.string.action_cancel),
                                    dialogClickListener).show()
                            setDialogSize(mActivity, dialog)
                            logger.log(TAG, "Downloading: $fileName")
                        } else {
                            downloadHandler.onDownloadStartNoStream(mActivity as AppCompatActivity,
                                userPreferences,
                                url,
                                userAgent,
                                contentDisposition,
                                mimetype)
                        }
                    }

                    override fun onDenied(permission: String) {
                        //
                    }
                })

            // Some download link spawn an empty tab, just close it then
            if (mActivity is BrowserActivity) {
                mActivity.closeCurrentTabIfEmpty()
            }
        } else {
            val fileName = guessFileName(contentDisposition, null, url, mimetype)
            val downloadSize: String = if (contentLength > 0) {
                Formatter.formatFileSize(mActivity, contentLength)
            } else {
                mActivity.getString(R.string.unknown_size)
            }

            val dialogClickListener =
                DialogInterface.OnClickListener { _: DialogInterface?, which: Int ->
                    when (which) {
                        DialogInterface.BUTTON_POSITIVE ->
                            downloadHandler.onDownloadStartNoStream(mActivity as AppCompatActivity,
                                userPreferences,
                                url,
                                userAgent,
                                contentDisposition,
                                mimetype)
                        DialogInterface.BUTTON_NEGATIVE -> {
                        }
                    }
                }

            if (userPreferences.showDownloadConfirmation) {
                val builder = MaterialAlertDialogBuilder(mActivity) // dialog
                val message = mActivity.getString(R.string.dialog_download, downloadSize)
                val dialog: Dialog = builder.setTitle(fileName)
                    .setMessage(message)
                    .setPositiveButton(mActivity.resources.getString(R.string.action_download),
                        dialogClickListener)
                    .setNegativeButton(mActivity.resources.getString(R.string.action_cancel),
                        dialogClickListener).show()
                setDialogSize(mActivity, dialog)
                logger.log(TAG, "Downloading: $fileName")
            } else {
                downloadHandler.onDownloadStartNoStream(mActivity as AppCompatActivity,
                    userPreferences,
                    url,
                    userAgent,
                    contentDisposition,
                    mimetype)
            }

            // Some download link spawn an empty tab, just close it then
            if (mActivity is BrowserActivity) {
                mActivity.closeCurrentTabIfEmpty()
            }
        }
    }

    companion object {
        private const val TAG = "StyxDownloader"
    }

    init {
        mActivity = context
    }
}
