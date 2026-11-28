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

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.NikaDiag
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/** Nika: one action starts the visual helper (Gemini Live with the camera) and the next one ends it. */
object NikaGeminiVisual {
  const val VALUE = "nika_gem_visual"
  private const val BARD = "com.google.android.apps.bard"
  private val PACKAGES = setOf(BARD, "com.google.android.googlequicksearchbox")
  private val LIVE = listOf("live", "лайв", "наживо")
  private val CAMERA = listOf("камер", "camera", "відео", "видео", "video")
  private val END = listOf("завершити", "закінчити", "зупинити", "завершить", "закончить", "end", "stop")
  private val handler = Handler(Looper.getMainLooper())
  private var run = 0

  @JvmStatic
  fun perform(service: TalkBackService): Boolean {
    run++
    val token = run
    try {
      val launch = service.packageManager.getLaunchIntentForPackage(BARD)
      if (launch == null) {
        say(service, R.string.nika_gv_missing)
        return true
      }
      val end = find(service, END)
      if (end != null) {
        end.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        say(service, R.string.nika_gv_ended)
        handler.postDelayed(
          { if (token == run) service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) },
          1500L,
        )
        return true
      }
      say(service, R.string.nika_gv_starting)
      launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      service.startActivity(launch)
      poll(service, token, 0, false)
    } catch (e: RuntimeException) {
      NikaDiag.log("gemini", "visual helper: " + e)
    }
    return true
  }

  private fun poll(service: TalkBackService, token: Int, n: Int, liveClicked: Boolean) {
    if (token != run) return
    try {
      val cam = find(service, CAMERA)
      if (cam != null) {
        cam.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        say(service, R.string.nika_gv_started)
        return
      }
      var clicked = liveClicked
      if (!clicked) {
        val live = find(service, LIVE)
        if (live != null) {
          live.performAction(AccessibilityNodeInfo.ACTION_CLICK)
          clicked = true
        }
      }
      if (n >= 30) {
        NikaDiag.log("gemini", "visual helper: buttons seen: " + labels(service))
        say(service, R.string.nika_gv_notfound)
        return
      }
      handler.postDelayed({ poll(service, token, n + 1, clicked) }, 700L)
    } catch (e: RuntimeException) {
      NikaDiag.log("gemini", "visual helper: " + e)
    }
  }

  private fun roots(service: TalkBackService): List<AccessibilityNodeInfo> {
    val result = ArrayList<AccessibilityNodeInfo>()
    for (w in service.windows) {
      val r = w.root ?: continue
      if (PACKAGES.contains(r.packageName?.toString())) result.add(r)
    }
    return result
  }

  private fun label(n: AccessibilityNodeInfo): String =
    ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim().lowercase()

  private fun walk(service: TalkBackService, visit: (AccessibilityNodeInfo) -> Boolean) {
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    for (r in roots(service)) stack.addLast(r)
    var n = 0
    while (stack.isNotEmpty() && n < 3000) {
      val node = stack.removeFirst()
      n++
      if (node.isVisibleToUser && visit(node)) return
      for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
    }
  }

  private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    var cur: AccessibilityNodeInfo? = node
    var up = 0
    while (cur != null && up < 5) {
      if (cur.isClickable) return cur
      cur = cur.parent
      up++
    }
    return null
  }

  private fun find(service: TalkBackService, words: List<String>): AccessibilityNodeInfo? {
    var found: AccessibilityNodeInfo? = null
    walk(service) { node ->
      val l = label(node)
      if (l.isNotEmpty() && words.any { w -> l == w || l.startsWith(w) || l.contains(" " + w) || (w.length > 3 && l.contains(w)) }) {
        found = clickable(node)
      }
      found != null
    }
    return found
  }

  private fun labels(service: TalkBackService): String {
    val list = ArrayList<String>()
    walk(service) { node ->
      val l = label(node)
      if (l.isNotEmpty() && node.isClickable && list.size < 40) list.add(l)
      false
    }
    return list.joinToString(" | ")
  }

  private fun say(service: TalkBackService, res: Int) {
    service.speechController.speak(
      service.getString(res),
      SpeechController.QUEUE_MODE_FLUSH_ALL,
      FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
      null,
      Performance.EVENT_ID_UNTRACKED,
    )
  }
}
