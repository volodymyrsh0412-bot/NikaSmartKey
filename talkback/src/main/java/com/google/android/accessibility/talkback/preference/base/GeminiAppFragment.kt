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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.nikafocus.NikaGeminiVisual
import com.google.android.accessibility.utils.SharedPreferencesUtils

/** Nika: the app that the visual helper opens. By default Nika finds Gemini itself. */
class GeminiAppFragment : TalkbackBaseFragment() {
  public override fun getTitle(): CharSequence = getText(R.string.nika_gv_app_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    build()
  }

  override fun onResume() {
    super.onResume()
    build()
  }

  private fun labelOf(context: Context, pkg: String): String =
    try {
      val pm = context.packageManager
      pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (e: Exception) {
      pkg
    }

  private fun build() {
    val context = context ?: return
    val screen = preferenceManager.createPreferenceScreen(context)
    val chosen = NikaGeminiVisual.chosenApp(context)
    val summary = if (chosen.isEmpty()) getString(R.string.nika_gv_app_auto) else labelOf(context, chosen)
    screen.addPreference(
      Preference(context).apply {
        isPersistent = false
        layoutResource = R.layout.listitem_2texts
        setTitle(R.string.nika_gv_app_choose)
        setSummary(summary)
        setOnPreferenceClickListener {
          choose()
          true
        }
      }
    )
    preferenceScreen = screen
  }

  private fun choose() {
    val act = activity ?: return
    val pm = act.packageManager
    val apps =
      try {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
          .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
          .distinctBy { it.second }
          .sortedBy { it.first.lowercase() }
      } catch (e: RuntimeException) {
        emptyList()
      }
    if (apps.isEmpty()) {
      Toast.makeText(act, R.string.nika_vc_empty_list, Toast.LENGTH_LONG).show()
      return
    }
    val names = ArrayList<String>()
    names.add(getString(R.string.nika_gv_app_auto))
    for (a in apps) names.add(a.first)
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_gv_app_choose)
      .setItems(names.toTypedArray()) { _, which ->
        val pkg = if (which == 0) "" else apps[which - 1].second
        SharedPreferencesUtils.getSharedPreferences(act).edit().putString(NikaGeminiVisual.PREF_APP, pkg).apply()
        build()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
