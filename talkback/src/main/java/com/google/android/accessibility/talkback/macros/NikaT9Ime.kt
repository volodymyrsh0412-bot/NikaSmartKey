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
package com.google.android.accessibility.talkback.macros

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.NikaDiag
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/**
 * Nika SmartKey: a keyboard without any screen. The letters are typed with the number buttons of the phone,
 * as on old phones: press "2" once for the first letter, again and again for the next ones.
 * Star changes the language (Ukrainian, English, digits). Pound changes the size of the letters.
 * A long press of pound is left to the phone.
 */
class NikaT9Ime : InputMethodService() {
  private val handler = Handler(Looper.getMainLooper())
  private val commit = Runnable { finish() }
  private var mode = 0
  private var shift = 0
  private var capNow = false
  private var lastKey = -1
  private var tap = 0
  private var lastTime = 0L
  private var composing = false

  override fun onEvaluateInputViewShown(): Boolean = false

  override fun onEvaluateFullscreenMode(): Boolean = false

  override fun onFinishInput() {
    finish()
    super.onFinishInput()
  }

  private fun digitOf(code: Int): Int =
    when {
      code in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> code - KeyEvent.KEYCODE_0
      code in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> code - KeyEvent.KEYCODE_NUMPAD_0
      else -> -1
    }

  override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
    val ic = currentInputConnection ?: return super.onKeyDown(keyCode, event)
    NikaDiag.log("t9", "key down " + keyCode + " repeat " + event.repeatCount)
    val d = digitOf(keyCode)
    if (d >= 0) {
      if (event.repeatCount == 0) press(d)
      return true
    }
    when (keyCode) {
      KeyEvent.KEYCODE_STAR -> {
        if (event.repeatCount == 0) switchMode()
        return true
      }
      KeyEvent.KEYCODE_POUND -> return true
      KeyEvent.KEYCODE_DEL -> {
        if (composing) {
          handler.removeCallbacks(commit)
          ic.setComposingText("", 1)
          composing = false
          lastKey = -1
        } else {
          ic.deleteSurroundingText(1, 0)
        }
        return true
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
    if (currentInputConnection == null) return super.onKeyUp(keyCode, event)
    val d = digitOf(keyCode)
    if (d >= 0 || keyCode == KeyEvent.KEYCODE_STAR) return true
    if (keyCode == KeyEvent.KEYCODE_POUND) {
      if (event.eventTime - event.downTime < 450L) toggleShift()
      return true
    }
    return super.onKeyUp(keyCode, event)
  }

  private fun letters(d: Int): String {
    if (mode == 2) return d.toString()
    if (d == 0) return " 0"
    if (d == 1) return ".,?!-:;@1"
    return if (mode == 0) UK[d - 2] + d else EN[d - 2] + d
  }

  private fun press(d: Int) {
    val ic = currentInputConnection ?: return
    if (mode == 2) {
      finish()
      ic.commitText(d.toString(), 1)
      speak(d.toString())
      return
    }
    val chars = letters(d)
    val now = SystemClock.uptimeMillis()
    if (d == lastKey && composing && now - lastTime < 1000L) {
      tap = (tap + 1) % chars.length
    } else {
      finish()
      tap = 0
      capNow = shift != 0
    }
    lastKey = d
    lastTime = now
    var ch = chars[tap].toString()
    if (capNow) ch = ch.uppercase()
    ic.setComposingText(ch, 1)
    composing = true
    speak(if (ch == " ") getString(R.string.nika_t9_space) else ch)
    handler.removeCallbacks(commit)
    handler.postDelayed(commit, 1000L)
  }

  private fun finish() {
    handler.removeCallbacks(commit)
    if (composing) {
      currentInputConnection?.finishComposingText()
      composing = false
      if (shift == 1) shift = 0
    }
    lastKey = -1
  }

  private fun switchMode() {
    finish()
    mode = (mode + 1) % 3
    speak(getString(when (mode) { 0 -> R.string.nika_t9_m_uk; 1 -> R.string.nika_t9_m_en; else -> R.string.nika_t9_m_dg }))
  }

  private fun toggleShift() {
    finish()
    shift = (shift + 1) % 3
    speak(getString(when (shift) { 1 -> R.string.nika_t9_cap1; 2 -> R.string.nika_t9_cap2; else -> R.string.nika_t9_cap0 }))
  }

  private fun speak(text: String) {
    try {
      TalkBackService.getInstance()
        ?.speechController
        ?.speak(
          text,
          SpeechController.QUEUE_MODE_FLUSH_ALL,
          FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
          null,
          Performance.EVENT_ID_UNTRACKED,
        )
    } catch (e: RuntimeException) {
      // skip
    }
  }

  companion object {
    private val UK = listOf("абвгґ", "дежєз", "иіїйк", "лмноп", "рстуф", "хцчшщ", "ьюя", "")
    private val EN = listOf("abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz")
  }
}
