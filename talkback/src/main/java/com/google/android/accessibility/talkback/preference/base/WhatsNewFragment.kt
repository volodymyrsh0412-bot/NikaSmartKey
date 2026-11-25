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
import android.os.Handler
import android.os.Looper
import androidx.preference.Preference
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.update.UpdateChecker
import com.google.android.accessibility.talkback.update.Updater

/**
 * Shows what is new in this version of Nika. If a newer build is available, it is shown at the top
 * with its changes, and a click installs it. The text is in the strings nika_whats_new_page_*.
 */
class WhatsNewFragment : TalkbackBaseFragment(R.xml.whats_new_preferences) {
  override fun getTitle(): CharSequence = getText(R.string.title_pref_whats_new)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    val appContext = context?.applicationContext ?: return
    val updatePref = findPreference<Preference>("whats_new_update") ?: return
    updatePref.isVisible = false
    Thread {
        val update =
          try {
            UpdateChecker.check(appContext)
          } catch (e: Exception) {
            null
          }
        if (update != null) {
          Handler(Looper.getMainLooper()).post {
            if (!isAdded) {
              return@post
            }
            updatePref.title = getString(R.string.nika_update_available, update.build)
            updatePref.summary =
              update.changes.joinToString("\n").ifEmpty { getString(R.string.nika_update_tap) }
            updatePref.setOnPreferenceClickListener {
              Updater.install(appContext, update.downloadUrl)
              true
            }
            updatePref.isVisible = true
          }
        }
      }
      .start()
  }
}
