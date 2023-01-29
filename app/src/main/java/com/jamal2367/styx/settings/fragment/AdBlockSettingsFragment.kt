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

package com.jamal2367.styx.settings.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceClickListener
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jamal2367.styx.R
import com.jamal2367.styx.adblock.AbpBlockerManager
import com.jamal2367.styx.adblock.AbpListUpdater
import com.jamal2367.styx.adblock.AbpUpdateMode
import com.jamal2367.styx.extensions.resizeAndShow
import com.jamal2367.styx.extensions.snackbar
import com.jamal2367.styx.extensions.withSingleChoiceItems
import com.jamal2367.styx.preference.UserPreferences
import com.jamal2367.styx.utils.ContextUtils.getColorStateListSafe
import com.jamal2367.styx.utils.ContextUtils.getDrawableSafe
import dagger.hilt.android.AndroidEntryPoint
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpDao
import jp.hazuki.yuzubrowser.adblock.repository.abp.AbpEntity
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.*
import javax.inject.Inject

/**
 * Settings for the content control mechanic.
 */
@AndroidEntryPoint
class AdBlockSettingsFragment : AbstractSettingsFragment() {

    @Inject
    internal lateinit var userPreferences: UserPreferences
    @Inject
    internal lateinit var abpListUpdater: AbpListUpdater
    @Inject
    internal lateinit var abpBlockerManager: AbpBlockerManager

    private lateinit var abpDao: AbpDao
    private val entityPrefs = mutableMapOf<Int, FilterListSwitchPreference>()

    // if blocklist changed, they need to be reloaded, but this should happen only once
    //  if reloadLists is true, list reload will be launched onDestroy
    private var reloadLists = false

    // updater is launched in background, and lists should not be reloaded while updater is running
    //  int since multiple lists could be updated at the same time
    private var updatesRunning = 0

    // uri of temporary blocklist file
    private var fileUri: Uri? = null

    // Our preferences filters category, will contains our filters file entries
    private lateinit var filtersCategory: PreferenceGroup

