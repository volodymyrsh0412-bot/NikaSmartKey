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

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.google.android.accessibility.talkback.TalkBackService

/** Nika: next and previous item on the keyboard go through the keys in reading order, row by row. */
object NikaKeyboardNav {
  @JvmStatic
  fun step(service: TalkBackService, forward: Boolean): Boolean {
    try {
      var keyboard: AccessibilityWindowInfo? = null
      for (w in service.windows) if (w.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) keyboard = w
      val root = keyboard?.root ?: return false
      val current = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return false
      val keys = ArrayList<AccessibilityNodeInfo>()
      val stack = ArrayDeque<AccessibilityNodeInfo>()
      stack.addLast(root)
      var n = 0
      while (stack.isNotEmpty() && n < 1500) {
        val node = stack.removeFirst()
        n++
        if (node.isVisibleToUser && node.childCount == 0 && (node.isClickable || node.isLongClickable)) {
          keys.add(node)
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { stack.addLast(it) }
      }
      if (keys.size < 2) return false
      val rect = Rect()
      val info = HashMap<AccessibilityNodeInfo, IntArray>()
      for (k in keys) {
        k.getBoundsInScreen(rect)
        info[k] = intArrayOf(rect.centerY(), rect.left, rect.height())
      }
      val sorted = keys.sortedBy { info[it]!![0] }
      // Rows: keys with nearly the same vertical centre are in one row.
      val ordered = ArrayList<AccessibilityNodeInfo>()
      var row = ArrayList<AccessibilityNodeInfo>()
      var rowY = Int.MIN_VALUE
      for (k in sorted) {
        val y = info[k]!![0]
        val h = info[k]!![2]
        if (rowY == Int.MIN_VALUE || y - rowY > h / 2) {
          ordered.addAll(row.sortedBy { info[it]!![1] })
          row = ArrayList()
          rowY = y
        }
        row.add(k)
      }
      ordered.addAll(row.sortedBy { info[it]!![1] })
      val cur = Rect()
      current.getBoundsInScreen(cur)
      var index = -1
      for (i in ordered.indices) {
        ordered[i].getBoundsInScreen(rect)
        if (rect == cur) {
          index = i
          break
        }
      }
      if (index < 0) return false
      val next = index + (if (forward) 1 else -1)
      if (next < 0 || next >= ordered.size) return false
      return ordered[next].performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
    } catch (e: RuntimeException) {
      return false
    }
  }
}
