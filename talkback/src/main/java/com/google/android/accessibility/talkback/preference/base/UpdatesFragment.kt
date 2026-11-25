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
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.google.android.accessibility.talkback.BuildConfig
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.update.Updater
import com.google.android.accessibility.utils.SharedPreferencesUtils

/** Nika: updates in one place: the switch of the automatic check and "Check for updates". */
class UpdatesFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_up_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    val ctx = context ?: return
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val key = getString(R.string.pref_auto_update_check_key)
    val prefs = SharedPreferencesUtils.getSharedPreferences(ctx)
    val sw = SwitchPreferenceCompat(group.context)
    sw.key = key
    sw.title = getString(R.string.title_pref_auto_update_check)
    sw.summary = getString(R.string.summary_pref_auto_update_check)
    sw.isChecked = prefs.getBoolean(key, resources.getBoolean(R.bool.pref_auto_update_check_default))
    sw.setOnPreferenceChangeListener { _, value ->
      prefs.edit().putBoolean(key, value as Boolean).apply()
      true
    }
    group.addPreference(sw)

    val check = Preference(group.context)
    check.title = getString(R.string.title_pref_check_for_updates)
    check.summary = getString(R.string.summary_pref_check_for_updates, BuildConfig.COMMIT_COUNT)
    check.isPersistent = false
    check.setOnPreferenceClickListener {
      activity?.let { Updater.checkManually(it) }
      true
    }
    group.addPreference(check)
  }
}
