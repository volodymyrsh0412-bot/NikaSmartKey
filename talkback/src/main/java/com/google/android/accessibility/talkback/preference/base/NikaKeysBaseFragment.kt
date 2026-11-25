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
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.keyactions.NikaKeyActions
import com.google.android.accessibility.talkback.preference.TalkBackPreferencesActivity.TalkBackSubSettings
import com.google.android.accessibility.utils.FormFactorUtils
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.preference.PreferencesActivity.FRAGMENT_ARGS
import com.google.android.accessibility.utils.preference.PreferencesActivity.FRAGMENT_NAME

/** Nika: a settings screen with actions of keys. Shows the action list for gesture pickers. */
abstract class NikaKeysBaseFragment(xml: Int) : TalkbackBaseFragment(xml) {
  private var updateOnResume = false

  /** Screens with many sections start with them collapsed. */
  protected open val collapseSections: Boolean = true

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    if (FormFactorUtils.isAndroidWear()) {
      keepOnlyAnyKeysOnWatch()
    }
    for (n in 1..4) {
      val pref = findPreference<Preference>("nika_key_c${n}_pick") ?: continue
      showChosenKey(pref, n)
      pref.setOnPreferenceClickListener {
        capture(pref, n)
        true
      }
    }
    if (collapseSections) {
      collapseCategories()
    }
  }

  /** On a watch only the "any key" slots are kept: there are no digit or volume keys. */
  private fun keepOnlyAnyKeysOnWatch() {
    val screen = preferenceScreen ?: return
    val toRemove = mutableListOf<Preference>()
    for (i in 0 until screen.preferenceCount) {
      val p = screen.getPreference(i)
      val key = p.key ?: continue
      val drop =
        (key.startsWith("nika_keys_cat_") && !key.startsWith("nika_keys_cat_c")) ||
          key == "nika_keys_arrows" ||
          key == "nika_keys_skip_edit"
      if (drop) toRemove.add(p)
    }
    toRemove.forEach { screen.removePreference(it) }
  }

  override fun onDisplayPreferenceDialog(preference: Preference) {
    if (preference is GestureListPreference) {
      updateOnResume = true
      val intent = Intent(context, TalkBackSubSettings::class.java)
      intent.putExtra(FRAGMENT_NAME, GesturePreferenceFragmentCompat::class.java.canonicalName)
      intent.putExtra(
        FRAGMENT_ARGS,
        GesturePreferenceFragmentCompat.createBundleForFragmentArguments(preference),
      )
      startActivity(intent)
    } else {
      super.onDisplayPreferenceDialog(preference)
    }
  }

  override fun onResume() {
    super.onResume()
    if (updateOnResume) {
      updateOnResume = false
      refreshSummaries(preferenceScreen)
    }
  }

  private fun refreshSummaries(group: PreferenceGroup?) {
    if (group == null) return
    for (i in 0 until group.preferenceCount) {
      val p = group.getPreference(i)
      if (p is PreferenceGroup) {
        refreshSummaries(p)
      } else if (p is GestureListPreference) {
        p.summary = p.summary
      }
    }
  }

  private fun showChosenKey(pref: Preference, n: Int) {
    val ctx = context ?: return
    val code =
      SharedPreferencesUtils.getSharedPreferences(ctx).getInt(NikaKeyActions.customCodeKey(n), 0)
    pref.summary =
      if (code == 0) {
        getString(R.string.nika_keys_pick_none)
      } else {
        KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
      }
  }

  private fun save(n: Int, code: Int, pref: Preference) {
    val ctx = context ?: return
    SharedPreferencesUtils.getSharedPreferences(ctx)
      .edit()
      .putInt(NikaKeyActions.customCodeKey(n), code)
      .apply()
    showChosenKey(pref, n)
  }

  private fun capture(pref: Preference, n: Int) {
    val ctx = activity ?: return
    NikaKeyActions.capturing = true
    AlertDialog.Builder(ctx)
      .setTitle(R.string.nika_keys_capture_title)
      .setMessage(R.string.nika_keys_capture_message)
      .setNegativeButton(android.R.string.cancel, null)
      .setNeutralButton(R.string.nika_keys_capture_clear) { _, _ -> save(n, 0, pref) }
      .setOnKeyListener { dialog, keyCode, event ->
        if (
          event.action == KeyEvent.ACTION_DOWN &&
            keyCode != KeyEvent.KEYCODE_BACK &&
            keyCode != KeyEvent.KEYCODE_HOME &&
            keyCode != KeyEvent.KEYCODE_POWER &&
            keyCode != KeyEvent.KEYCODE_UNKNOWN &&
            !KeyEvent.isModifierKey(keyCode)
        ) {
          save(n, keyCode, pref)
          dialog.dismiss()
          true
        } else {
          false
        }
      }
      .setOnDismissListener { NikaKeyActions.capturing = false }
      .create()
      .show()
  }
}
