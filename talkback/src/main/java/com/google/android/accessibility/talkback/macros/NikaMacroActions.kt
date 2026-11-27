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

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.StringRes
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/** Nika: runs macros and records buttons into them. Used as actions of gestures and keys. */
object NikaMacroActions {
  private const val PREFIX = "nika_macro:"
  const val REC_CLICK = "nika_macro_rec_click"
  const val REC_STOP = "nika_macro_rec_stop"
  const val REC_HOLD = "nika_macro_rec_hold"
  const val HELP = "nika_help_mode"

  class Fixed(val value: String, @StringRes val labelRes: Int)

  @JvmField
  val FIXED: List<Fixed> =
    listOf(
      Fixed(REC_CLICK, R.string.nika_act_mac_rec),
      Fixed(REC_HOLD, R.string.nika_act_mac_rec_hold),
      Fixed(REC_STOP, R.string.nika_act_mac_stop),
      Fixed(HELP, R.string.nika_act_help),
    )

  @JvmField
  val CALLS: List<Fixed> = listOf(
      Fixed(NikaCallActions.DECLINE, R.string.nika_act_call_decline),
      Fixed(NikaCallActions.QUICK, R.string.nika_act_call_quick),
    )

  private val handler = Handler(Looper.getMainLooper())
  private var runToken = 0

  // The finger that is being held down by a "hold" step, until a "release" step lifts it.
  private var heldStroke: GestureDescription.StrokeDescription? = null
  private var heldX = 0f
  private var heldY = 0f

  @JvmStatic fun actionOf(id: String): String = PREFIX + id

  @JvmStatic
  fun isAction(value: String?): Boolean =
    value != null && (value.startsWith(PREFIX) || (FIXED + CALLS).any { it.value == value } || NikaVoiceCmds.isCmdAction(value))

  @JvmStatic
  fun label(context: Context, value: String?): String? {
    if (value == null) return null
    NikaVoiceCmds.cmdLabel(context, value)?.let { return it }
    (FIXED + CALLS).firstOrNull { it.value == value }?.let { return context.getString(it.labelRes) }
    if (!value.startsWith(PREFIX)) return null
    val macro = NikaMacros.find(context, value.removePrefix(PREFIX))
    return context.getString(R.string.nika_mac_prefix, macro?.name ?: "?")
  }

  @JvmStatic
  fun perform(service: TalkBackService, value: String): Boolean {
    try {
      when {
        value == REC_CLICK -> recordFocused(service, NikaMacros.CLICK)
        value == REC_HOLD -> recordFocused(service, NikaMacros.HOLD)
        value == REC_STOP -> {
          NikaMacros.setRecording(service, "")
          say(service, service.getString(R.string.nika_mac_rec_stopped))
        }
        value == HELP -> NikaHelpMode.toggle(service)
        value == NikaCallActions.ACTION -> NikaCallActions.perform(service)
        value == NikaCallActions.DECLINE || value == NikaCallActions.QUICK -> NikaCallActions.run(service, value)
        NikaVoiceCmds.isCmdAction(value) -> NikaVoiceCmds.performCmd(service, value)
        value.startsWith(PREFIX) -> start(service, value.removePrefix(PREFIX))
        else -> return false
      }
    } catch (e: RuntimeException) {
      // a macro never breaks the screen reader
    }
    return true
  }

  // ---------------------------------------------------------------- recording

  private fun recordFocused(service: TalkBackService, type: String) {
    val id = NikaMacros.recordingId(service)
    if (id.isEmpty() || NikaMacros.find(service, id) == null) {
      say(service, service.getString(R.string.nika_mac_rec_off))
      return
    }
    var focused: AccessibilityNodeInfo? = null
    for (w in service.windows) {
      focused = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
      if (focused != null) break
    }
    if (focused == null) {
      say(service, service.getString(R.string.nika_mac_rec_nofocus))
      return
    }
    val label = labelOf(focused)
    NikaMacros.addStep(
      service,
      id,
      NikaMacros.Step(type, focused.viewIdResourceName ?: "", label, 0),
    )
    say(service, service.getString(R.string.nika_mac_recorded, label.ifEmpty { focused.viewIdResourceName ?: "" }))
  }

  // ---------------------------------------------------------------- running

  private fun start(service: TalkBackService, id: String) {
    val macro = NikaMacros.find(service, id) ?: return
    // "Run only when": every condition must hold, otherwise the macro does not start.
    for (c in macro.conds) {
      if (!condition(service, c.a, c.b)) {
        say(service, service.getString(R.string.nika_mac_cond_failed))
        return
      }
    }
    runToken += 1
    val token = runToken
    runStep(service, Run(macro.steps.toList()), 0, token, 0)
  }

