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

package com.google.android.accessibility.talkback.gesture

import android.content.Context
import com.google.android.accessibility.utils.SharedPreferencesUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * Nika: gesture profiles. A profile is a saved copy of every gesture setting: the actions of the
 * gestures, the gestures of single applications, the edge, side and shake gestures. Applying a
 * profile puts those settings back.
 */
object NikaGestureProfiles {
  private const val KEY = "nika_gesture_profiles"
  private val PREFIXES = listOf("pref_shortcut", "nika_app:", "nika_motion_", "nika_iph_", "nika_side_")

  class Profile(val name: String, val values: Map<String, Any>)

  private fun isGestureKey(key: String) = PREFIXES.any { key.startsWith(it) }

  @JvmStatic
  fun load(context: Context): MutableList<Profile> {
    val out = ArrayList<Profile>()
    try {
      val raw = SharedPreferencesUtils.getSharedPreferences(context).getString(KEY, null) ?: return out
      val array = JSONArray(raw)
      for (i in 0 until array.length()) {
        val o = array.getJSONObject(i)
        val vals = o.optJSONObject("v") ?: JSONObject()
        val map = LinkedHashMap<String, Any>()
        for (k in vals.keys()) map[k] = vals.get(k)
        out.add(Profile(o.optString("n"), map))
      }
    } catch (e: Exception) {
      // an empty list
    }
    return out
  }

  private fun save(context: Context, list: List<Profile>) {
    val array = JSONArray()
    for (p in list) {
      val v = JSONObject()
      for ((k, x) in p.values) v.put(k, x)
      array.put(JSONObject().put("n", p.name).put("v", v))
    }
    SharedPreferencesUtils.getSharedPreferences(context).edit().putString(KEY, array.toString()).apply()
  }

  /** Saves the gesture settings that are in use now. A profile with the same name is replaced. */
  @JvmStatic
  fun saveCurrent(context: Context, name: String) {
    val values = LinkedHashMap<String, Any>()
    for ((k, v) in SharedPreferencesUtils.getSharedPreferences(context).all) {
      if (v != null && isGestureKey(k) && (v is String || v is Boolean || v is Int || v is Long || v is Float)) {
        values[k] = v
      }
    }
    val list = load(context)
    list.removeAll { it.name == name }
    list.add(Profile(name, values))
    save(context, list)
  }

  @JvmStatic
  fun apply(context: Context, name: String) {
    val profile = load(context).firstOrNull { it.name == name } ?: return
    val prefs = SharedPreferencesUtils.getSharedPreferences(context)
    val editor = prefs.edit()
    // Settings that the profile does not have go back to their defaults.
    for (k in prefs.all.keys) if (isGestureKey(k) && !profile.values.containsKey(k)) editor.remove(k)
    for ((k, v) in profile.values) {
      when (v) {
        is String -> editor.putString(k, v)
        is Boolean -> editor.putBoolean(k, v)
        is Int -> editor.putInt(k, v)
        is Long -> editor.putLong(k, v)
        is Number -> editor.putFloat(k, v.toFloat())
      }
    }
    editor.apply()
  }

  @JvmStatic
  fun delete(context: Context, name: String) {
    val list = load(context)
    list.removeAll { it.name == name }
    save(context, list)
  }
}
