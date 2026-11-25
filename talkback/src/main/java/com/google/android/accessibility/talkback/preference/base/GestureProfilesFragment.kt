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
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.gesture.NikaGestureProfiles

/** Nika: save the gesture settings as a profile and switch between profiles. */
class GestureProfilesFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_prof_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  private fun rebuild() {
    val ctx = context ?: return
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val add = Preference(ctx)
    add.title = getString(R.string.nika_prof_save)
    add.summary = getString(R.string.nika_prof_save_summary)
    add.isPersistent = false
    add.setOnPreferenceClickListener {
      askName("") { name ->
        NikaGestureProfiles.saveCurrent(ctx, name)
        rebuild()
      }
      true
    }
    group.addPreference(add)
    val list = NikaGestureProfiles.load(ctx)
    if (list.isEmpty()) {
      val none = Preference(ctx)
      none.title = getString(R.string.nika_prof_empty)
      none.isSelectable = false
      none.isPersistent = false
      group.addPreference(none)
    }
    for (p in list) {
      val row = Preference(ctx)
      row.title = p.name
      row.isPersistent = false
      row.setOnPreferenceClickListener {
        chooseAction(p.name)
        true
      }
      group.addPreference(row)
    }
  }

  private fun chooseAction(name: String) {
    val act = activity ?: return
    val labels =
      arrayOf(
        getString(R.string.nika_prof_apply),
        getString(R.string.nika_prof_update),
        getString(R.string.nika_mac_delete),
      )
    AlertDialog.Builder(act)
      .setTitle(name)
      .setItems(labels) { _, which ->
        when (which) {
          0 -> {
            NikaGestureProfiles.apply(act, name)
            Toast.makeText(act, getString(R.string.nika_prof_applied, name), Toast.LENGTH_SHORT).show()
          }
          1 -> {
            NikaGestureProfiles.saveCurrent(act, name)
            Toast.makeText(act, R.string.nika_prof_updated, Toast.LENGTH_SHORT).show()
          }
          else -> {
            NikaGestureProfiles.delete(act, name)
            rebuild()
          }
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun askName(initial: String, done: (String) -> Unit) {
    val act = activity ?: return
    val edit = EditText(act)
    edit.setText(initial)
    edit.hint = getString(R.string.nika_prof_name_hint)
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_prof_save)
      .setView(edit)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val name = edit.text.toString().trim()
        if (name.isNotEmpty()) done(name)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
