/*
 * Copyright 2026 Nika contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.accessibility.talkback.macros

import android.os.Handler
import android.os.Looper
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/** Help mode: a pressed key is not used, Nika only says what is assigned to it (like the input help of NVDA). */
object NikaHelpMode {
  const val ACTION = "nika_help_mode"

  @JvmField @Volatile var on = false

  private const val AUTO_OFF_MS = 180_000L
  private val handler = Handler(Looper.getMainLooper())
  private var offTask: Runnable? = null

  @JvmStatic
  fun toggle(service: TalkBackService) {
    on = !on
    offTask?.let { handler.removeCallbacks(it) }
    offTask = null
    if (on) {
      touch(service)
      say(service, service.getString(R.string.nika_help_on))
    } else {
      say(service, service.getString(R.string.nika_help_off))
    }
  }

  /** Every key press in the help mode postpones the automatic end of it. */
  @JvmStatic
  fun touch(service: TalkBackService) {
    offTask?.let { handler.removeCallbacks(it) }
    val task = Runnable { if (on) toggle(service) }
    offTask = task
    handler.postDelayed(task, AUTO_OFF_MS)
  }

  @JvmStatic
  fun say(service: TalkBackService, text: String) {
    try {
      service.speechController.speak(
        text,
        SpeechController.QUEUE_MODE_FLUSH_ALL,
        FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
        null,
        com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED,
      )
    } catch (e: RuntimeException) {
      // skip
    }
  }

  @JvmStatic
  fun actionLabel(service: TalkBackService, value: String): String =
    NikaMacroActions.label(service, value)
      ?: try {
        com.google.android.accessibility.talkback.gesture.GestureShortcutMapping.getActionString(service, value)
      } catch (e: RuntimeException) {
        value
      }

  @JvmStatic
  fun keyName(service: TalkBackService, code: Int): String =
    when (code) {
      android.view.KeyEvent.KEYCODE_VOLUME_UP -> service.getString(R.string.nika_help_key_vol_up)
      android.view.KeyEvent.KEYCODE_VOLUME_DOWN -> service.getString(R.string.nika_help_key_vol_down)
      android.view.KeyEvent.KEYCODE_STAR -> service.getString(R.string.nika_help_key_star)
      android.view.KeyEvent.KEYCODE_POUND -> service.getString(R.string.nika_help_key_pound)
      else ->
        android.view.KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ').lowercase()
    }
}