    override fun providePreferencesXmlResource() = R.xml.preference_ad_block

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)

        switchPreference(
            preference = getString(R.string.pref_key_content_control),
            isChecked = userPreferences.adBlockEnabled,
            onCheckChange = {
                userPreferences.adBlockEnabled = it
                // update enabled lists when enabling blocker
                if (it) {
                    updateFilterList(null, false)
                    reloadLists = true
                }
            }
        )

        filtersCategory = findPreference(getString(R.string.pref_key_content_control_filters))!!

        if (context != null) {

            abpDao = AbpDao(requireContext())

            clickableDynamicPreference(
                preference = getString(R.string.pref_key_blocklist_auto_update),
                summary = userPreferences.blockListAutoUpdate.toDisplayString(),
                onClick = { summaryUpdater ->
                    activity?.let { MaterialAlertDialogBuilder(it) }?.apply {
                        setTitle(R.string.blocklist_update)
                        val values = AbpUpdateMode.values().map { Pair(it, it.toDisplayString()) }
                        withSingleChoiceItems(values, userPreferences.blockListAutoUpdate) {
                            userPreferences.blockListAutoUpdate = it
                            summaryUpdater.updateSummary(it.toDisplayString())
                        }
                        setPositiveButton(resources.getString(R.string.action_ok), null)
                    }?.resizeAndShow()
                }
            )

            clickableDynamicPreference(
                preference = getString(R.string.pref_key_blocklist_auto_update_frequency),
                summary = userPreferences.blockListAutoUpdateFrequency.toUpdateFrequency(),
                onClick = { summaryUpdater ->
                    activity?.let { MaterialAlertDialogBuilder(it) }?.apply {
                        setTitle(R.string.blocklist_update_frequency)
                        val values = listOf(
                            Pair(1, resources.getString(R.string.block_remote_frequency_daily)),
                            Pair(7, resources.getString(R.string.block_remote_frequency_weekly)),
                            Pair(30, resources.getString(R.string.block_remote_frequency_monthly))
                        )
                        withSingleChoiceItems(values,
                            userPreferences.blockListAutoUpdateFrequency) {
                            userPreferences.blockListAutoUpdateFrequency = it
                            summaryUpdater.updateSummary(it.toUpdateFrequency())
                        }
                        setPositiveButton(R.string.action_ok, null)
                        setNeutralButton(R.string.blocklist_update_now) { _, _ ->
                            updateFilterList(null, true)
                        }
                    }?.resizeAndShow()
                }
            )

            clickableDynamicPreference(
                preference = getString(R.string.pref_key_modify_filters),
                summary = userPreferences.modifyFilters.toModifySetting(),
                onClick = { summaryUpdater ->
                    activity?.let { MaterialAlertDialogBuilder(it) }?.apply {
                        setCustomTitle(TextView(ContextThemeWrapper(context,
                            R.style.MaterialAlertDialog_Material3)).apply {
                            setPadding(40, 30, 40, 10)
                            setText(R.string.use_modify_filters_warning)
                            textSize = 16f
                        })
                        val values = listOf(
                            Pair(0, resources.getString(R.string.disabled)),
                            Pair(1,
                                resources.getString(R.string.modify_filters_not_for_main_frame)),
                            Pair(2, resources.getString(R.string.enabled))
                        )
                        withSingleChoiceItems(values, userPreferences.modifyFilters) {
                            userPreferences.modifyFilters = it
                            summaryUpdater.updateSummary(it.toModifySetting())
                        }
                        setPositiveButton(R.string.action_ok, null)
                    }?.resizeAndShow()
                }
            )

            loadFilterLists()
        }
    }

    private fun loadFilterLists() {
        filtersCategory.removeAll()

        // "new list" button
        val newList = Preference(requireContext())
        newList.title = resources.getString(R.string.add_blocklist)
        newList.icon =
            ResourcesCompat.getDrawable(resources, R.drawable.ic_add, requireActivity().theme)
        newList.onPreferenceClickListener = OnPreferenceClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setNeutralButton(R.string.action_cancel,
                    null) // actually the negative button, but looks nicer this way
                .setNegativeButton(R.string.local_file) { _, _ -> showBlockList(AbpEntity(url = "file")) }
                .setPositiveButton(R.string.remote_file) { _, _ -> showBlockList(AbpEntity(url = "")) }
                .setTitle(R.string.add_blocklist)
                .setMessage(R.string.add_blocklist_hint)
                .create()
            dialog.show()
            true
        }
        filtersCategory.addPreference(newList)
        newList.dependency = getString(R.string.pref_key_content_control)

        // list of blocklists/entities
        for (entity in abpDao.getAll().sortedBy { it.title?.lowercase() }) {
            val entityPref = FilterListSwitchPreference(entity)
            entityPref.icon = ResourcesCompat.getDrawable(resources,
                R.drawable.ic_import_export,
                requireActivity().theme)
            entityPrefs[entity.entityId] = entityPref
            updateSummary(entity)
            filtersCategory.addPreference(entityPrefs[entity.entityId]!!)
            entityPref.dependency = getString(R.string.pref_key_content_control)
        }

    }

    private fun updateSummary(entity: AbpEntity) {
        if (entity.lastLocalUpdate > 0)
            entityPrefs[entity.entityId]?.summary =
                resources.getString(R.string.blocklist_last_update,
                    DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT)
                        .format(Date(entity.lastLocalUpdate)))
        else
            entityPrefs[entity.entityId]?.summary = ""
    }

    // update filter list and adjust displayed last update time
    //  update all lists if no entity provided
    @OptIn(DelicateCoroutinesApi::class)
    private fun updateFilterList(abpEntity: AbpEntity?, forceUpdate: Boolean) {
        GlobalScope.launch(Dispatchers.IO) {
            // do nothing if update not required
            if (!forceUpdate && abpEntity != null && !abpListUpdater.needsUpdate(abpEntity))
                return@launch

            ++updatesRunning
            activity?.runOnUiThread {
                if (abpEntity != null)
                    entityPrefs[abpEntity.entityId]?.summary =
                        resources.getString(R.string.blocklist_updating)
            }
            val updated = if (abpEntity == null) abpListUpdater.updateAll(forceUpdate)
            else abpListUpdater.updateAbpEntity(abpEntity, forceUpdate)

            // delete temporary file
            //  this is necessary because all local blocklists use the same temporary file (uri)
            //  so it could happen that lists get mixed up via an old temporary blocklist file
            activity?.externalCacheDir?.let { File(it, BLOCK_LIST_FILE).delete() }

            if (updated)
                reloadBlockLists()

            // update the "last updated" times
            activity?.runOnUiThread {
                for (entity in abpDao.getAll())
                    updateSummary(entity)
            }
            --updatesRunning
        }
    }


    @Suppress("DEPRECATION")
    private fun showBlockList(entity: AbpEntity) {
        val builder = MaterialAlertDialogBuilder(requireContext())
        var dialog: AlertDialog? = null
        builder.setTitle(R.string.action_edit)
        val linearLayout = LinearLayout(context)
        linearLayout.orientation = LinearLayout.VERTICAL

        // edit field for blocklist title
        val title = EditText(context)
        title.inputType = InputType.TYPE_CLASS_TEXT
        title.setText(entity.title)
        title.hint = getString(R.string.hint_title)
        title.addTextChangedListener {
            entity.title = it.toString()
            updateButton(dialog?.getButton(AlertDialog.BUTTON_POSITIVE), entity.url, entity.title)
        }
        linearLayout.addView(title)

        var needsUpdate = false
        val oldUrl = entity.url

        // field for choosing file or url
        when {
            entity.url.startsWith("file") -> {
                val fileChooseButton = MaterialButton(requireContext())
                fileChooseButton.text = if (entity.url == "file") getString(R.string.title_chooser)
                else getString(R.string.local_file_replace)
                fileChooseButton.setOnClickListener {
                    // show file chooser
                    //  no storage permission necessary
                    fileUri = null
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = TEXT_MIME_TYPE
                    }
                    startActivityForResult(intent, FILE_REQUEST_CODE)

                    // wait until file was chosen
                    lifecycleScope.launch {
                        while (fileUri == null)
                            delay(200)

                        // don't update if it's the fake http uri provided on file chooser cancel
                        if (fileUri?.scheme == "file") {
                            entity.url = fileUri.toString()
                            updateButton(dialog?.getButton(AlertDialog.BUTTON_POSITIVE),
                                entity.url,
                                entity.title)
                            fileChooseButton.text = getString(R.string.local_file_chosen)
                            needsUpdate = true
                        }
                    }
                }
                linearLayout.addView(fileChooseButton)
            }
            entity.url.toHttpUrlOrNull() != null || entity.url == "" -> {
                val url = EditText(context)
                url.inputType = InputType.TYPE_TEXT_VARIATION_URI
                url.setText(entity.url)
                url.hint = getString(R.string.hint_url)
                url.addTextChangedListener {
                    entity.url = it.toString()
                    updateButton(dialog?.getButton(AlertDialog.BUTTON_POSITIVE),
                        entity.url,
                        entity.title)
                }
                linearLayout.addView(url)
            }
        }

        // delete button
        // don't show for internal list or when creating a new entity
        if (entity.entityId != 0) {
            val delete = MaterialButton(requireContext()) // looks ugly, but works
            delete.text = resources.getString(R.string.blocklist_remove)
            // confirm deletion!
            delete.setOnClickListener {
                val confirmDialog = MaterialAlertDialogBuilder(requireContext())
                    .setNegativeButton(R.string.action_cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        abpDao.delete(entity)
                        dialog?.dismiss()
                        filtersCategory.removePreference(entityPrefs[entity.entityId]!!)
                        reloadBlockLists()
                    }
                    .setTitle(resources.getString(R.string.blocklist_remove_confirm, entity.title))
                    .create()
                confirmDialog.show()
            }
            linearLayout.addView(delete)
        }

        // update button
        if (entity.entityId != 0 && entity.url.toHttpUrlOrNull() != null) {
            val updateListButton = MaterialButton(requireContext())
            updateListButton.text = resources.getString(R.string.blocklist_update)
            updateListButton.setOnClickListener {
                updateFilterList(entity, true)
            }
            linearLayout.addView(updateListButton)
        }

        // arbitrary numbers that look ok on my phone -> ok for other phones?
        linearLayout.setPadding(30, 10, 30, 10)

        builder.setView(linearLayout)
        builder.setNegativeButton(R.string.action_cancel, null)
        builder.setPositiveButton(R.string.action_ok) { _, _ ->

            entity.title = title.text.toString()

            // make sure list is reloaded if url changed
            if (oldUrl != entity.url) {
                entity.lastLocalUpdate = 0
                entity.lastModified = null
                needsUpdate = true
            }

            if (entity.entityId == 0) // id == 0 if new entity was added, we want to update it immediately
                needsUpdate = true
            val newId = abpDao.update(entity)

            // check for update (after abpDao.update!)
            if (needsUpdate)
                updateFilterList(entity, needsUpdate)

            if (entityPrefs[newId] == null) // not in entityPrefs if new
                loadFilterLists() // load lists again, to get alphabetical order
            else
                entityPrefs[entity.entityId]?.title = entity.title
        }
        dialog = builder.create()
        dialog.show()
        updateButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE), entity.url, entity.title)
    }

    // list should be reloaded only once
    //  this is done when leaving settings screen
    //  joint lists are removed immediately to avoid using them if app is stopped without leaving the setting screen
    private fun reloadBlockLists() {
        reloadLists = true
        abpBlockerManager.removeJointLists()
    }

    // disable ok button if url or title not valid
    private fun updateButton(button: Button?, url: String, title: String?) {
        if (title?.contains("§§") == true || title.isNullOrBlank()) {
            button?.text = resources.getText(R.string.invalid_title)
            button?.isEnabled = false
            return
        }
        if ((url.toHttpUrlOrNull() == null || url.contains("§§")) && !url.startsWith("file:")) {
            button?.text =
                if (url.startsWith("file")) "no file chosen" else resources.getText(R.string.invalid_url)
            button?.isEnabled = false
            return
        }
        button?.text = resources.getString(R.string.action_ok)
        button?.isEnabled = true
    }

    private fun AbpUpdateMode.toDisplayString(): String = getString(when (this) {
        AbpUpdateMode.NONE -> R.string.blocklist_update_off
        AbpUpdateMode.WIFI_ONLY -> R.string.blocklist_update_wifi
        AbpUpdateMode.ALWAYS -> R.string.blocklist_update_on
    })

    private fun Int.toUpdateFrequency() = when (this) {
        1 -> resources.getString(R.string.block_remote_frequency_daily)
        7 -> resources.getString(R.string.block_remote_frequency_weekly)
        30 -> resources.getString(R.string.block_remote_frequency_monthly)
        else -> "" //should not happen
    }

    private fun Int.toModifySetting() = when (this) {
        0 -> resources.getString(R.string.disabled)
        1 -> resources.getString(R.string.modify_filters_not_for_main_frame)
        2 -> resources.getString(R.string.enabled)
        else -> "" //should not happen
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onDestroy() {
        super.onDestroy()
        // reload lists after updates are done
        if (reloadLists || updatesRunning > 0) {
            GlobalScope.launch(Dispatchers.Default) {
                while (updatesRunning > 0)
                    delay(200)
                if (reloadLists)
                    abpBlockerManager.loadLists()
            }
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val dataUri = data?.data ?: return
                val cacheDir = activity?.externalCacheDir ?: return
                val inputStream = activity?.contentResolver?.openInputStream(dataUri) ?: return
                try {
                    // copy file to temporary file, like done by lightning
                    val outputFile = File(cacheDir, BLOCK_LIST_FILE)
                    inputStream.copyTo(outputFile.outputStream())
                    fileUri = Uri.fromFile(outputFile)
                    return
                } catch (exception: IOException) {
                    return
                }
            } else {
                (requireActivity()).snackbar(R.string.action_message_canceled)
                // set some fake uri to cancel wait-for-file loop
                fileUri = Uri.parse("http://no.file")
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    // class necessary to allow separate onClickListener for the switch
    private inner class FilterListSwitchPreference(val entity: AbpEntity) :
        SwitchPreferenceCompat(requireContext()) {

        override fun onBindViewHolder(holder: PreferenceViewHolder) {
            super.onBindViewHolder(holder)
            val switch: SwitchCompat? = holder.itemView.findViewById(R.id.filter_list_switch_widget)

            if (switch is SwitchCompat) {
                switch.apply {
                    trackDrawable = context.getDrawableSafe(R.drawable.ui_m3_switch_track)
                    trackTintList = context.getColorStateListSafe(R.color.sel_m3_switch_track)
                    thumbDrawable = context.getDrawableSafe(R.drawable.ui_m3_switch_thumb)
                    thumbTintList = context.getColorStateListSafe(R.color.sel_m3_switch_thumb)
                }
            }

            switch?.isChecked = entity.enabled
            switch?.setOnClickListener {
                isChecked = (it as SwitchCompat).isChecked
                entity.enabled = isChecked
                abpDao.update(entity)
                if (isChecked)
                    updateFilterList(entity,
                        false) // check for update, entity may have been disabled for a longer time
                reloadBlockLists()
            }
            onPreferenceClickListener = OnPreferenceClickListener {
                (it as FilterListSwitchPreference).isChecked =
                    entity.enabled // avoid flipping the switch on click
                showBlockList(entity)
                true
            }
        }

        // layout resource must be set before onBindViewHolder
        // title and isChecked can't be changed in onBindViewHolder, so also set it now
        override fun onAttached() {
            super.onAttached()
            title = entity.title
            isChecked = entity.enabled
            widgetLayoutResource = R.layout.filter_list_preference_widget
        }
    }

    companion object {
        private const val FILE_REQUEST_CODE = 100
        private const val BLOCK_LIST_FILE = "local_blocklist.txt"
        private const val TEXT_MIME_TYPE = "text/*"
    }
}
