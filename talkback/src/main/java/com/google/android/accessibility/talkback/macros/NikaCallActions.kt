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
      // A call of the phone itself: the usual action of this gesture does it (answers or ends).
      if (!phoneCall(service)) {
        if (press(service, ANSWER)) return
        if (viberSwipe(service)) return
        if (reveal(service)) return
        if (press(service, END)) return
        NikaDiag.log("call", "no messenger call found")
        dump(service)
      }
    } catch (e: Throwable) {
      NikaDiag.log("call", "failed: $e")
    }
    // Everything else: the standard action of the two-finger double tap (media or voice input, calls).
    try {
      service.performNikaKeyAction("MEDIA_CONTROL_OR_VOICE_INPUT")
    } catch (e: RuntimeException) {
      // skip
    }
  }

  /** The call screen of the phone: its own application is on the screen, or a call is in progress. */
  private fun phoneCall(service: TalkBackService): Boolean {
    val audio = service.getSystemService(android.media.AudioManager::class.java)
    if (audio != null && audio.mode == android.media.AudioManager.MODE_IN_CALL) return true
    for (w in service.windows) {
      val pkg = w.root?.packageName?.toString() ?: continue
      if (pkg.contains("incallui") || pkg.contains("dialer") || pkg == "com.android.phone" || pkg.contains("telecom")) {
        return true
      }
    }
    return false
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

  /**
   * The Viber call screen on the lock screen has no buttons for a screen reader. The user answers it with
   * a double tap and a drag to the right, the second touch stays down (checked in the script for SandFly).
   * The variants are tried one after another until the call is no longer incoming.
   */
  private class Slide(
    val focus: Boolean,
    val ms: Long,
    val hold: Long,
    val wiggle: Float,
    val holdEnd: Boolean,
    val frac: Float,
    val two: Boolean = false,
    val gap: Float = 0f,
    val along: Boolean = false,
  )

  private val SLIDES =
    listOf(
      // Two fingers side by side swipe to the right from the round button: the way the user does it.
      Slide(false, 450, 0, 0f, false, 0.25f, two = true, gap = 120f),
      Slide(false, 300, 0, 0f, false, 0.25f, two = true, gap = 80f),
      Slide(false, 450, 0, 0f, false, 0.25f, two = true, gap = 120f, along = true),
      Slide(false, 700, 0, 0f, false, 0.3f, two = true, gap = 160f),
      Slide(false, 1300, 650, 8f, false, 0.22f),
      Slide(false, 1600, 700, 8f, true, 0.22f),
      Slide(false, 6000, 0, 0f, false, 0.22f),
      Slide(true, 1300, 650, 8f, false, 0.22f),
      Slide(false, 2000, 900, 6f, false, 0.35f),
    )
  private var slideRun = 0

  private fun viberWindowRoot(service: TalkBackService): AccessibilityNodeInfo? {
    for (w in service.windows) {
      val root = w.root ?: continue
      if (root.packageName?.toString() == "com.viber.voip" && hasPhrase(root, 0)) return root
    }
    return null
  }

  private fun viberSwipe(service: TalkBackService): Boolean {
    val root = viberWindowRoot(service) ?: return false
    slideRun += 1
    NikaDiag.log("call", "viber: slide variants start")
    trySlide(service, 0, slideRun)
    return true
  }

  private fun trySlide(service: TalkBackService, step: Int, run: Int) {
    if (run != slideRun) return
    val root = viberWindowRoot(service)
    if (root == null) {
      NikaDiag.log("call", "viber: the call is no longer incoming, variant " + step)
      return
    }
    if (step >= SLIDES.size) {
      NikaDiag.log("call", "viber: all variants did not work")
      return
    }
    val s = SLIDES[step]
    val b = Rect()
    root.getBoundsInScreen(b)
    val w = b.width().toFloat()
    val cx = b.left + w * 0.5f
    val cy = b.top + b.height() * 0.605f
    val tx = minOf(cx + w * s.frac, b.left + w * 0.9f)
    val lead = if (s.focus) 600L else 0L
    val builder = GestureDescription.Builder()
    if (s.two) {
      val ox = if (s.along) s.gap else 0f
      val oy = if (s.along) 0f else s.gap / 2
      val a = Path()
      a.moveTo(cx - ox, cy - oy)
      a.lineTo(tx - ox, cy - oy)
      val c = Path()
      c.moveTo(cx, cy + oy)
      c.lineTo(tx, cy + oy)
      builder.addStroke(GestureDescription.StrokeDescription(a, 0L, s.ms))
      builder.addStroke(GestureDescription.StrokeDescription(c, 0L, s.ms))
      val okTwo = service.dispatchGesture(builder.build(), null, null)
      NikaDiag.log("call", "viber: variant " + step + " two fingers, gap " + s.gap + (if (s.along) " along" else "") + ", " + s.ms + " ms: " + okTwo)
      handler.postDelayed({ trySlide(service, step + 1, run) }, s.ms + 1500L)
      return
    }
    if (s.focus) builder.addStroke(GestureDescription.StrokeDescription(dot(cx, cy), 0L, 40L))
    builder.addStroke(GestureDescription.StrokeDescription(dot(cx, cy), lead, 40L))
    builder.addStroke(GestureDescription.StrokeDescription(dragPath(cx, cy, tx, cy, s), lead + 140L, s.ms))
    val ok = service.dispatchGesture(builder.build(), null, null)
    NikaDiag.log("call", "viber: variant " + step + " from " + cx + "," + cy + " to " + tx + ": " + ok)
    handler.postDelayed({ trySlide(service, step + 1, run) }, lead + 140L + s.ms + 1500L)
  }

  private fun dot(x: Float, y: Float): Path {
    val p = Path()
    p.moveTo(x, y)
    return p
  }

  /** A drag with a hold: the hold is a movement to and fro, a standing finger would not take time. */
  private fun dragPath(x1: Float, y1: Float, x2: Float, y2: Float, s: Slide): Path {
    val steps = 24
    val moveMs = maxOf(s.ms - s.hold, 100L).toFloat()
    val dist = Math.sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).toDouble()).toFloat()
    val swings =
      if (s.hold > 0 && s.wiggle > 0f) Math.max(1, Math.ceil((dist * s.hold / moveMs / (2 * s.wiggle)).toDouble()).toInt()) else 0
    val p = Path()
    p.moveTo(x1, y1)
    if (swings > 0 && !s.holdEnd) {
      for (i in 1..swings) p.lineTo(x1, y1 + (if (i % 2 == 1) s.wiggle else -s.wiggle))
      p.lineTo(x1, y1)
    }
    for (i in 0..steps) {
      val t = i.toFloat() / steps
      p.lineTo(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t)
    }
    if (swings > 0 && s.holdEnd) {
      for (i in 1..swings) p.lineTo(x2, y2 + (if (i % 2 == 1) s.wiggle else -s.wiggle))
      p.lineTo(x2, y2)
    }
    return p
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
