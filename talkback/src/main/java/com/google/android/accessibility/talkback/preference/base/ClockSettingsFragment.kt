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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.preference.Preference
import com.google.android.accessibility.talkback.R

/** Nika: settings of the talking clock, the timer and the stopwatch. */
class ClockSettingsFragment : NikaKeysBaseFragment(R.xml.clock_preferences) {
  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    findPreference<Preference>("nika_clock_permission")?.setOnPreferenceClickListener {
      openAlarmPermission()
      true
    }
  }

  /** Opens the system screen where exact alarms are allowed; falls back to the app details. */
  private fun openAlarmPermission() {
    val ctx = context ?: return
    val pkg = ctx.packageName
    val intents = ArrayList<Intent>()
    if (Build.VERSION.SDK_INT >= 31) {
      intents.add(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
    }
    intents.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
    for (i in intents) {
      try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
        return
      } catch (e: RuntimeException) {
        // try the next screen
      }
    }
  }

  override fun getTitle(): CharSequence = getText(R.string.nika_clock_title)
}