  /** Runs a macro that the person started from its settings screen. */
  @JvmStatic
  fun runNow(id: String) {
    val service = TalkBackService.getInstance() ?: return
    start(service, id)
  }

  /** The state of one run: the loops that are open and how many steps were done. */
  private class Run(val steps: List<NikaMacros.Step>) {
    class Loop(val start: Int, val end: Int, var left: Int, val isWhile: Boolean)

    val loops = ArrayList<Loop>()
    var done = 0
  }

  private const val MAX_STEPS = 3000
  /** Index of the step that closes the block opened at [from], or -1. */
  private fun blockEnd(steps: List<NikaMacros.Step>, from: Int): Int {
    var depth = 0
    for (i in from until steps.size) {
      when (steps[i].type) {
        NikaMacros.IF, NikaMacros.REPEAT, NikaMacros.REPEAT_WHILE -> depth++
        NikaMacros.END_IF, NikaMacros.END_REPEAT -> {
          depth--
          if (depth == 0) return i
        }
      }
    }
    return -1
  }

  /** The next ELSE_IF, ELSE or END_IF at the same level after the IF at [from]. */
  private fun nextBranch(steps: List<NikaMacros.Step>, from: Int): Int {
    var depth = 0
    for (i in from + 1 until steps.size) {
      when (steps[i].type) {
        NikaMacros.IF, NikaMacros.REPEAT, NikaMacros.REPEAT_WHILE -> depth++
        NikaMacros.END_REPEAT -> depth--
        NikaMacros.END_IF -> {
          if (depth == 0) return i
          depth--
        }
        NikaMacros.ELSE_IF, NikaMacros.ELSE -> if (depth == 0) return i
      }
    }
    return -1
  }

  private fun condition(service: AccessibilityService, kind: String, value: String): Boolean {
    val word = value.lowercase()
    return when (kind) {
      NikaMacros.COND_SEE, NikaMacros.COND_NOT_SEE -> {
        val seen = word.isNotEmpty() && collect(service).any { it.isVisibleToUser && labelOf(it).contains(word) }
        if (kind == NikaMacros.COND_SEE) seen else !seen
      }
      NikaMacros.COND_APP, NikaMacros.COND_NOT_APP -> {
        val pkg = service.rootInActiveWindow?.packageName?.toString() ?: ""
        if (kind == NikaMacros.COND_APP) pkg == value else pkg != value
      }
      else -> false
    }
  }

