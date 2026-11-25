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

package com.google.android.accessibility.talkback.notifications

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.text.TextUtils
import android.text.format.DateFormat
import com.google.android.accessibility.utils.SharedPreferencesUtils
import java.util.Date

/**
 * Nika: builds the spoken text of a notification from the parts the user chose: app name, time,
 * type, title and text. With the default choices the text is the same as before.
 */
object NikaNotificationFormat {
  const val PREF_APP = "nika_notif_app"
  const val PREF_TIME = "nika_notif_time"
  const val PREF_TYPE = "nika_notif_type"
  const val PREF_HEAD = "nika_notif_head"
  const val PREF_TEXT = "nika_notif_text"
  const val PREF_TEMPLATE = "nika_notif_template"
  const val PREF_NO_EMOJI = "nika_notif_noemoji"
  const val PREF_MAX_WORDS = "nika_notif_maxwords"
  const val PREF_MUTED = "nika_notif_muted"
  const val PREF_HISTORY_ON = "nika_notif_history_on"

  private val EMOJI = Regex("[\\p{So}\\p{Sk}\\uFE0F\\u200D]+")

  /** Packages whose notifications are not read. */
  @JvmStatic
  fun mutedApps(context: Context): MutableSet<String> {
    val raw = SharedPreferencesUtils.getSharedPreferences(context).getString(PREF_MUTED, "") ?: ""
    return raw.split("\n").filter { it.isNotEmpty() }.toMutableSet()
  }

  @JvmStatic
  fun saveMutedApps(context: Context, apps: Set<String>) {
    SharedPreferencesUtils.getSharedPreferences(context)
      .edit()
      .putString(PREF_MUTED, apps.joinToString("\n"))
      .apply()
  }

  @JvmStatic
  fun compose(
    context: Context,
    notification: Notification?,
    packageName: CharSequence?,
    type: CharSequence,
    defaultText: CharSequence,
  ): CharSequence {
    if (notification == null || Notification.CATEGORY_CALL == notification.category) {
      return defaultText
    }
    val prefs = SharedPreferencesUtils.getSharedPreferences(context)
    val extras = notification.extras
    val title = extras?.getCharSequence("android.title")
    val body = extras?.getCharSequence("android.text")
    val millis = if (notification.`when` > 0) notification.`when` else System.currentTimeMillis()
    if (prefs.getBoolean(PREF_HISTORY_ON, true)) {
      NikaNotificationHistory.add(
        context,
        packageName?.toString() ?: "",
        appLabel(context, packageName)?.toString() ?: "",
        listOf(title, body).filter { !TextUtils.isEmpty(it) }.joinToString(". "),
        millis,
      )
    }
    if (packageName != null && mutedApps(context).contains(packageName.toString())) {
      return ""
    }
    val app = prefs.getBoolean(PREF_APP, false)
    val time = prefs.getBoolean(PREF_TIME, false)
    val typeOn = prefs.getBoolean(PREF_TYPE, true)
    val head = prefs.getBoolean(PREF_HEAD, true)
    val text = prefs.getBoolean(PREF_TEXT, true)
    val template = prefs.getString(PREF_TEMPLATE, "") ?: ""
    val noEmoji = prefs.getBoolean(PREF_NO_EMOJI, false)
    val maxWords = (prefs.getString(PREF_MAX_WORDS, "0") ?: "0").toIntOrNull() ?: 0
    if (!app && !time && typeOn && head && text && template.isEmpty() && !noEmoji && maxWords <= 0) {
      return defaultText
    }
    val bodyText: CharSequence? =
      if (!TextUtils.isEmpty(body)) {
        body
      } else if (
        !TextUtils.isEmpty(notification.tickerText) &&
          notification.tickerText.toString().trim() != title?.toString()?.trim()
      ) {
        notification.tickerText
      } else {
        null
      }
    val timeText = DateFormat.getTimeFormat(context).format(Date(millis))
    var result: CharSequence
    if (template.isNotEmpty()) {
      result =
        template
          .replace("{app}", appLabel(context, packageName)?.toString() ?: "")
          .replace("{time}", timeText)
          .replace("{category}", type.toString())
          .replace("{title}", title?.toString() ?: "")
          .replace("{text}", bodyText?.toString() ?: "")
          .replace(Regex("\\s+"), " ")
          .trim()
    } else {
      val parts = ArrayList<CharSequence>()
      if (app) {
        appLabel(context, packageName)?.let { parts.add(it) }
      }
      if (time) {
        parts.add(timeText)
      }
      if (typeOn && !TextUtils.isEmpty(type)) {
        parts.add(type)
      }
      if (head && !TextUtils.isEmpty(title)) {
        parts.add(title!!)
      }
      if (text && bodyText != null) {
        parts.add(bodyText)
      }
      result = TextUtils.join(". ", parts)
    }
    var out = result.toString()
    if (noEmoji) out = EMOJI.replace(out, " ").replace(Regex("\\s+"), " ").trim()
    if (maxWords > 0) {
      val words = out.split(" ").filter { it.isNotEmpty() }
      if (words.size > maxWords) out = words.take(maxWords).joinToString(" ")
    }
    return out
  }

  private fun appLabel(context: Context, packageName: CharSequence?): CharSequence? {
    val pkg = packageName?.toString() ?: return null
    return try {
      val pm = context.packageManager
      pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
    } catch (e: PackageManager.NameNotFoundException) {
      pkg
    } catch (e: RuntimeException) {
      pkg
    }
  }
}
