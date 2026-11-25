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
package com.google.android.accessibility.talkback.nikafocus

import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/**
 * Nika: one gesture chooses the next or previous setting, another one changes its value to the
 * next or previous. The new choice is spoken. This is for settings that are changed often.
 */
object NikaValueActions {
  const val SETTING_NEXT = "nika_val_setting_next"
  const val SETTING_PREV = "nika_val_setting_prev"
  const val VALUE_NEXT = "nika_val_value_next"
  const val VALUE_PREV = "nika_val_value_prev"
  const val SLIDER_UP = "nika_val_slider_up"
  const val SLIDER_DOWN = "nika_val_slider_down"
  const val CURSOR_RIGHT = "nika_val_cursor_right"
  const val CURSOR_LEFT = "nika_val_cursor_left"

  /** A list setting: its key, the value when nothing is stored, and the arrays of its choices. */
  private class Setting(
    val key: String,
    val fallback: String,
    val entries: String,
    val values: String,
    val title: Int,
  )

  private val SETTINGS =
    listOf(
      Setting("pref_keyboard_echo", "0", "pref_keyboard_echo_entries", "pref_keyboard_echo_values", R.string.nika_val_keyboard_echo),
      Setting("pref_punctuation_verbosity", "2", "pref_punctuation_entries", "pref_punctuation_values", R.string.nika_val_punctuation),
      Setting("pref_emoji_speech", "engine", "pref_emoji_speech_entries", "pref_emoji_speech_values", R.string.nika_val_emoji),
    )

  private var current = 0

  @JvmStatic
  fun isAction(value: String?): Boolean =
    value == SETTING_NEXT || value == SETTING_PREV || value == VALUE_NEXT || value == VALUE_PREV ||
      value == SLIDER_UP || value == SLIDER_DOWN || value == CURSOR_RIGHT || value == CURSOR_LEFT

  @JvmStatic
  fun perform(service: TalkBackService, action: String): Boolean {
    if (!isAction(action)) return false
    try {
      when (action) {
        SETTING_NEXT -> {
          current = (current + 1) % SETTINGS.size
          announce(service, false)
        }
        SETTING_PREV -> {
          current = (current + SETTINGS.size - 1) % SETTINGS.size
          announce(service, false)
        }
        VALUE_NEXT -> change(service, 1)
        VALUE_PREV -> change(service, -1)
        SLIDER_UP -> slider(service, true)
        SLIDER_DOWN -> slider(service, false)
        CURSOR_RIGHT -> cursor(service, 1)
        CURSOR_LEFT -> cursor(service, -1)
      }
    } catch (e: RuntimeException) {
      // skip
    }
    return true
  }

  /** Moves the slider in focus (or the slider that holds the focused item) one step. */
  private fun slider(service: TalkBackService, up: Boolean) {
    var node: AccessibilityNodeInfo? = null
    for (w in service.windows) {
      node = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
      if (node != null) break
    }
    var steps = 0
    while (node != null && steps < 4) {
      if (node.rangeInfo != null) {
        node.performAction(
          if (up) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        )
        return
      }
      node = node.parent
      steps++
    }
  }

  /** Moves the cursor of the text field being edited one character, and says the character passed. */
  private fun cursor(service: TalkBackService, step: Int) {
    var field: AccessibilityNodeInfo? = null
    for (w in service.windows) {
      field = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
      if (field != null) break
    }
    if (field == null || !field.isEditable) return
    val text = field.text?.toString() ?: ""
    val pos = if (field.textSelectionEnd >= 0) field.textSelectionEnd else text.length
    val target = (pos + step).coerceIn(0, text.length)
    val args = Bundle()
    args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, target)
    args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, target)
    field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    val passed = if (step > 0) text.getOrNull(pos) else text.getOrNull(target)
    if (passed != null && target != pos) say(service, passed.toString())
  }

  private fun say(service: TalkBackService, text: String) {
    service.speechController.speak(
      text,
      SpeechController.QUEUE_MODE_FLUSH_ALL,
      FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
      null,
      Performance.EVENT_ID_UNTRACKED,
    )
  }

  private fun array(context: Context, name: String): Array<String> {
    val id = context.resources.getIdentifier(name, "array", context.packageName)
    return if (id == 0) emptyArray() else context.resources.getStringArray(id)
  }

  private fun change(service: TalkBackService, step: Int) {
    val s = SETTINGS[current]
    val values = array(service, s.values)
    if (values.isEmpty()) return
    val prefs = SharedPreferencesUtils.getSharedPreferences(service)
    val now = prefs.getString(s.key, s.fallback) ?: s.fallback
    val index = values.indexOf(now).let { if (it < 0) 0 else it }
    val next = (index + step + values.size) % values.size
    prefs.edit().putString(s.key, values[next]).apply()
    announce(service, true)
  }

  private fun announce(service: TalkBackService, valueOnly: Boolean) {
    val s = SETTINGS[current]
    val values = array(service, s.values)
    val entries = array(service, s.entries)
    val prefs = SharedPreferencesUtils.getSharedPreferences(service)
    val now = prefs.getString(s.key, s.fallback) ?: s.fallback
    val i = values.indexOf(now)
    val label = if (i in entries.indices) entries[i] else now
    val text = if (valueOnly) label else service.getString(R.string.nika_val_say, service.getString(s.title), label)
    service.speechController.speak(
      text,
      SpeechController.QUEUE_MODE_FLUSH_ALL,
      FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
      null,
      Performance.EVENT_ID_UNTRACKED,
    )
  }
}
