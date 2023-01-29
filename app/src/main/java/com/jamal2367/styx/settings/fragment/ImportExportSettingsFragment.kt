/*
 * Copyright © 2020-2021 Jamal Rothfuchs
 * Copyright © 2020-2021 Stéphane Lenclud
 * Copyright © 2015 Anthony Restaino
 */

package com.jamal2367.styx.settings.fragment

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.styx.R
import com.jamal2367.styx.bookmark.LegacyBookmarkImporter
import com.jamal2367.styx.bookmark.NetscapeBookmarkFormatImporter
import com.jamal2367.styx.browser.TabsManager
import com.jamal2367.styx.browser.sessions.Session
import com.jamal2367.styx.database.bookmark.BookmarkExporter
import com.jamal2367.styx.database.bookmark.BookmarkRepository
import com.jamal2367.styx.di.DatabaseScheduler
import com.jamal2367.styx.di.MainScheduler
import com.jamal2367.styx.dialog.BrowserDialog
import com.jamal2367.styx.dialog.DialogItem
import com.jamal2367.styx.extensions.fileName
import com.jamal2367.styx.extensions.snackbar
import com.jamal2367.styx.log.Logger
import com.jamal2367.styx.settings.activity.SettingsActivity
import com.jamal2367.styx.utils.Utils
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.Scheduler
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class ImportExportSettingsFragment : AbstractSettingsFragment() {

    @Inject
    internal lateinit var bookmarkRepository: BookmarkRepository
    @Inject
    internal lateinit var application: Application
    @Inject
    internal lateinit var netscapeBookmarkFormatImporter: NetscapeBookmarkFormatImporter
    @Inject
    internal lateinit var legacyBookmarkImporter: LegacyBookmarkImporter
    @Inject
    @DatabaseScheduler
    internal lateinit var databaseScheduler: Scheduler
    @Inject
    @MainScheduler
    internal lateinit var mainScheduler: Scheduler
    @Inject
    internal lateinit var logger: Logger

    @Inject
    lateinit var tabsManager: TabsManager

    private var importSubscription: Disposable? = null
    private var exportSubscription: Disposable? = null
    private var bookmarksSortSubscription: Disposable? = null

    private lateinit var sessionsCategory: PreferenceCategory

    override fun providePreferencesXmlResource() = R.xml.preference_import

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        clickablePreference(preference = SETTINGS_EXPORT, onClick = this::exportBookmarks)
        clickablePreference(preference = SETTINGS_IMPORT, onClick = this::importBookmarks)
        clickablePreference(preference = SETTINGS_DELETE_BOOKMARKS,
            onClick = this::deleteAllBookmarks)
        clickablePreference(preference = SETTINGS_SETTINGS_EXPORT,
            onClick = this::requestSettingsExport)
        clickablePreference(preference = SETTINGS_SETTINGS_IMPORT,
            onClick = this::requestSettingsImport)
        clickablePreference(preference = SETTINGS_DELETE_SETTINGS, onClick = this::clearSettings)

        // Sessions
        clickablePreference(preference = getString(R.string.pref_key_sessions_import),
            onClick = this::showSessionImportDialog)

        sessionsCategory = findPreference(getString(R.string.pref_key_session_export_category))!!

        // Populate our sessions
        tabsManager.iSessions.forEach { s -> addPreferenceSessionExport(s) }
    }

    /**
     * Add a preference corresponding to the give session.
     */
    private fun addPreferenceSessionExport(aSession: Session) {
        val pref = Preference(requireContext())
        val tab = resources.getString(R.string.tab)
        val tabs = resources.getString(R.string.tabs)

        // Show tab count if any
        if (aSession.tabCount > 1) {
            pref.title = aSession.name + " - (" + aSession.tabCount + " " + tabs + ")"
        } else {
            pref.title = aSession.name + " - (" + aSession.tabCount + " " + tab + ")"
        }

        pref.icon = ResourcesCompat.getDrawable(resources, R.drawable.ic_tab, activity?.theme)
        pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            showSessionExportDialog(aSession.name,
                aSession.tabCount,
                tabsManager.fileFromSessionName(aSession.name))
            true
        }
        sessionsCategory.addPreference(pref)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        exportSubscription?.dispose()
        importSubscription?.dispose()
        bookmarksSortSubscription?.dispose()
    }

    override fun onDestroy() {
        super.onDestroy()

        exportSubscription?.dispose()
        importSubscription?.dispose()
        bookmarksSortSubscription?.dispose()
    }

    @Suppress("DEPRECATION")
    private fun requestSettingsImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, IMPORT_SETTINGS)
    }

    @Suppress("DEPRECATION")
    private fun requestSettingsExport() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            var timeStamp = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val dateFormat = SimpleDateFormat("-yyyy-MM-dd-(HH:mm)", Locale.US)
                timeStamp = dateFormat.format(Date())
            }
            // That is a neat feature as it guarantee no file will be overwritten.
            putExtra(Intent.EXTRA_TITLE, "StyxSettings$timeStamp.txt")
        }
        startActivityForResult(intent, EXPORT_SETTINGS)
    }

    private fun clearSettings() {
        val builder = MaterialAlertDialogBuilder(activity as Activity)
        builder.setTitle(getString(R.string.action_delete))
        builder.setMessage(getString(R.string.clean_settings))


        builder.setPositiveButton(resources.getString(R.string.action_ok)) { _, _ ->
            (activity as AppCompatActivity).snackbar(R.string.settings_reseted)

            Handler(Looper.getMainLooper()).postDelayed({
                (activity?.getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                    .clearApplicationUserData()
            }, 500)
        }
        builder.setNegativeButton(resources.getString(R.string.action_cancel)) { _, _ ->

        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(true)
        alertDialog.show()
    }

    private fun exportSettings(uri: Uri) {
        val userPref = PreferenceManager.getDefaultSharedPreferences(application.applicationContext)
        val allEntries: Map<String, *> = userPref!!.all
        var string = "{"
        for (entry in allEntries.entries) {
            string += "\"${entry.key}\"=\"${entry.value}\","
        }

        string = string.substring(0, string.length - 1) + "}"

        try {
            val output: OutputStream? = requireActivity().contentResolver.openOutputStream(uri)

            output?.write(string.toByteArray())
            output?.flush()
            output?.close()
            activity?.snackbar("${getString(R.string.settings_exported)} ${uri.fileName}")
        } catch (e: IOException) {
            activity?.snackbar(R.string.settings_export_failure)
        }
    }

    private fun importBookmarks() {
        showImportBookmarksDialog()
    }

    private fun exportBookmarks() {
        showExportBookmarksDialog()
    }

    /**
     * Start bookmarks export workflow by showing file creation dialog.
     */
    private fun showExportBookmarksDialog() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            var timeStamp = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val dateFormat = SimpleDateFormat("-yyyy-MM-dd-(HH:mm)", Locale.US)
                timeStamp = dateFormat.format(Date())
            }
            // That is a neat feature as it guarantee no file will be overwritten.
            putExtra(Intent.EXTRA_TITLE, "StyxBookmarks$timeStamp.txt")
        }
        bookmarkExportFilePicker.launch(intent)
    }

    private val bookmarkExportFilePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {

                // Using content resolver to get an input stream from selected URI
                result.data?.data?.let { uri ->
                    context?.contentResolver?.openOutputStream(uri)?.let { outputStream ->
                        //val mimeType = context?.contentResolver?.getType(uri)

                        bookmarksSortSubscription = bookmarkRepository.getAllBookmarksSorted()
                            .subscribeOn(databaseScheduler)
                            .subscribe { list ->
                                if (!isAdded) {
                                    return@subscribe
                                }

                                exportSubscription?.dispose()
                                exportSubscription =
                                    BookmarkExporter.exportBookmarksToFile(list, outputStream)
                                        .subscribeOn(databaseScheduler)
                                        .observeOn(mainScheduler)
                                        .subscribeBy(
                                            onComplete = {
                                                activity?.apply {
                                                    snackbar("${getString(R.string.bookmark_export_path)} ${uri.fileName}")
                                                }
                                            },
                                            onError = { throwable ->
                                                logger.log(TAG,
                                                    "onError: exporting bookmarks",
                                                    throwable)
                                                val activity = activity
                                                if (activity != null && !activity.isFinishing && isAdded) {
                                                    Utils.createInformativeDialog(activity as AppCompatActivity,
                                                        R.string.title_error,
                                                        R.string.bookmark_export_failure)
                                                } else {
                                                    (activity as AppCompatActivity).snackbar(R.string.bookmark_export_failure)
                                                }
                                            }
                                        )
                            }
                    }
                }
            }
        }

    private fun showImportBookmarksDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // That's needed for some reason, crashes otherwise
            putExtra(
                // List all file types you want the user to be able to select
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    "text/html", // .html
                    "text/plain" // .txt
                )
            )
        }
        bookmarkImportFilePicker.launch(intent)
    }

    private val bookmarkImportFilePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Using content resolver to get an input stream from selected URI
                result.data?.data?.let { uri ->
                    context?.contentResolver?.openInputStream(uri).let { inputStream ->
                        val mimeType = context?.contentResolver?.getType(uri)
                        importSubscription?.dispose()
                        importSubscription = Single.just(inputStream)
                            .map {
                                if (mimeType == "text/html") {
                                    netscapeBookmarkFormatImporter.importBookmarks(it)
                                } else {
                                    legacyBookmarkImporter.importBookmarks(it)
                                }
                            }
                            .flatMap {
                                bookmarkRepository.addBookmarkList(it).andThen(Single.just(it.size))
                            }
                            .subscribeOn(databaseScheduler)
                            .observeOn(mainScheduler)
                            .subscribeBy(
                                onSuccess = { count ->
                                    activity?.apply {
                                        snackbar("$count ${getString(R.string.message_import)}")
                                        // Tell browser activity bookmarks have changed
                                        (activity as SettingsActivity).userPreferences.bookmarksChanged =
                                            true
                                    }
                                },
                                onError = {
                                    logger.log(TAG, "onError: importing bookmarks", it)
                                    val activity = activity
                                    if (activity != null && !activity.isFinishing && isAdded) {
                                        Utils.createInformativeDialog(activity as AppCompatActivity,
                                            R.string.title_error,
                                            R.string.import_bookmark_error)
                                    } else {
                                        (activity as AppCompatActivity).snackbar(R.string.import_bookmark_error)
                                    }
                                }
                            )
                    }
                }
            }
        }

    private fun importSettings(uri: Uri) {
        val input: InputStream? = requireActivity().contentResolver.openInputStream(uri)

        val bufferSize = 1024
        val buffer = CharArray(bufferSize)
        val out = StringBuilder()
        val `in`: Reader = InputStreamReader(input, "UTF-8")
        while (true) {
            val rsz = `in`.read(buffer, 0, buffer.size)
            if (rsz < 0) break
            out.append(buffer, 0, rsz)
        }

        val content = out.toString()

        val answer = JSONObject(content)
        val keys: JSONArray? = answer.names()
        val userPref = PreferenceManager.getDefaultSharedPreferences(application.applicationContext)
        for (i in 0 until keys!!.length()) {
            val key: String = keys.getString(i)
            val value: String = answer.getString(key)
            with(userPref.edit()) {
                if (value.matches("-?\\d+".toRegex())) {
                    putInt(key, value.toInt())
                } else if (value == "true" || value == "false") {
                    putBoolean(key, value.toBoolean())
                } else {
                    putString(key, value)
                }
                apply()
            }
        }
        activity?.snackbar(R.string.settings_reseted)
    }

    /**
     *
     */
    private fun showSessionExportDialog(aName: String, aTabCount: Int, aFile: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = KSessionMimeType // Specify type of newly created document

            var timeStamp = ""
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val dateFormat = SimpleDateFormat("-yyyy-MM-dd-(HH:mm)", Locale.US)
                timeStamp = dateFormat.format(Date())
            }
            // Specify default file name, user can change it.
            // If that file already exists a numbered suffix is automatically generated and appended to the file name between brackets.
            // That is a neat feature as it guarantee no file will be overwritten.
            putExtra(Intent.EXTRA_TITLE, "$aName$timeStamp-[$aTabCount]")
        }
        iSessionFile = aFile
        iSessionName = aName
        sessionExportFilePicker.launch(intent)
    }

    private var iSessionFile: File? = null
    private var iSessionName: String = ""
    private val sessionExportFilePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {

                // Using content resolver to get an input stream from selected URI
                // See:  https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
                result.data?.data?.let { uri ->
                    // Copy our session file to user selected path
                    context?.contentResolver?.openOutputStream(uri)?.let { outputStream ->
                        val input = FileInputStream(iSessionFile)
                        outputStream.write(input.readBytes())
                        input.close()
                        outputStream.flush()
                        outputStream.close()
                        iSessionFile = null
                    }

                    activity?.snackbar(getString(R.string.message_session_exported, iSessionName))

                }
            }
        }

    /**
     *
     */
    private fun showSessionImportDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // That's needed for some reason, crashes otherwise
            putExtra(
                // List all file types you want the user to be able to select
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    KSessionMimeType
                )
            )
        }
        sessionImportFilePicker.launch(intent)
        // See bookmarkImportFilePicker declaration below for result handler
    }

    private val sessionImportFilePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Using content resolver to get an input stream from selected URI
                // See:  https://commonsware.com/blog/2016/03/15/how-consume-content-uri.html
                result.data?.data?.let { uri ->
                    context?.contentResolver?.openInputStream(uri).let { input ->
                        // Build up our session name
                        val fileName =
                            application.filesDir?.path + '/' + TabsManager.FILENAME_SESSION_PREFIX + uri.fileName
                        //val file = File.createTempFile(TabsManager.FILENAME_SESSION_PREFIX + uri.fileName,"",application.filesDir)

                        // Make sure our file name is unique and short
                        var i = 0
                        var file = File(fileName)
                        while (file.exists()) {
                            i++
                            file = File(fileName + i.toString())
                        }
                        file.createNewFile()
                        // Write our session file
                        val output = FileOutputStream(file)
                        output.write(input?.readBytes())
                        input?.close()
                        output.flush()
                        output.close()
                        // Workout session name
                        val sessionName =
                            file.name.substring(TabsManager.FILENAME_SESSION_PREFIX.length)
                        // Add imported session to our session collection in our tab manager
                        val session = Session(sessionName)
                        tabsManager.iSessions.add(session)
                        // Make sure we persist our imported session
                        tabsManager.saveSessions()
                        // Add imported session to our preferences list
                        addPreferenceSessionExport(session)

                        activity?.snackbar(getString(R.string.message_session_imported,
                            sessionName))
                    }
                }
            }
        }

    private fun deleteAllBookmarks() {
        showDeleteBookmarksDialog()
    }

    private fun showDeleteBookmarksDialog() {
        activity?.let {
            BrowserDialog.showPositiveNegativeDialog(
                aContext = it as AppCompatActivity,
                title = R.string.action_delete,
                message = R.string.delete_all_bookmarks,
                positiveButton = DialogItem(title = R.string.yes) {
                    bookmarkRepository
                        .deleteAllBookmarks()
                        .subscribeOn(databaseScheduler)
                        .subscribe()
                    (activity as AppCompatActivity).snackbar(R.string.bookmark_restore)
                },
                negativeButton = DialogItem(title = R.string.no) {},
                onCancel = {}
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val uri: Uri? = data?.data
        if (requestCode == EXPORT_SETTINGS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                exportSettings(uri)
            }
        } else if (requestCode == IMPORT_SETTINGS && resultCode == Activity.RESULT_OK) {
            if (uri != null) {
                importSettings(uri)
            }
        }
    }

    companion object {

        private const val TAG = "BookmarkSettingsFrag"

        private const val SETTINGS_EXPORT = "export_bookmark"
        private const val SETTINGS_IMPORT = "import_bookmark"
        private const val SETTINGS_DELETE_BOOKMARKS = "delete_bookmarks"
        private const val SETTINGS_SETTINGS_EXPORT = "export_settings"
        private const val SETTINGS_SETTINGS_IMPORT = "import_settings"
        private const val SETTINGS_DELETE_SETTINGS = "clear_settings"
        private const val KSessionMimeType = "application/octet-stream"

        const val EXPORT_SETTINGS = 0
        const val IMPORT_SETTINGS = 1

    }
}