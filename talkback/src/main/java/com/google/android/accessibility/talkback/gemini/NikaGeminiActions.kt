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

package com.google.android.accessibility.talkback.gemini

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.StringRes
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.SharedPreferencesUtils

/**
 * Nika: actions for the Gemini app that can be assigned to gestures and keys. Each one finds the
 * button on the screen by words in its name (the words can be changed in the settings) and
 * presses it.
 */
object NikaGeminiActions {

  private const val GEMINI_PACKAGE = "com.google.android.googlequicksearchbox"

  /** [value] is saved in the gesture preference, so it must never change once released. */
  class Action(val value: String, @StringRes val labelRes: Int, val defaults: String)

  @JvmField
  val ACTIONS: List<Action> =
    listOf(
      Action("nika_gem_live", R.string.nika_act_gem_live, "live,лайв"),
      Action("nika_gem_video", R.string.nika_act_gem_video, "камеру,камера,camera"),
      Action("nika_gem_screen", R.string.nika_act_gem_screen, "показати екран,поділитися екраном,share screen,show screen"),
      Action("nika_gem_text", R.string.nika_act_gem_text, "запитайте gemini,ask gemini,спросите gemini"),
      Action("nika_gem_learn_live", R.string.nika_act_gem_learn_live, ""),
      Action("nika_gem_learn_video", R.string.nika_act_gem_learn_video, ""),
      Action("nika_gem_learn_screen", R.string.nika_act_gem_learn_screen, ""),
      Action("nika_gem_learn_text", R.string.nika_act_gem_learn_text, ""),
      Action("nika_gem_dump", R.string.nika_act_gem_dump, ""),
    )

  private fun find(value: String?): Action? = ACTIONS.firstOrNull { it.value == value }

  @JvmStatic fun isAction(value: String?): Boolean = find(value) != null

  @JvmStatic
  fun label(context: Context, value: String?): String? =
    find(value)?.let { context.getString(it.labelRes) }

  @JvmStatic
  fun keywordsKey(value: String): String = "${value}_words"

  @JvmStatic
  fun defaultsOf(value: String): String = find(value)?.defaults ?: ""

  private fun learnedKey(value: String) = "${value}_learned"

  private fun prefsRead(service: TalkBackService, key: String): String? =
    android.preference.PreferenceManager.getDefaultSharedPreferences(service).getString(key, null)
      ?: SharedPreferencesUtils.getSharedPreferences(service).getString(key, null)

  /** The roots to search: the windows of the Google app (Gemini), or the active window. */
  private fun roots(service: TalkBackService): List<AccessibilityNodeInfo> {
    val result = ArrayList<AccessibilityNodeInfo>()
    try {
      for (w in service.windows) {
        val r = w.root ?: continue
        if (r.packageName?.toString() == GEMINI_PACKAGE) result.add(r)
      }
    } catch (e: RuntimeException) {
      // use the active window below
    }
    if (result.isEmpty()) service.rootInActiveWindow?.let { result.add(it) }
    return result
  }

