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

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.NikaDiag

/**
 * Answers or ends a call of any messenger (or of the phone) without scripts: the button is found by its
 * name on the screen or in the notification at the top. With no call on the screen the media key is sent.
 */
object NikaCallActions {
  const val ACTION = "nika_call_answer"

  private val ANSWER =
    setOf(
      "принять", "принять вызов", "ответить", "прийняти", "прийняти виклик", "відповісти",
      "answer", "answer call", "accept", "accept call", "odbierz", "прыняць", "адказаць",
    )
  private val END =
    setOf(
      "отклонить", "завершить", "завершить вызов", "сбросить", "завершить звонок",
      "відхилити", "завершити", "завершити виклик", "скинути",
      "decline", "hang up", "end call", "end", "reject", "odrzuć", "zakończ", "zakończ rozmowę",
      "адхіліць", "завяршыць",
    )

  @JvmStatic
  fun perform(service: TalkBackService) {
    try {
      if (press(service, ANSWER)) return
      if (press(service, END)) return
    } catch (e: Throwable) {
      NikaDiag.log("call", "failed: $e")
    }
    // No call: the usual action of the two-finger double tap, play or pause.
    try {
      val audio = service.getSystemService(android.media.AudioManager::class.java)
      audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
      audio?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    } catch (e: RuntimeException) {
      // skip
    }
  }

  private fun press(service: TalkBackService, names: Set<String>): Boolean {
    for (w in service.windows) {
      val root = w.root ?: continue
      val found = find(root, names, 0)
      if (found != null) {
        NikaDiag.log("call", "found: " + found.text + " / " + found.contentDescription)
        return click(service, found)
      }
    }
    return false
  }

  private fun label(node: AccessibilityNodeInfo): String =
    (node.contentDescription ?: node.text ?: "").toString().trim().lowercase().trimEnd('.', '!')

  private fun find(node: AccessibilityNodeInfo, names: Set<String>, depth: Int): AccessibilityNodeInfo? {
    if (depth > 40) return null
    if (node.isVisibleToUser && names.contains(label(node))) return node
    for (i in 0 until node.childCount) {
      val child = node.getChild(i) ?: continue
      val r = find(child, names, depth + 1)
      if (r != null) return r
    }
    return null
  }

  private fun click(service: TalkBackService, node: AccessibilityNodeInfo): Boolean {
    var n: AccessibilityNodeInfo? = node
    var up = 0
    while (n != null && up < 4) {
      if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
      n = n.parent
      up++
    }
    // The name is a separate text under a round button: touch the screen just above it.
    val b = Rect()
    node.getBoundsInScreen(b)
    val x = b.exactCenterX()
    val y = (b.top - b.height() * 3.5f).coerceAtLeast(1f)
    val path = Path()
    path.moveTo(x, y)
    val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
    return service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
  }
}
