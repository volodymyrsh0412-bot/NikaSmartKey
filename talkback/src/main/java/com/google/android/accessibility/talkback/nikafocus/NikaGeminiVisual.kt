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

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.NikaDiag
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/**
 * Nika: the visual helper is Gemini Live with the camera. It lives in the Google app. The buttons are
 * found by their labels, as the diary of a real phone shows them: "Open Gemini Live", "Увімкнути або
 * вимкнути камеру" (a switch), "Завершити режим Live".
 */
object NikaGeminiVisual {
  const val VALUE = "nika_gem_visual"
  const val START = "nika_gem_visual_start"
  const val END_VALUE = "nika_gem_visual_end"
  private const val GOOGLE = "com.google.android.googlequicksearchbox"
  private const val GEMINI_ACTIVITY =
    "com.google.android.apps.search.assistant.surfaces.voice.robin.main.MainActivity"
  const val PREF_APP = "nika_gv_app"
  private val PACKAGES = setOf(GOOGLE, "com.google.android.apps.bard")

  /** The app that the person chose for the visual helper, or an empty text if Nika must find it. */
  @JvmStatic
  fun chosenApp(context: Context): String =
    SharedPreferencesUtils.getSharedPreferences(context).getString(PREF_APP, "") ?: ""
  // The words are tried in this order: the exact labels first, then the short words.
  private val LIVE = listOf("open gemini live", "відкрити gemini live", "открыть gemini live", "gemini live", "live", "лайв")
  private val CAMERA =
    listOf(
      "увімкнути або вимкнути камеру",
      "включить или выключить камеру",
      "turn camera on or off",
      "toggle camera",
      "camera",
      "камер",
    )
  private val END =
    listOf("завершити режим live", "завершити режим", "завершить режим", "end live", "завершити", "end")
  private val handler = Handler(Looper.getMainLooper())
  private var run = 0

  @JvmStatic
  fun isAction(value: String?): Boolean = value == VALUE || value == START || value == END_VALUE

  @JvmStatic
  fun perform(service: TalkBackService, value: String): Boolean {
    run++
    val token = run
    try {
      val end = find(service, END)
      when (value) {
        END_VALUE -> if (end != null) finish(service, end, token) else say(service, R.string.nika_gv_notrunning)
        START -> if (end != null) say(service, R.string.nika_gv_already) else begin(service, token)
        else -> if (end != null) finish(service, end, token) else begin(service, token)
      }
    } catch (e: RuntimeException) {
      NikaDiag.log("gemini", "visual helper: " + e)
    }
    return true
  }

