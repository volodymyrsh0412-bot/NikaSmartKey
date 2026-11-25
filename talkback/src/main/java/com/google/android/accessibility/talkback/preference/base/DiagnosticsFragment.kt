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

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.utils.NikaDiag
import com.google.android.accessibility.utils.SharedPreferencesUtils

/** Nika: the diary of what the screen reader does, and where to save it. */
class DiagnosticsFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_diag_title)

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
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val sw = SwitchPreferenceCompat(group.context)
    sw.title = getString(R.string.nika_diag_switch)
    sw.summary = getString(R.string.nika_diag_switch_summary)
    sw.isPersistent = false
    sw.isChecked = SharedPreferencesUtils.getSharedPreferences(ctx).getBoolean(NikaDiag.PREF_ON, true)
    sw.setOnPreferenceChangeListener { _, value ->
      SharedPreferencesUtils.getSharedPreferences(ctx).edit().putBoolean(NikaDiag.PREF_ON, value == true).apply()
      true
    }
    group.addPreference(sw)
    row(group, getString(R.string.nika_diag_save), getString(R.string.nika_diag_save_summary, NikaDiag.size() / 1024)) {
      val name = NikaDiag.saveToDownloads(ctx)
      Toast.makeText(
          ctx,
          if (name != null) getString(R.string.nika_diag_saved, name) else getString(R.string.nika_diag_error),
          Toast.LENGTH_LONG,
        )
        .show()
    }
    row(group, getString(R.string.nika_diag_clear), null) {
      NikaDiag.clear()
      Toast.makeText(ctx, R.string.nika_diag_cleared, Toast.LENGTH_SHORT).show()
    }
  }

  private fun row(group: PreferenceGroup, title: String, summary: String?, click: () -> Unit) {
    val p = Preference(group.context)
    p.title = title
    p.summary = summary
    p.isPersistent = false
    p.setOnPreferenceClickListener {
      click()
      true
    }
    group.addPreference(p)
  }
}
