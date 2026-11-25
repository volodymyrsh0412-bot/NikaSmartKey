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
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.talkback.macros.NikaAdminReceiver
import com.google.android.accessibility.talkback.macros.NikaKeepAlive
import com.google.android.accessibility.utils.SharedPreferencesUtils

/** Nika: protection from being closed when all the apps are closed. */
class KeepAliveFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_ka_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  override fun onResume() {
    super.onResume()
    rebuild()
  }

  private fun admin(ctx: Context) = ComponentName(ctx, NikaAdminReceiver::class.java)

  private fun isAdmin(ctx: Context): Boolean =
    try {
      (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).isAdminActive(admin(ctx))
    } catch (e: RuntimeException) {
      false
    }

  private fun open(intent: Intent) {
    try {
      startActivity(intent)
    } catch (e: RuntimeException) {
      Toast.makeText(context, R.string.nika_ka_cannot_open, Toast.LENGTH_LONG).show()
    }
  }

  private fun rebuild() {
    val ctx = context ?: return
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()

    val note = Preference(group.context)
    note.title = getString(R.string.nika_ka_note)
    note.isPersistent = false
    note.isSelectable = false
    group.addPreference(note)

    val sw = SwitchPreferenceCompat(group.context)
    sw.key = NikaKeepAlive.PREF
    sw.title = getString(R.string.nika_ka_switch)
    sw.summary = getString(R.string.nika_ka_switch_summary)
    sw.setDefaultValue(true)
    sw.isChecked = SharedPreferencesUtils.getSharedPreferences(ctx).getBoolean(NikaKeepAlive.PREF, true)
    sw.setOnPreferenceChangeListener { _, value ->
      SharedPreferencesUtils.getSharedPreferences(ctx).edit().putBoolean(NikaKeepAlive.PREF, value as Boolean).apply()
      TalkBackService.getInstance()?.let { NikaKeepAlive.apply(it) }
      true
    }
    group.addPreference(sw)

    row(group, getString(R.string.nika_ka_battery), getString(R.string.nika_ka_battery_summary)) {
      open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
    row(group, getString(R.string.nika_ka_notif), getString(R.string.nika_ka_notif_summary)) {
      open(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
          .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
      )
    }
    val on = isAdmin(ctx)
    row(
      group,
      getString(if (on) R.string.nika_ka_admin_off else R.string.nika_ka_admin_on),
      getString(R.string.nika_ka_admin_summary),
    ) {
      if (on) {
        AlertDialog.Builder(activity ?: return@row)
          .setMessage(R.string.nika_ka_admin_confirm)
          .setPositiveButton(android.R.string.ok) { _, _ ->
            try {
              (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).removeActiveAdmin(admin(ctx))
            } catch (e: RuntimeException) {
              // skip
            }
            rebuild()
          }
          .setNegativeButton(android.R.string.cancel, null)
          .show()
      } else {
        open(
          Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(ctx))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.nika_ka_admin_summary))
        )
      }
    }
  }

  private fun row(group: PreferenceGroup, title: String, summary: String, click: () -> Unit) {
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
