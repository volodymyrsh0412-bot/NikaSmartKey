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

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.BoolRes
import androidx.annotation.StringRes
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/** Nika: a touch like a finger on the item in focus, and the switches of the one-touch modes. */
object NikaTapActions {
  const val TAP = "nika_tap_finger"
  const val SINGLE = "nika_toggle_single_tap"
  const val DIRECT = "nika_toggle_direct_click"

  @JvmStatic
  fun isAction(value: String?): Boolean = value == TAP || value == SINGLE || value == DIRECT

  @JvmStatic
  fun perform(service: TalkBackService, value: String): Boolean {
    try {
      when (value) {
        TAP -> tap(service)
        SINGLE ->
          toggle(service, R.string.pref_single_tap_key, R.bool.pref_single_tap_default, R.string.nika_act_toggle_single_tap)
        DIRECT ->
          toggle(service, R.string.pref_direct_click_key, R.bool.pref_direct_click_default, R.string.nika_act_toggle_direct_click)
        else -> return false
      }
    } catch (e: RuntimeException) {
      // skip
    }
    return true
  }

  private fun focused(service: TalkBackService): AccessibilityNodeInfo? {
    for (w in service.windows) {
      val root = w.root ?: continue
      val f = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
      if (f != null) return f
    }
    return null
  }

  /** A touch on the middle of the item in focus, as a finger does it. */
  private fun tap(service: TalkBackService) {
    val node = focused(service) ?: return
    val r = Rect()
    node.getBoundsInScreen(r)
    if (r.width() <= 0 || r.height() <= 0) {
      node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
      return
    }
    val path = Path()
    path.moveTo(r.exactCenterX(), r.exactCenterY())
    val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
    service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
  }

  private fun toggle(
    service: TalkBackService,
    @StringRes key: Int,
    @BoolRes default: Int,
    @StringRes title: Int,
  ) {
    val prefs = SharedPreferencesUtils.getSharedPreferences(service)
    val now = prefs.getBoolean(service.getString(key), service.resources.getBoolean(default))
    prefs.edit().putBoolean(service.getString(key), !now).apply()
    val text = service.getString(if (now) R.string.nika_tap_off else R.string.nika_tap_on, service.getString(title))
    service.speechController.speak(
      text,
      SpeechController.QUEUE_MODE_FLUSH_ALL,
      FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
      null,
      Performance.EVENT_ID_UNTRACKED,
    )
  }
}
