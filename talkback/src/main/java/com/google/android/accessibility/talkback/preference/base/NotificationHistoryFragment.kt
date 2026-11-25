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
import android.text.format.DateUtils
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.notifications.NikaNotificationHistory

/** Nika: the last announced notifications. */
class NotificationHistoryFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_notif_history)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  private fun rebuild() {
    val ctx = context ?: return
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val list = NikaNotificationHistory.load(ctx)
    if (list.isEmpty()) {
      val none = Preference(ctx)
      none.title = getString(R.string.nika_notif_history_empty)
      none.isSelectable = false
      none.isPersistent = false
      group.addPreference(none)
      return
    }
    val clear = Preference(ctx)
    clear.title = getString(R.string.nika_notif_history_clear)
    clear.isPersistent = false
    clear.setOnPreferenceClickListener {
      NikaNotificationHistory.clear(ctx)
      rebuild()
      true
    }
    group.addPreference(clear)
    for (e in list) {
      val p = Preference(ctx)
      p.title = e.text
      p.summary =
        listOf(
            e.app.ifEmpty { e.pkg },
            DateUtils.formatDateTime(
              ctx,
              e.time,
              DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL,
            ),
          )
          .joinToString(", ")
      p.isPersistent = false
      p.setOnPreferenceClickListener {
        val act = activity
        if (act != null) {
          android.app.AlertDialog.Builder(act)
            .setTitle(e.text)
            .setPositiveButton(R.string.nika_notif_delete_one) { _, _ ->
              NikaNotificationHistory.remove(ctx, e)
              rebuild()
            }
            .setNeutralButton(R.string.nika_notif_history_clear) { _, _ ->
              NikaNotificationHistory.clear(ctx)
              rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        }
        true
      }
      group.addPreference(p)
    }
  }
}
