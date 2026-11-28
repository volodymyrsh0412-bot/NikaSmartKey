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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.NikaDiag
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/**
 * Nika: a voice command that opens a person in Viber, Telegram or WhatsApp by a link, number or name,
 * and then presses the call button by its label. The labels that were seen are written to the diary
 * if the button is not found.
 */
object NikaMessengerCall {
  const val KIND_VIBER = "callviber"
  const val KIND_TELEGRAM = "calltelegram"
  const val KIND_WHATSAPP = "callwhatsapp"

  private val CALL_WORDS =
    listOf(
      "voice call", "audio call", "free call", "call", "голосовий виклик", "аудіодзвінок", "аудіовиклик",
      "безкоштовний дзвінок", "зателефонувати", "подзвонити", "дзвінок", "голосовой вызов", "позвонить", "звонок",
    )
  // Short labels that are only taken when they are the whole label (a message bubble says "Вихідні виклики").
  private val CALL_EXACT = setOf("виклик", "вызов")
  // The button of the question "Start an audio call?" (WhatsApp), found by its text.
  private val CONFIRM = setOf("дзвінок", "call", "звонок", "позвонить", "подзвонити", "зателефонувати")
  private val NOT_WORDS = listOf("video", "відео", "видео", "end", "заверш", "recent", "history", "історі")
  private val handler = Handler(Looper.getMainLooper())
  private var run = 0

  @JvmStatic
  fun isKind(kind: String?): Boolean = kind == KIND_VIBER || kind == KIND_TELEGRAM || kind == KIND_WHATSAPP

  private fun packageOf(kind: String): String =
    when (kind) {
      KIND_VIBER -> "com.viber.voip"
      KIND_TELEGRAM -> "org.telegram.messenger"
      else -> "com.whatsapp"
    }

  private fun digits(v: String): String = v.filter { it.isDigit() }

  private fun linkOf(kind: String, value: String): Uri? {
    val v = value.trim()
    if (v.isEmpty()) return null
    if (v.contains("://") || v.startsWith("http", ignoreCase = true)) return Uri.parse(v)
    if (v.startsWith("t.me/") || v.startsWith("wa.me/")) return Uri.parse("https://$v")
    return when (kind) {
      KIND_VIBER -> {
        val d = digits(v)
        if (d.isEmpty()) null else Uri.parse("viber://chat?number=" + Uri.encode("+$d"))
      }
      KIND_TELEGRAM -> {
        val d = digits(v)
        if (d.length >= 7 && v.none { it.isLetter() }) {
          Uri.parse("tg://resolve?phone=$d")
        } else {
          Uri.parse("tg://resolve?domain=" + Uri.encode(v.removePrefix("@")))
        }
      }
      else -> {
        val d = digits(v)
        if (d.isEmpty()) null else Uri.parse("https://wa.me/$d")
      }
    }
  }

  @JvmStatic
  fun run(service: TalkBackService, kind: String, value: String) {
    run++
    val token = run
    val uri = linkOf(kind, value)
    if (uri == null) {
      say(service, R.string.nika_vc_msg_bad)
      return
    }
    val pkg = packageOf(kind)
    try {
      service.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
      say(service, R.string.nika_vc_msg_noapp)
      return
    } catch (e: SecurityException) {
      say(service, R.string.nika_vc_msg_noapp)
      return
    }
    NikaDiag.log("messenger", "opened " + kind + " " + uri)
    handler.postDelayed({ poll(service, pkg, token, 0) }, 1500L)
  }

  private fun poll(service: TalkBackService, pkg: String, token: Int, n: Int) {
    if (token != run) return
    try {
      val nodes = nodes(service, pkg)
      for (node in nodes) {
        val l = label(node)
        if (l.isEmpty() || l.length > 40) continue
        // The call button is in the top bar. A message about a call in the chat is not it.
        val box = android.graphics.Rect()
        node.getBoundsInScreen(box)
        if (box.top > service.resources.displayMetrics.heightPixels * 22 / 100) continue
        if (NOT_WORDS.any { l.contains(it) }) continue
        if (CALL_WORDS.none { l.contains(it) } && !CALL_EXACT.contains(l)) continue
        val c = clickable(node) ?: continue
        if (c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
          NikaDiag.log("messenger", "call button: $l")
          say(service, R.string.nika_vc_msg_calling)
          handler.postDelayed({ confirm(service, pkg, token, 0) }, 800L)
          return
        }
      }
      if (n >= 14) {
        val seen = ArrayList<String>()
        for (node in nodes) {
          val l = label(node)
          if (l.isNotEmpty() && (node.isClickable || node.isCheckable) && seen.size < 40) seen.add(l)
        }
        NikaDiag.log("messenger", "no call button; buttons seen: " + seen.joinToString(" | "))
        say(service, R.string.nika_vc_msg_nobutton)
        return
      }
      handler.postDelayed({ poll(service, pkg, token, n + 1) }, 700L)
    } catch (e: RuntimeException) {
      NikaDiag.log("messenger", "failed: $e")
    }
  }

  /** WhatsApp asks "Start an audio call?": the button with the text "Дзвінок" is pressed. */
  private fun confirm(service: TalkBackService, pkg: String, token: Int, n: Int) {
    if (token != run) return
    try {
      for (node in nodes(service, pkg)) {
        val t = node.text?.toString()?.trim()?.lowercase() ?: continue
        if (!CONFIRM.contains(t) || !node.isClickable) continue
        if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
          NikaDiag.log("messenger", "confirmed: $t")
          return
        }
      }
      if (n < 4) handler.postDelayed({ confirm(service, pkg, token, n + 1) }, 500L)
    } catch (e: RuntimeException) {
      NikaDiag.log("messenger", "confirm failed: $e")
    }
  }

  private fun label(n: AccessibilityNodeInfo): String =
    ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).trim().lowercase()

  private fun nodes(service: TalkBackService, pkg: String): List<AccessibilityNodeInfo> {
    val list = ArrayList<AccessibilityNodeInfo>()
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    for (w in service.windows) {
      val r = w.root ?: continue
      if (r.packageName?.toString() == pkg) stack.addLast(r)
    }
    var count = 0
    while (stack.isNotEmpty() && count < 3000) {
      val node = stack.removeFirst()
      count++
      if (node.isVisibleToUser) list.add(node)
      for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
    }
    return list
  }

  private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    var cur: AccessibilityNodeInfo? = node
    var up = 0
    while (cur != null && up < 4) {
      if (cur.isClickable) return cur
      cur = cur.parent
      up++
    }
    return null
  }

  private fun say(service: TalkBackService, res: Int) {
    try {
      service.speechController.speak(
        service.getString(res),
        SpeechController.QUEUE_MODE_FLUSH_ALL,
        FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
        null,
        Performance.EVENT_ID_UNTRACKED,
      )
    } catch (e: RuntimeException) {
      // skip
    }
  }
}
