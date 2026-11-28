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

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.StringRes
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.macros.NikaVoiceCmds
import com.google.android.accessibility.talkback.TalkBackService

/** Nika: focus actions for gestures and keys. */
object NikaFocusActions {

  /** [value] is saved in the gesture preference, so it must never change once released. */
  class Action(val value: String, @StringRes val labelRes: Int)

  @JvmField
  val ACTIONS: List<Action> =
    listOf(
      Action("nika_focus_toggle", R.string.nika_act_focus_toggle),
      Action(NikaTapActions.TAP, R.string.nika_act_tap_finger),
      Action(NikaTapActions.SINGLE, R.string.nika_act_toggle_single_tap),
      Action(NikaTapActions.DIRECT, R.string.nika_act_toggle_direct_click),
      Action(NikaVoiceCmds.ACTION_LISTEN, R.string.nika_act_vc_listen),
      Action(NikaValueActions.SETTING_NEXT, R.string.nika_act_setting_next),
      Action(NikaValueActions.SETTING_PREV, R.string.nika_act_setting_prev),
      Action(NikaValueActions.VALUE_NEXT, R.string.nika_act_value_next),
      Action(NikaValueActions.VALUE_PREV, R.string.nika_act_value_prev),
      Action(NikaValueActions.SLIDER_UP, R.string.nika_act_slider_up),
      Action(NikaValueActions.SLIDER_DOWN, R.string.nika_act_slider_down),
      Action(NikaValueActions.CURSOR_RIGHT, R.string.nika_act_cursor_right),
      Action(NikaValueActions.CURSOR_LEFT, R.string.nika_act_cursor_left),
    )

  private fun find(value: String?): Action? = ACTIONS.firstOrNull { it.value == value }

  @JvmStatic fun isAction(value: String?): Boolean = find(value) != null

  @JvmStatic
  fun label(context: Context, value: String?): String? =
    find(value)?.let { context.getString(it.labelRes) }

  @JvmStatic
  fun perform(service: TalkBackService, value: String): Boolean {
    if (NikaValueActions.isAction(value)) return NikaValueActions.perform(service, value)
    if (value == NikaVoiceCmds.ACTION_LISTEN) {
      NikaVoiceCmds.listen(service)
      return true
    }
    if (NikaTapActions.isAction(value)) return NikaTapActions.perform(service, value)
    if (value != "nika_focus_toggle") return false
    try {
      toggle(service)
    } catch (e: RuntimeException) {
      // skip
    }
    return true
  }

  /**
   * If the screen reader focus is on the keyboard, it goes to the text field being edited. In any
   * other case it goes to the keyboard.
   */
  private fun toggle(service: TalkBackService) {
    holdToken++
    var keyboard: AccessibilityWindowInfo? = null
    var app: AccessibilityWindowInfo? = null
    for (w in service.windows) {
      if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) keyboard = w
      if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION && (app == null || w.isActive)) app = w
    }
    val keyboardRoot = keyboard?.root ?: return
    val appRoot = app?.root
    val onKeyboard =
      keyboardRoot.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) != null
    if (onKeyboard) {
      val field =
        appRoot?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
          ?: appRoot?.let { firstEditable(it) }
          ?: return
      field.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    } else {
      val prevFocus = appRoot?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
      val prevKey = prevFocus?.let { nodeKey(it) }
      val key = firstKey(keyboardRoot) ?: return
      key.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
      hold(service, prevKey) // nika-focus-hold
    }
  }

  private val holdHandler = android.os.Handler(android.os.Looper.getMainLooper())
  private var holdToken = 0

  private fun nodeKey(n: AccessibilityNodeInfo): String {
    val r = android.graphics.Rect()
    n.getBoundsInScreen(r)
    return "" + n.className + "|" + n.text + "|" + r.flattenToString()
  }

  /**
   * Some applications (Telegram) take the focus back to the old place about 3 seconds after it
   * moved to the keyboard. If it is the same old place, the focus goes to the keyboard again,
   * up to 3 times. Any other place means the user moved the focus, so it is left alone.
   */
  private fun hold(service: TalkBackService, prevKey: String?) {
    if (prevKey == null) return
    val token = ++holdToken
    val end = android.os.SystemClock.uptimeMillis() + 9000
    var left = 3
    val check =
      object : Runnable {
        override fun run() {
          if (token != holdToken) return
          try {
            var keyboard: AccessibilityWindowInfo? = null
            var app: AccessibilityWindowInfo? = null
            for (w in service.windows) {
              if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) keyboard = w
              if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION && (app == null || w.isActive)) app = w
            }
            val kRoot = keyboard?.root ?: return
            if (kRoot.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) == null) {
              val cur = app?.root?.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
              if (cur != null && nodeKey(cur) == prevKey && left > 0) {
                left--
                firstKey(kRoot)?.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
              } else if (cur != null) {
                return
              }
            }
          } catch (e: RuntimeException) {
            return
          }
          if (left > 0 && android.os.SystemClock.uptimeMillis() < end) {
            holdHandler.postDelayed(this, 600)
          }
        }
      }
    holdHandler.postDelayed(check, 2500)
  }

  private fun firstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.addLast(root)
    var n = 0
    while (stack.isNotEmpty() && n < 1500) {
      val node = stack.removeFirst()
      n++
      if (node.isEditable && node.isVisibleToUser) return node
      for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
    }
    return null
  }

  private fun firstKey(root: AccessibilityNodeInfo): AccessibilityNodeInfo? { // nika-first-letter
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.addLast(root)
    var n = 0
    var any: AccessibilityNodeInfo? = null
    var letter: AccessibilityNodeInfo? = null
    var letterPos = Long.MAX_VALUE
    val r = android.graphics.Rect()
    while (stack.isNotEmpty() && n < 1500) {
      val node = stack.removeFirst()
      n++
      if (node.isVisibleToUser && node.childCount == 0 &&
        (node.isClickable || node.isLongClickable) &&
        node.isImportantForAccessibility) {
        if (any == null) any = node
        val label = (node.text ?: node.contentDescription)?.toString() ?: ""
        if (label.length == 1 && label[0].isLetter()) {
          node.getBoundsInScreen(r)
          val pos = r.top.toLong() * 100000L + r.left
          if (pos < letterPos) {
            letterPos = pos
            letter = node
          }
        }
      }
      for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
    }
    return letter ?: any
  }
}
