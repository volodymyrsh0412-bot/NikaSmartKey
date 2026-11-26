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
      if (phoneCall(service)) return
      if (press(service, ANSWER)) return
      if (viberSwipe(service)) return
      if (reveal(service)) return
      if (press(service, END)) return
      NikaDiag.log("call", "no call button found")
      dump(service)
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


  /**
   * A call of the phone itself (not of a messenger): the headset button answers a ringing call and
   * ends a call in progress, like the media key of the two-finger tap did before.
   */
  private fun phoneCall(service: TalkBackService): Boolean {
    val audio = service.getSystemService(android.media.AudioManager::class.java) ?: return false
    val mode = audio.mode
    val ringing = mode == android.media.AudioManager.MODE_RINGTONE
    val talking = mode == android.media.AudioManager.MODE_IN_CALL
    if (!ringing && !talking) return false
    if (ringing) {
      var dialer = false
      for (w in service.windows) {
        val pkg = w.root?.packageName?.toString() ?: continue
        if (pkg.contains("incallui") || pkg.contains("dialer") || pkg == "com.android.phone" || pkg.contains("telecom")) {
          dialer = true
          break
        }
      }
      if (!dialer) return false
    }
    NikaDiag.log("call", "phone call: " + (if (ringing) "answer" else "end") + " by the headset button")
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK))
    return true
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


  private val INCOMING = listOf("вхідний виклик", "входящий вызов", "incoming call", "вхідний дзвінок", "przychodzące")

  private fun hasPhrase(node: AccessibilityNodeInfo, depth: Int): Boolean {
    if (depth > 40) return false
    val t = ((node.text ?: "").toString() + " " + (node.contentDescription ?: "")).lowercase()
    if (INCOMING.any { t.contains(it) }) return true
    for (i in 0 until node.childCount) {
      val c = node.getChild(i) ?: continue
      if (hasPhrase(c, depth + 1)) return true
    }
    return false
  }

  /** The Viber call screen has no buttons for a screen reader: the round button is swiped to the right. */
  private fun viberSwipe(service: TalkBackService): Boolean {
    for (w in service.windows) {
      val root = w.root ?: continue
      if (root.packageName?.toString() != "com.viber.voip") continue
      if (!hasPhrase(root, 0)) continue
      val b = Rect()
      root.getBoundsInScreen(b)
      val cx = b.left + b.width() * 0.5f
      val cy = b.top + b.height() * 0.605f
      val path = Path()
      path.moveTo(cx, cy)
      path.lineTo(b.left + b.width() * 0.88f, cy)
      NikaDiag.log("call", "viber swipe from " + cx + "," + cy)
      val stroke = GestureDescription.StrokeDescription(path, 0L, 350L)
      return service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }
    return false
  }


  private val CALL_APPS = setOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.viber.voip", "com.facebook.orca", "com.skype.raider")
  private val CALLING =
    listOf(
      "звонок whatsapp", "дзвінок whatsapp", "whatsapp call", "whatsapp voice call", "whatsapp video call",
      "calling", "ringing", "вызываем", "дзвонимо", "звонок telegram", "дзвінок telegram", "telegram call",
    )
  private val handler = android.os.Handler(android.os.Looper.getMainLooper())

  private fun hasAny(node: AccessibilityNodeInfo, phrases: List<String>, depth: Int): Boolean {
    if (depth > 40) return false
    val t = ((node.text ?: "").toString() + " " + (node.contentDescription ?: "")).lowercase()
    if (phrases.any { t.contains(it) }) return true
    for (i in 0 until node.childCount) {
      val c = node.getChild(i) ?: continue
      if (hasAny(c, phrases, depth + 1)) return true
    }
    return false
  }

  /** Some call screens show their buttons only after a touch: touch the screen and look again. */
  private fun reveal(service: TalkBackService): Boolean {
    for (w in service.windows) {
      val root = w.root ?: continue
      if (!CALL_APPS.contains(root.packageName?.toString())) continue
      if (!hasAny(root, CALLING, 0)) continue
      val b = Rect()
      root.getBoundsInScreen(b)
      val path = Path()
      path.moveTo(b.exactCenterX(), b.top + b.height() * 0.4f)
      NikaDiag.log("call", "touch to show the buttons")
      service.dispatchGesture(
        GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, 60L)).build(),
        null,
        null,
      )
      handler.postDelayed(
        {
          try {
            if (!press(service, ANSWER)) press(service, END)
          } catch (e: Throwable) {
            NikaDiag.log("call", "second look failed: $e")
          }
        },
        800,
      )
      return true
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
    val metrics = service.resources.displayMetrics
    val maxW = metrics.widthPixels * 0.6f
    val maxH = metrics.heightPixels * 0.25f
    val r = Rect()
    var n: AccessibilityNodeInfo? = node
    var up = 0
    while (n != null && up < 4) {
      n.getBoundsInScreen(r)
      // A big container is a panel, not the button: its click does nothing.
      if (r.width() > maxW || r.height() > maxH) break
      if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
      n = n.parent
      up++
    }
    node.getBoundsInScreen(r)
    val x = r.exactCenterX()
    // A small text under a round button: touch the screen just above the text.
    val y =
      if (node.isClickable || r.height() > metrics.density * 60) r.exactCenterY()
      else (r.top - r.height() * 3.5f).coerceAtLeast(1f)
    return tapAt(service, x, y)
  }

  private fun tapAt(service: TalkBackService, x: Float, y: Float): Boolean {
    val path = Path()
    path.moveTo(x, y)
    val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
    return service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
  }

  /** For the diary: what the screen reader can see when no button was found. */
  private fun dump(service: TalkBackService) {
    try {
      for (w in service.windows) {
        val root = w.root ?: continue
        val labels = ArrayList<String>()
        collect(root, labels, 0)
        NikaDiag.log("call", "window " + root.packageName + ": " + labels.take(25).joinToString(" | "))
      }
    } catch (e: Throwable) {
      // skip
    }
  }

  private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
    if (depth > 40 || out.size > 25) return
    val l = label(node)
    if (l.isNotEmpty()) out.add(l + (if (node.isClickable) "*" else ""))
    for (i in 0 until node.childCount) {
      val c = node.getChild(i) ?: continue
      collect(c, out, depth + 1)
    }
  }
}
