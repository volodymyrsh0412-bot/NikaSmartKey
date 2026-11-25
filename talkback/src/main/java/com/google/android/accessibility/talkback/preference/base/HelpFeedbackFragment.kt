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
import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.training.TutorialInitiator
import com.google.android.accessibility.talkback.update.NikaSettings

/** Nika: help and feedback in one place: the Telegram group, a message to the developer, the guide. */
class HelpFeedbackFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_hf_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    row(group, getString(R.string.nika_telegram_title), getString(R.string.nika_telegram_summary)) {
      open(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+Yb0tYhVnUHhkOWIy")))
    }
    row(group, getString(R.string.nika_feedback_title), getString(R.string.nika_feedback_summary)) {
      activity?.let { NikaSettings.sendFeedback(it) }
    }
    row(group, getString(R.string.title_pref_category_tutorial), null) {
      activity?.let { open(TutorialInitiator.createTutorialIntent(it)) }
    }
  }

  private fun open(intent: Intent) {
    try {
      startActivity(intent)
    } catch (e: RuntimeException) {
      Toast.makeText(context, R.string.nika_ka_cannot_open, Toast.LENGTH_LONG).show()
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
