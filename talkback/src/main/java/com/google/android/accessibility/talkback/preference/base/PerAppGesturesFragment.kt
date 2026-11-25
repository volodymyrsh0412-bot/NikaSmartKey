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

import android.content.Intent
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.gesture.NikaAppGestures

/** Nika: gestures that work only in one chosen application. */
class PerAppGesturesFragment : NikaKeysBaseFragment(R.xml.per_app_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_app_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    val ctx = context ?: return
    val picker = findPreference<ListPreference>(NikaAppGestures.SELECTED_KEY) ?: return
    val labels = ArrayList<String>()
    val values = ArrayList<String>()
    try {
      val pm = ctx.packageManager
      val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      val seen = HashSet<String>()
      val found = ArrayList<Pair<String, String>>()
      for (info in pm.queryIntentActivities(intent, 0)) {
        val pkg = info.activityInfo?.packageName ?: continue
        if (!seen.add(pkg)) continue
        found.add(Pair(info.loadLabel(pm).toString(), pkg))
      }
      found.sortBy { it.first.lowercase() }
      for (f in found) {
        labels.add(f.first)
        values.add(f.second)
      }
    } catch (e: RuntimeException) {
      // an empty list is shown
    }
    picker.entries = labels.toTypedArray()
    picker.entryValues = values.toTypedArray()
    picker.setOnPreferenceChangeListener { _, newValue ->
      applyApp(newValue as? String)
      showPicked(picker, newValue as? String)
      true
    }
    val current = picker.value
    showPicked(picker, current)
    applyApp(current)
  }

  private fun showPicked(picker: ListPreference, pkg: String?) {
    val index = if (pkg == null) -1 else picker.findIndexOfValue(pkg)
    picker.summary =
      if (index >= 0) picker.entries[index] else getString(R.string.nika_app_pick_hint)
  }

  /** Points every gesture item of the screen at the settings of the chosen application. */
  private fun applyApp(pkg: String?) {
    val screen = preferenceScreen ?: return
    fun walk(group: PreferenceGroup) {
      for (i in 0 until group.preferenceCount) {
        val p = group.getPreference(i)
        if (p is PreferenceGroup) {
          walk(p)
        } else if (p is GestureListPreference) {
          val orig = p.extras.getString(ORIG) ?: (p.key ?: continue).also { p.extras.putString(ORIG, it) }
          val id = NikaAppGestures.GESTURES[orig] ?: continue
          p.key = NikaAppGestures.prefKey(pkg ?: "none", id)
          // Toggling makes the row draw again with the actions of the chosen application.
          p.isEnabled = false
          p.isEnabled = !pkg.isNullOrEmpty()
        }
      }
    }
    walk(screen)
  }

  companion object {
    private const val ORIG = "nika_orig_key"
  }
}