  private fun runStep(
    service: TalkBackService,
    run: Run,
    index: Int,
    token: Int,
    tries: Int,
    jumped: Boolean = false,
  ) {
    val steps = run.steps
    if (token != runToken || index < 0 || index >= steps.size) return
    if (++run.done > MAX_STEPS) {
      say(service, service.getString(R.string.nika_mac_too_long))
      return
    }
    val step = steps[index]
    fun goto(target: Int, delay: Long = 0L, jump: Boolean = false) {
      handler.postDelayed({ runStep(service, run, target, token, 0, jump) }, delay)
    }
    fun next(delay: Long = 400L) = goto(index + 1, delay)
    try {
      when (step.type) {
        NikaMacros.IF, NikaMacros.ELSE_IF -> {
          if (step.type == NikaMacros.ELSE_IF && !jumped) {
            // reached by running through the branch before it: skip the rest of the chain
            goto(chainEnd(steps, index))
          } else if (condition(service, step.a, step.b)) {
            next(0L)
          } else {
            val to = nextBranch(steps, index)
            if (to < 0) return
            if (steps[to].type == NikaMacros.ELSE_IF) goto(to, 0L, true) else goto(to + 1)
          }
        }
        NikaMacros.ELSE -> goto(chainEnd(steps, index))
        NikaMacros.END_IF -> next(0L)
        NikaMacros.REPEAT, NikaMacros.REPEAT_WHILE -> {
          val end = blockEnd(steps, index)
          if (end < 0) return
          val isWhile = step.type == NikaMacros.REPEAT_WHILE
          val loop = run.loops.lastOrNull()?.takeIf { it.start == index }
          if (loop == null) {
            val count = if (isWhile) 0 else step.n.coerceIn(0, 1000)
            if ((isWhile && !condition(service, step.a, step.b)) || (!isWhile && count <= 0)) {
              goto(end + 1)
            } else {
              run.loops.add(Run.Loop(index, end, count - 1, isWhile))
              next(0L)
            }
          } else {
            // back at the top of a while loop
            if (condition(service, step.a, step.b)) {
              next(0L)
            } else {
              run.loops.removeAt(run.loops.size - 1)
              goto(end + 1)
            }
          }
        }
        NikaMacros.END_REPEAT -> {
          val loop = run.loops.lastOrNull { it.end == index }
          if (loop == null) {
            next(0L)
          } else if (loop.isWhile) {
            goto(loop.start, 300L)
          } else if (loop.left > 0) {
            loop.left -= 1
            goto(loop.start + 1, 300L)
          } else {
            run.loops.remove(loop)
            next(0L)
          }
        }
        NikaMacros.BREAK -> {
          val loop = run.loops.lastOrNull()
          if (loop == null) {
            next(0L)
          } else {
            run.loops.remove(loop)
            goto(loop.end + 1)
          }
        }
        NikaMacros.STOP -> return
        NikaMacros.WAIT_UNTIL -> {
          val limit = step.n.coerceIn(1, 120) * 2
          if (condition(service, step.a, step.b)) {
            next(300L)
          } else if (tries < limit) {
            handler.postDelayed({ runStep(service, run, index, token, tries + 1) }, 500L)
          } else {
            say(service, service.getString(R.string.nika_mac_wait_timeout))
          }
        }
        NikaMacros.WAIT -> next(step.n.coerceIn(0, 60) * 1000L)
        NikaMacros.SAY -> {
          say(service, step.a)
          next(900L)
        }
        NikaMacros.TYPE -> {
          typeText(service, step.a)
          next()
        }
        NikaMacros.APP -> {
          val intent = service.packageManager.getLaunchIntentForPackage(step.a)
          if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
          }
          next(1500L)
        }
        NikaMacros.GLOBAL -> {
          service.performGlobalAction(step.n)
          next(700L)
        }
        NikaMacros.HOLD -> {
          if (holdStep(service, step)) {
            next(600L)
          } else if (tries < 8) {
            handler.postDelayed({ runStep(service, run, index, token, tries + 1) }, 500L)
          } else {
            say(service, service.getString(R.string.nika_mac_not_found))
          }
        }
        NikaMacros.TAP_POS -> {
          tapPosition(service, step)
          next(600L)
        }
        NikaMacros.SELECT, NikaMacros.SAY_OBJECT -> {
          if (selectStep(service, step)) {
            next(if (step.type == NikaMacros.SAY_OBJECT) 900L else 400L)
          } else if (tries < 8) {
            handler.postDelayed({ runStep(service, run, index, token, tries + 1) }, 500L)
          } else {
            say(service, service.getString(R.string.nika_mac_not_found))
          }
        }
        NikaMacros.RELEASE -> {
          releaseStep(service, step)
          next()
        }
        NikaMacros.CLICK, NikaMacros.CLICK_TEXT -> {
          if (clickStep(service, step)) {
            next()
          } else if (tries < 8) {
            // the screen may still be opening: try again
            handler.postDelayed({ runStep(service, run, index, token, tries + 1) }, 500L)
          } else {
            say(service, service.getString(R.string.nika_mac_not_found))
          }
        }
        else -> next(0L)
      }
    } catch (e: RuntimeException) {
      say(service, service.getString(R.string.nika_mac_not_found))
    }
  }

  /** The END_IF that closes the chain of IF, ELSE_IF and ELSE that contains the step at [index]. */
  private fun chainEnd(steps: List<NikaMacros.Step>, index: Int): Int {
    var at = index
    while (at in steps.indices && steps[at].type != NikaMacros.END_IF) {
      val n = nextBranch(steps, at)
      if (n < 0) return steps.size
      at = n
    }
    return at
  }

  private fun labelOf(node: AccessibilityNodeInfo): String =
    ((node.contentDescription ?: node.text ?: node.hintText ?: "").toString()).lowercase()

  private fun collect(service: AccessibilityService): List<AccessibilityNodeInfo> {
    val out = ArrayList<AccessibilityNodeInfo>()
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    for (w in service.windows) w.root?.let { stack.addLast(it) }
    var n = 0
    while (stack.isNotEmpty() && n < 3000) {
      val node = stack.removeFirst()
      n++
      out.add(node)
      for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
    }
    return out
  }

  private fun clickStep(service: AccessibilityService, step: NikaMacros.Step): Boolean {
    val all = collect(service)
    val found: AccessibilityNodeInfo? =
      if (step.type == NikaMacros.CLICK_TEXT) {
        val word = step.a.lowercase()
        all.firstOrNull { it.isVisibleToUser && word.isNotEmpty() && labelOf(it).contains(word) }
      } else {
        val id = step.a
        val name = step.b
        all.firstOrNull { it.isVisibleToUser && id.isNotEmpty() && it.viewIdResourceName == id && (name.isEmpty() || labelOf(it) == name) }
          ?: all.firstOrNull { it.isVisibleToUser && id.isEmpty() && name.isNotEmpty() && labelOf(it) == name }
          ?: all.firstOrNull { it.isVisibleToUser && id.isNotEmpty() && it.viewIdResourceName == id }
      }
    if (found == null) return false
    var n: AccessibilityNodeInfo? = found
    var depth = 0
    while (n != null && depth < 6) {
      if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
      n = n.parent
      depth++
    }
    return false
  }

  private fun findNode(all: List<AccessibilityNodeInfo>, step: NikaMacros.Step, byText: Boolean): AccessibilityNodeInfo? =
    if (byText) {
      val word = step.a.lowercase()
      all.firstOrNull { it.isVisibleToUser && word.isNotEmpty() && labelOf(it).contains(word) }
    } else {
      val id = step.a
      val name = step.b
      all.firstOrNull { it.isVisibleToUser && id.isNotEmpty() && it.viewIdResourceName == id && (name.isEmpty() || labelOf(it) == name) }
        ?: all.firstOrNull { it.isVisibleToUser && id.isEmpty() && name.isNotEmpty() && labelOf(it) == name }
        ?: all.firstOrNull { it.isVisibleToUser && id.isNotEmpty() && it.viewIdResourceName == id }
    }

  /** Puts a finger on the button and keeps it there. A "release" step lifts it. */
  private fun holdStep(service: AccessibilityService, step: NikaMacros.Step): Boolean {
    // A hold step that has a recorded button has an id or a label in b; a hold step by name has
    // only the name in a and no b.
    val byText = step.b.isEmpty() && step.a.isNotEmpty() && !step.a.contains(":id/")
    val node = findNode(collect(service), step, byText) ?: return false
    val r = Rect()
    node.getBoundsInScreen(r)
    val x = r.exactCenterX()
    val y = r.exactCenterY()
    val path = Path()
    path.moveTo(x, y)
    val stroke = GestureDescription.StrokeDescription(path, 0L, 400L, true)
    heldStroke = stroke
    heldX = x
    heldY = y
    return service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
  }

  /** Lifts the held finger, after moving it [Step.n] millimetres in the direction [Step.a]. */
  private fun releaseStep(service: TalkBackService, step: NikaMacros.Step) {
    val prev = heldStroke
    if (prev == null) {
      say(service, service.getString(R.string.nika_mac_no_held))
      return
    }
    heldStroke = null
    val m = service.resources.displayMetrics
    val mm = step.n.coerceIn(0, 40)
    val xdpi = if (m.xdpi > 0f) m.xdpi else m.densityDpi.toFloat()
    val ydpi = if (m.ydpi > 0f) m.ydpi else m.densityDpi.toFloat()
    var dx = 0f
    var dy = 0f
    when (step.a) {
      "up" -> dy = -mm * ydpi / 25.4f
      "down" -> dy = mm * ydpi / 25.4f
      "left" -> dx = -mm * xdpi / 25.4f
      "right" -> dx = mm * xdpi / 25.4f
    }
    val endX = (heldX + dx).coerceIn(1f, (m.widthPixels - 1).toFloat())
    val endY = (heldY + dy).coerceIn(1f, (m.heightPixels - 1).toFloat())
    val path = Path()
    path.moveTo(heldX, heldY)
    if (endX != heldX || endY != heldY) path.lineTo(endX, endY)
    val duration = (mm * 15L).coerceAtLeast(100L)
    val stroke = prev.continueStroke(path, 0L, duration, false)
    service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
  }

  /** Taps the place of the screen given in percents: a = across (0..100), b = down (0..100). */
  private fun tapPosition(service: AccessibilityService, step: NikaMacros.Step) {
    val m = service.resources.displayMetrics
    val x = (step.a.toFloatOrNull() ?: 50f).coerceIn(0f, 100f) / 100f * (m.widthPixels - 1)
    val y = (step.b.toFloatOrNull() ?: 50f).coerceIn(0f, 100f) / 100f * (m.heightPixels - 1)
    val path = Path()
    path.moveTo(x, y)
    val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
    service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
  }

  /**
   * Puts the screen reader's focus on the object that has the words in a (or on the one that is
   * in focus now if a is empty). For "say object" it also speaks the object.
   */
  private fun selectStep(service: TalkBackService, step: NikaMacros.Step): Boolean {
    val word = step.a.lowercase()
    var node: AccessibilityNodeInfo? = null
    if (word.isEmpty()) {
      for (w in service.windows) {
        node = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        if (node != null) break
      }
    } else {
      node = collect(service).firstOrNull { it.isVisibleToUser && labelOf(it).contains(word) }
    }
    if (node == null) return false
    if (step.type == NikaMacros.SELECT) {
      return node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    }
    node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    val text = (node.contentDescription ?: node.text ?: "").toString()
    if (text.isNotEmpty()) say(service, text)
    return true
  }

  private fun typeText(service: AccessibilityService, text: String) {
    for (w in service.windows) {
      val field = w.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: continue
      val old = field.text?.toString() ?: ""
      val args = Bundle()
      args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, old + text)
      field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
      return
    }
  }

  private fun say(service: TalkBackService, text: String) {
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
}
