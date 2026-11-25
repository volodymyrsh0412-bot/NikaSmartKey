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
import android.widget.EditText
import androidx.preference.Preference
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.notifications.NikaNotificationFormat
import com.google.android.accessibility.utils.SharedPreferencesUtils

/** Nika: what is said about notifications, in one place. */
class NotificationAnnounceFragment : TalkbackBaseFragment(R.xml.notification_announce_preferences) {
  override fun getTitle(): CharSequence = getText(R.string.title_pref_speak_notifications)

  private var templateRow: Preference? = null
  private var mutedRow: Preference? = null

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    val ctx = context ?: return
    val screen = preferenceScreen ?: return
    val template = Preference(ctx)
    template.title = getString(R.string.nika_notif_template)
    template.isPersistent = false
    template.setOnPreferenceClickListener {
      askTemplate()
      true
    }
    screen.addPreference(template)
    templateRow = template
    val muted = Preference(ctx)
    muted.title = getString(R.string.nika_notif_muted)
    muted.isPersistent = false
    muted.setOnPreferenceClickListener {
      chooseMuted()
      true
    }
    screen.addPreference(muted)
    mutedRow = muted
    val history = Preference(ctx)
    history.title = getString(R.string.nika_notif_history)
    history.isPersistent = false
    history.fragment = NotificationHistoryFragment::class.java.name
    screen.addPreference(history)
    refresh()
  }

  override fun onResume() {
    super.onResume()
    refresh()
  }

  private fun refresh() {
    val ctx = context ?: return
    val t = SharedPreferencesUtils.getSharedPreferences(ctx).getString(NikaNotificationFormat.PREF_TEMPLATE, "") ?: ""
    templateRow?.summary = if (t.isEmpty()) getString(R.string.nika_notif_template_none) else t
    val n = NikaNotificationFormat.mutedApps(ctx).size
    mutedRow?.summary = getString(R.string.nika_notif_muted_count, n)
  }

  private fun askTemplate() {
    val act = activity ?: return
    val ctx = act
    val prefs = SharedPreferencesUtils.getSharedPreferences(ctx)
    val edit = EditText(act)
    edit.setText(prefs.getString(NikaNotificationFormat.PREF_TEMPLATE, "") ?: "")
    edit.hint = "{app}, {category}, {title}, {text}, {time}"
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_notif_template)
      .setMessage(R.string.nika_notif_template_help)
      .setView(edit)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        prefs.edit().putString(NikaNotificationFormat.PREF_TEMPLATE, edit.text.toString().trim()).apply()
        refresh()
      }
      .setNeutralButton(R.string.nika_notif_template_reset) { _, _ ->
        prefs.edit().remove(NikaNotificationFormat.PREF_TEMPLATE).apply()
        refresh()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseMuted() {
    val act = activity ?: return
    val pm = act.packageManager
    val found = ArrayList<Pair<String, String>>()
    val seen = HashSet<String>()
    try {
      val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      for (info in pm.queryIntentActivities(intent, 0)) {
        val pkg = info.activityInfo?.packageName ?: continue
        if (seen.add(pkg)) found.add(Pair(info.loadLabel(pm).toString(), pkg))
      }
    } catch (e: RuntimeException) {
      // an empty list
    }
    found.sortBy { it.first.lowercase() }
    val muted = NikaNotificationFormat.mutedApps(act)
    val checked = BooleanArray(found.size) { muted.contains(found[it].second) }
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_notif_muted)
      .setMultiChoiceItems(found.map { it.first }.toTypedArray(), checked) { _, which, on -> checked[which] = on }
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val result = HashSet<String>()
        // keep apps that are not in the list of launcher apps
        for (m in muted) if (found.none { it.second == m }) result.add(m)
        for (i in found.indices) if (checked[i]) result.add(found[i].second)
        NikaNotificationFormat.saveMutedApps(act, result)
        refresh()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
