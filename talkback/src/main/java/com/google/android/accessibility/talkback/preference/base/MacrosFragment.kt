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

package com.google.android.accessibility.talkback.preference.base

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.macros.NikaMacros

/** Nika: the list of macros. */
class MacrosFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_mac_title)

  private val importFile =
    registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
      val ctx = context
      if (uri != null && ctx != null) {
        try {
          val text = ctx.contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } ?: ""
          val n = NikaMacros.importText(ctx, text)
          android.widget.Toast.makeText(
              ctx,
              if (n > 0) getString(R.string.nika_mac_imported, n) else getString(R.string.nika_mac_file_error),
              android.widget.Toast.LENGTH_LONG,
            )
            .show()
          rebuild()
        } catch (e: Exception) {
          android.widget.Toast.makeText(ctx, R.string.nika_mac_file_error, android.widget.Toast.LENGTH_SHORT).show()
        }
      }
    }

  private val exportFile =
    registerForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      val ctx = context
      if (uri != null && ctx != null) {
        try {
          ctx.contentResolver.openOutputStream(uri)?.use { it.write(NikaMacros.toJson(NikaMacros.load(ctx)).toByteArray()) }
          android.widget.Toast.makeText(ctx, R.string.nika_mac_exported, android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          android.widget.Toast.makeText(ctx, R.string.nika_mac_file_error, android.widget.Toast.LENGTH_SHORT).show()
        }
      }
    }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  override fun onResume() {
    super.onResume()
    rebuild()
  }

  private fun rebuild() {
    val ctx = context ?: return
    // An empty group from the XML is removed by the settings filter, so the rows go on the screen itself.
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val add = Preference(ctx)
    add.title = getString(R.string.nika_mac_add)
    add.summary = getString(R.string.nika_mac_summary)
    add.isPersistent = false
    add.setOnPreferenceClickListener {
      askName(null, "") { name ->
        val all = NikaMacros.load(ctx)
        all.add(NikaMacros.Macro(NikaMacros.newId(), name))
        NikaMacros.save(ctx, all)
        rebuild()
      }
      true
    }
    group.addPreference(add)
    val imp = Preference(ctx)
    imp.title = getString(R.string.nika_mac_import)
    imp.isPersistent = false
    imp.setOnPreferenceClickListener {
      importFile.launch(arrayOf("*/*"))
      true
    }
    group.addPreference(imp)
    val exp = Preference(ctx)
    exp.title = getString(R.string.nika_mac_export_all)
    exp.isPersistent = false
    exp.setOnPreferenceClickListener {
      exportFile.launch("nika-macros.json")
      true
    }
    group.addPreference(exp)
    val all = NikaMacros.load(ctx)
    if (all.isEmpty()) {
      val none = Preference(ctx)
      none.title = getString(R.string.nika_mac_empty)
      none.isSelectable = false
      none.isPersistent = false
      group.addPreference(none)
    }
    for (m in all) {
      val p = Preference(ctx)
      p.title = m.name
      p.summary = getString(R.string.nika_mac_steps, m.steps.size)
      p.isPersistent = false
      p.fragment = MacroEditFragment::class.java.name
      p.extras.putString(MacroEditFragment.ARG_ID, m.id)
      group.addPreference(p)
    }
  }

  private fun askName(title: String?, initial: String, done: (String) -> Unit) {
    val act = activity ?: return
    val edit = EditText(act)
    edit.setText(initial)
    edit.hint = getString(R.string.nika_mac_name_hint)
    AlertDialog.Builder(act)
      .setTitle(title ?: getString(R.string.nika_mac_add))
      .setView(edit)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val name = edit.text.toString().trim()
        if (name.isNotEmpty()) done(name)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