  @JvmStatic
  fun perform(service: TalkBackService, value: String): Boolean {
    val action = find(value) ?: return false
    try {
      val roots = roots(service)
      if (roots.isEmpty()) {
        say(service, service.getString(R.string.nika_gem_not_found))
        return true
      }
      if (value == "nika_gem_dump") {
        dump(service, roots)
        return true
      }
      if (value.startsWith("nika_gem_learn_")) {
        learn(service, value.removePrefix("nika_gem_learn_"), roots)
        return true
      }
      val editOnly = value == "nika_gem_text"
      val saved = prefsRead(service, keywordsKey(value))
      val words =
        (saved?.takeIf { it.isNotBlank() } ?: action.defaults)
          .lowercase()
          .split(",")
          .map { it.trim() }
          .filter { it.isNotEmpty() }
      val all = ArrayList<AccessibilityNodeInfo>()
      for (r in roots) collect(r, all)
      var found: AccessibilityNodeInfo? = null
      // 1. The button that was remembered by the "remember the button" action.
      val learned = prefsRead(service, learnedKey(value))
      if (!learned.isNullOrEmpty()) {
        val id = learned.substringBefore("|")
        val name = learned.substringAfter("|", "")
        found =
          all.firstOrNull { n ->
            n.isVisibleToUser &&
              ((id.isNotEmpty() && n.viewIdResourceName == id && (name.isEmpty() || labelOf(n) == name)) ||
                (id.isEmpty() && name.isNotEmpty() && labelOf(n) == name))
          }
          ?: all.firstOrNull { n -> n.isVisibleToUser && id.isNotEmpty() && n.viewIdResourceName == id }
      }
      // 2. The words from the settings.
      if (found == null) {
        loop@ for (w in words) {
          for (n in all) {
            if (!n.isVisibleToUser) continue
            if (editOnly && !n.isEditable && n.className?.toString()?.contains("EditText") != true) continue
            if (labelOf(n).contains(w)) {
              found = n
              break@loop
            }
          }
        }
      }
      if (found == null) {
        say(service, service.getString(R.string.nika_gem_not_found))
        return true
      }
      if (editOnly) {
        found.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        found.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
      } else {
        val target = clickable(found)
        if (target == null) {
          say(service, service.getString(R.string.nika_gem_not_found))
        } else {
          target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
      }
    } catch (e: RuntimeException) {
      say(service, service.getString(R.string.nika_gem_not_found))
    }
    return true
  }

  /** Remembers the button that has the screen reader focus, as the button for this action. */
  private fun learn(service: TalkBackService, name: String, roots: List<AccessibilityNodeInfo>) {
    var focused: AccessibilityNodeInfo? = null
    for (r in roots) {
      focused = r.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
      if (focused != null) break
    }
    if (focused == null) {
      say(service, service.getString(R.string.nika_gem_learn_none))
      return
    }
    val id = focused.viewIdResourceName ?: ""
    val label = labelOf(focused)
    val value = "nika_gem_$name"
    try {
      android.preference.PreferenceManager.getDefaultSharedPreferences(service)
        .edit()
        .putString(learnedKey(value), "$id|$label")
        .apply()
      SharedPreferencesUtils.getSharedPreferences(service)
        .edit()
        .putString(learnedKey(value), "$id|$label")
        .apply()
    } catch (e: RuntimeException) {
      // skip
    }
    say(service, service.getString(R.string.nika_gem_learned, label.ifEmpty { id }))
  }

  private fun labelOf(node: AccessibilityNodeInfo): String =
    ((node.contentDescription ?: node.text ?: node.hintText ?: "").toString()).lowercase()

  private fun collect(root: AccessibilityNodeInfo, out: MutableList<AccessibilityNodeInfo>) {
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.addLast(root)
    var n = 0
    while (stack.isNotEmpty() && n < 1500) {
      val node = stack.removeFirst()
      n++
      out.add(node)
      for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
    }
  }

  private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    var n: AccessibilityNodeInfo? = node
    var depth = 0
    while (n != null && depth < 6) {
      if (n.isClickable) return n
      n = n.parent
      depth++
    }
    return null
  }

  /** Copies the names of all buttons on the screen, so the words in the settings can be chosen. */
  private fun dump(service: TalkBackService, roots: List<AccessibilityNodeInfo>) {
    val lines = ArrayList<String>()
    val all = ArrayList<AccessibilityNodeInfo>()
    for (r in roots) collect(r, all)
    for (n in all) {
      val label = labelOf(n)
      if (n.isVisibleToUser && (n.isClickable || n.isEditable) && (label.isNotEmpty() || n.viewIdResourceName != null)) {
        lines.add((n.viewIdResourceName ?: "") + " | " + label)
      }
    }
    try {
      service
        .getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("nika-gemini", lines.joinToString("\n")))
    } catch (e: RuntimeException) {
      // skip
    }
    say(service, service.getString(R.string.nika_gem_dumped, lines.size))
  }

  private fun say(service: TalkBackService, text: String) {
    try {
      service.speechController.speak(
        text,
        com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_FLUSH_ALL,
        com.google.android.accessibility.utils.output.FeedbackItem.FLAG_NO_HISTORY or
          com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
        null,
        com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED,
      )
    } catch (e: RuntimeException) {
      // skip
    }
  }
}
