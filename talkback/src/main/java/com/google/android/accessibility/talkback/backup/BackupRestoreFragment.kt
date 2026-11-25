/*
 * Copyright 2026 Nika contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.android.accessibility.talkback.backup

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.preference.base.TalkbackBaseFragment

/** Nika: saves a full copy to a zip file, and restores the groups the person chooses. */
class BackupRestoreFragment : TalkbackBaseFragment() {
  private val createBackup =
    registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
      if (uri != null) {
        writeBackup(uri)
      }
    }

  private val openBackup =
    registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        readBackup(uri)
      }
    }

  public override fun getTitle(): CharSequence = getText(R.string.settings_backup_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    val context = requireContext()
    val screen = preferenceManager.createPreferenceScreen(context)
    screen.addPreference(
      actionPreference(context, R.string.settings_backup_export, R.string.settings_backup_export_summary) {
        createBackup.launch(NikaBackup.FILE_NAME)
      }
    )
    screen.addPreference(
      actionPreference(context, R.string.settings_backup_import, R.string.settings_backup_import_summary) {
        openBackup.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream"))
      }
    )
    preferenceScreen = screen
  }

  private fun actionPreference(context: Context, title: Int, summary: Int, onClick: () -> Unit) =
    Preference(context).apply {
      isPersistent = false
      layoutResource = R.layout.listitem_2texts
      setTitle(title)
      setSummary(summary)
      setOnPreferenceClickListener {
        onClick()
        true
      }
    }

  private fun writeBackup(uri: Uri) {
    val context = requireContext()
    val written =
      try {
        val out = context.contentResolver.openOutputStream(uri)
        if (out != null) {
          NikaBackup.export(context, out)
          true
        } else {
          false
        }
      } catch (e: Exception) {
        false
      }
    toast(if (written) R.string.settings_backup_export_done else R.string.settings_backup_export_failed)
  }

  private fun readBackup(uri: Uri) {
    val context = requireContext()
    val content =
      try {
        context.contentResolver.openInputStream(uri)?.use { NikaBackup.read(it) }
      } catch (e: Exception) {
        null
      }
    if (content == null) {
      toast(R.string.settings_backup_import_failed)
      return
    }
    val groups = NikaBackup.available(content)
    if (groups.isEmpty()) {
      toast(R.string.settings_backup_import_failed)
      return
    }
    val names = groups.map { g -> getString(NikaBackup.GROUPS.first { it.first == g }.second) }.toTypedArray()
    val checked = BooleanArray(groups.size) { true }
    AlertDialog.Builder(context)
      .setTitle(R.string.nika_bk_pick_title)
      .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
      .setPositiveButton(R.string.nika_bk_restore) { _, _ ->
        val chosen = groups.filterIndexed { i, _ -> checked[i] }.toSet()
        if (chosen.isEmpty()) {
          return@setPositiveButton
        }
        val ok = NikaBackup.restore(context, content, chosen)
        if (ok) {
          AlertDialog.Builder(context)
            .setTitle(R.string.settings_backup_import_done)
            .setMessage(R.string.settings_backup_import_restart)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        } else {
          toast(R.string.settings_backup_import_failed)
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun toast(message: Int) {
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
  }
}
