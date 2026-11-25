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

package com.google.android.accessibility.talkback.progress

import android.content.Context
import android.content.SharedPreferences
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.preference.PreferenceManager
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils
import com.google.android.accessibility.utils.Role
import com.google.android.accessibility.utils.SharedPreferencesUtils
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController
import java.util.Optional
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Nika: how progress indicators are announced. The mode chooses voice, sound (the rising tone that
 * the screen reader already plays), both, or nothing. The voice can say the value in percent, and
 * only every few percent.
 */
object NikaProgress {

  private const val PREF_MODE = "nika_progress_mode"
  private const val PREF_PERCENT = "nika_progress_percent"
  private const val PREF_STEP = "nika_progress_step"

  private var lastSpoken = -1000
  private var lastBackgroundSpoken = -1000
  private var lastBackgroundAt = 0L
  private var lastBackgroundTone = 0L

  private fun stores(context: Context): List<SharedPreferences> {
    val list = ArrayList<SharedPreferences>(2)
    try {
      list.add(PreferenceManager.getDefaultSharedPreferences(context))
    } catch (e: RuntimeException) {
      // skip
    }
    try {
      val other = SharedPreferencesUtils.getSharedPreferences(context)
      if (list.none { it === other }) list.add(other)
    } catch (e: RuntimeException) {
      // skip
    }
    return list
  }

  private fun string(key: String, default: String): String {
    val context = TalkBackService.getInstance() ?: return default
    for (store in stores(context)) {
      if (store.contains(key)) {
        return try {
          store.getString(key, default) ?: default
        } catch (e: ClassCastException) {
          default
        }
      }
    }
    return default
  }

  private fun bool(key: String, default: Boolean): Boolean {
    val context = TalkBackService.getInstance() ?: return default
    for (store in stores(context)) {
      if (store.contains(key)) {
        return try {
          store.getBoolean(key, default)
        } catch (e: ClassCastException) {
          default
        }
      }
    }
    return default
  }

  private fun mode(): String = string(PREF_MODE, "both")

  /** Whether the progress tone may play. */
  @JvmStatic
  fun soundOn(): Boolean {
    val m = mode()
    return m == "both" || m == "sound"
  }

  /** The spoken text for a progress indicator; other nodes keep their text. */
  @JvmStatic
  fun speech(node: AccessibilityNodeInfoCompat?, text: CharSequence?): CharSequence? {
    if (node == null || Role.getRole(node) != Role.ROLE_PROGRESS_BAR) return text
    val m = mode()
    if (m == "sound" || m == "off") return ""
    if (text.isNullOrEmpty()) return text
    val percent = AccessibilityNodeInfoUtils.getProgressPercent(node)
    if (percent.isNaN() || percent < 0f || percent > 100.5f) return text
    val value = percent.roundToInt()
    val step = string(PREF_STEP, "5").toIntOrNull() ?: 5
    val due = lastSpoken < 0 || value >= 100 || value < lastSpoken || abs(value - lastSpoken) >= step
    if (!due) return ""
    lastSpoken = value
    if (!bool(PREF_PERCENT, true)) return text
    val context = TalkBackService.getInstance() ?: return text
    return context.getString(R.string.nika_progress_percent_fmt, value)
  }

  /** Same as [speech] for the compositor rules that keep the text in an Optional. */
  @JvmStatic
  fun speech(
    node: AccessibilityNodeInfoCompat?,
    text: Optional<CharSequence>,
  ): Optional<CharSequence> {
    val out = speech(node, text.orElse(null))
    return if (out == null) text else Optional.of(out)
  }

  /**
   * A progress indicator that is not in focus (a download, an install) is not announced by the
   * usual rules. Here it is announced by the same mode: a tone, or the percent, or both.
   */
  @JvmStatic
  fun onEvent(service: TalkBackService, event: AccessibilityEvent?) {
    if (event == null) return
    val type = event.eventType
    if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && type != AccessibilityEvent.TYPE_VIEW_SELECTED) return
    val m = mode()
    if (m == "off") return
    val source: AccessibilityNodeInfo = event.source ?: return
    try {
      val node = AccessibilityNodeInfoCompat.wrap(source)
      if (Role.getRole(node) != Role.ROLE_PROGRESS_BAR) return
      // The ones in focus or live are announced by the usual rules.
      if (node.isAccessibilityFocused || node.isFocused || node.liveRegion != 0) return
      val percent = AccessibilityNodeInfoUtils.getProgressPercent(node)
      if (percent.isNaN() || percent < 0f || percent > 100.5f) return
      val value = percent.roundToInt()
      val now = SystemClock.uptimeMillis()
      if ((m == "sound" || m == "both") && now - lastBackgroundTone > 400L) {
        lastBackgroundTone = now
        val rate = Math.pow(2.0, (value / 50.0) - 1).toFloat()
        service.feedbackController.playAuditory(R.raw.scroll_tone, rate, 0.5f, null)
      }
      if (m == "voice" || m == "both") {
        val step = string(PREF_STEP, "5").toIntOrNull() ?: 5
        val due =
          lastBackgroundSpoken < 0 ||
            value >= 100 ||
            value < lastBackgroundSpoken ||
            abs(value - lastBackgroundSpoken) >= step
        if (due && now - lastBackgroundAt > 1000L) {
          lastBackgroundSpoken = value
          lastBackgroundAt = now
          val text =
            if (bool(PREF_PERCENT, true)) service.getString(R.string.nika_progress_percent_fmt, value)
            else (node.stateDescription ?: node.text ?: "").toString()
          if (text.isNotEmpty()) {
            service.speechController.speak(
              text,
              SpeechController.QUEUE_MODE_QUEUE,
              FeedbackItem.FLAG_NO_HISTORY,
              null,
              com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED,
            )
          }
        }
      }
    } catch (e: RuntimeException) {
      // an indicator is only a help
    }
  }
}
