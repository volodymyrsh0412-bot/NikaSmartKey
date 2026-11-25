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

import android.content.Context
import com.google.android.accessibility.utils.SharedPreferencesUtils
import org.json.JSONArray
import org.json.JSONObject

/** Nika: the last notifications that were announced, newest first. */
object NikaNotificationHistory {
  private const val KEY = "nika_notif_history"
  private const val LIMIT = 60

  class Entry(val pkg: String, val app: String, val text: String, val time: Long)

  @JvmStatic
  @Synchronized
  fun add(context: Context, pkg: String, app: String, text: String, time: Long) {
    if (text.isEmpty()) return
    try {
      val list = load(context).toMutableList()
      // The same notification can be announced twice (an update); keep one.
      if (list.firstOrNull()?.let { it.pkg == pkg && it.text == text } == true) return
      list.add(0, Entry(pkg, app, text, time))
      save(context, list.take(LIMIT))
    } catch (e: RuntimeException) {
      // the history is only a help
    }
  }

  @JvmStatic
  fun load(context: Context): List<Entry> {
    val out = ArrayList<Entry>()
    try {
      val raw = SharedPreferencesUtils.getSharedPreferences(context).getString(KEY, null) ?: return out
      val array = JSONArray(raw)
      for (i in 0 until array.length()) {
        val o = array.getJSONObject(i)
        out.add(Entry(o.optString("p"), o.optString("a"), o.optString("t"), o.optLong("m")))
      }
    } catch (e: Exception) {
      // an empty list
    }
    return out
  }

  private fun save(context: Context, list: List<Entry>) {
    val array = JSONArray()
    for (e in list) {
      array.put(JSONObject().put("p", e.pkg).put("a", e.app).put("t", e.text).put("m", e.time))
    }
    SharedPreferencesUtils.getSharedPreferences(context).edit().putString(KEY, array.toString()).apply()
  }

  /** Removes one entry (the same time and text). */
  @JvmStatic
  @Synchronized
  fun remove(context: Context, entry: Entry) {
    try {
      save(context, load(context).filter { !(it.time == entry.time && it.text == entry.text && it.pkg == entry.pkg) })
    } catch (e: RuntimeException) {
      // skip
    }
  }

  @JvmStatic
  fun clear(context: Context) {
    SharedPreferencesUtils.getSharedPreferences(context).edit().remove(KEY).apply()
  }
}