  private fun finish(service: TalkBackService, end: AccessibilityNodeInfo, token: Int) {
    press(service, end)
    say(service, R.string.nika_gv_ended)
    handler.postDelayed(
      { if (token == run) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) },
      1500L,
    )
  }

  private var launched = ""
  private var backed = false

  /** The app with the name Gemini among the apps of the launcher. */
  private fun findGemini(service: TalkBackService): Intent? =
    try {
      val pm = service.packageManager
      val hit =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
          .firstOrNull { it.loadLabel(pm).toString().trim().equals("Gemini", ignoreCase = true) }
      if (hit == null) {
        null
      } else {
        launched = hit.activityInfo.packageName
        Intent(Intent.ACTION_MAIN)
          .setClassName(hit.activityInfo.packageName, hit.activityInfo.name)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    } catch (e: RuntimeException) {
      null
    }

  private fun begin(service: TalkBackService, token: Int) {
    val chosen = chosenApp(service)
    launched = chosen
    val intent =
      if (chosen.isNotEmpty()) {
        service.packageManager.getLaunchIntentForPackage(chosen)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      } else {
        findGemini(service)
          ?: Intent(Intent.ACTION_MAIN).setClassName(GOOGLE, GEMINI_ACTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    if (intent == null) {
      say(service, R.string.nika_gv_missing)
      return
    }
    try {
      service.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      say(service, R.string.nika_gv_missing)
      return
    } catch (e: SecurityException) {
      say(service, R.string.nika_gv_missing)
      return
    }
    backed = false
    say(service, R.string.nika_gv_starting)
    poll(service, token, 0, false)
  }

  private fun poll(service: TalkBackService, token: Int, n: Int, liveClicked: Boolean) {
    if (token != run) return
    try {
      val cam = find(service, CAMERA)
      if (cam != null) {
        // The camera button is a switch: a click on a switch that is already on would turn it off.
        if (!isOn(cam)) press(service, cam)
        say(service, R.string.nika_gv_started)
        return
      }
      var clicked = liveClicked
      if (!clicked && find(service, END) == null) {
        val live = find(service, LIVE)
        if (live != null) {
          press(service, live)
          clicked = true
        } else if (n >= 2 && !backed && keyboardOpen(service)) {
          // The keyboard of the Gemini text field covers the buttons: close it once.
          backed = true
          service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
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

  private fun isOn(node: AccessibilityNodeInfo): Boolean {
    if (node.isChecked) return true
    val state = node.stateDescription?.toString()?.lowercase() ?: return false
    return state == "увімкнено" || state == "включено" || state == "on" || state == "enabled"
  }

  private fun roots(service: TalkBackService): List<AccessibilityNodeInfo> {
    val result = ArrayList<AccessibilityNodeInfo>()
    val chosen = chosenApp(service)
    for (w in service.windows) {
      val r = w.root ?: continue
      val pkg = r.packageName?.toString()
      if (PACKAGES.contains(pkg) || (chosen.isNotEmpty() && pkg == chosen) || (launched.isNotEmpty() && pkg == launched)) result.add(r)
    }
    return result
  }

  private fun label(n: AccessibilityNodeInfo): String =
    ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim().lowercase()

  private fun all(service: TalkBackService): List<AccessibilityNodeInfo> {
    val list = ArrayList<AccessibilityNodeInfo>()
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    for (r in roots(service)) stack.addLast(r)
    var n = 0
    while (stack.isNotEmpty() && n < 3000) {
      val node = stack.removeFirst()
      n++
      if (node.isVisibleToUser) list.add(node)
      for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
    }
    return list
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

  /** The words are tried one after another, so the exact label wins over a short word. */
  private fun find(service: TalkBackService, words: List<String>): AccessibilityNodeInfo? {
    val nodes = all(service)
    for (w in words) {
      var plain: AccessibilityNodeInfo? = null
      for (node in nodes) {
        val l = label(node)
        if (l.isEmpty()) continue
        val hit = if (w.length <= 4) (l == w || l.startsWith("$w ") || l.endsWith(" $w") || l.contains(" $w ")) else l.contains(w)
        if (hit) {
          val c = clickable(node)
          if (c != null) return c
          if (plain == null) plain = node
        }
      }
      // A label without a clickable parent is pressed with a tap on its place.
      if (plain != null) return plain
    }
    return null
  }

  private fun keyboardOpen(service: TalkBackService): Boolean =
    try {
      service.windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    } catch (e: RuntimeException) {
      false
    }

  /** A click, or a tap in the middle of the node when it cannot be clicked. */
  private fun press(service: TalkBackService, node: AccessibilityNodeInfo) {
    if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
    try {
      val r = Rect()
      node.getBoundsInScreen(r)
      if (r.isEmpty) return
      val path = Path()
      path.moveTo(r.exactCenterX(), r.exactCenterY())
      val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, 60L)).build()
      service.dispatchGesture(g, null, null)
    } catch (e: RuntimeException) {
      NikaDiag.log("gemini", "tap failed: " + e)
    }
  }

  private fun labels(service: TalkBackService): String {
    val list = ArrayList<String>()
    for (node in all(service)) {
      val l = label(node)
      if (l.isNotEmpty() && list.size < 60) {
        list.add(l + " [" + (node.className?.toString()?.substringAfterLast('.') ?: "") + (if (node.isClickable) ",click" else "") + "]")
      }
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
