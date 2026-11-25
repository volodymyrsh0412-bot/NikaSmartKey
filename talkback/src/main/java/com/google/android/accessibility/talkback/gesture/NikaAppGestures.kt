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

package com.google.android.accessibility.talkback.gesture

import android.accessibilityservice.AccessibilityService
import android.content.Context
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.utils.SharedPreferencesUtils

/**
 * Nika: gestures that work only in one application. For the application that is open, an action
 * saved here replaces the normal action of the gesture. "Not assigned" means the normal action.
 */
object NikaAppGestures {
  /** Settings placeholder name of every gesture and its system gesture id. */
  @JvmField
  val GESTURES: Map<String, Int> =
    linkedMapOf(
      "nika_appg_1_up" to AccessibilityService.GESTURE_SWIPE_UP,
      "nika_appg_1_down" to AccessibilityService.GESTURE_SWIPE_DOWN,
      "nika_appg_1_left" to AccessibilityService.GESTURE_SWIPE_LEFT,
      "nika_appg_1_right" to AccessibilityService.GESTURE_SWIPE_RIGHT,
      "nika_appg_2_up" to AccessibilityService.GESTURE_2_FINGER_SWIPE_UP,
      "nika_appg_2_down" to AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN,
      "nika_appg_2_left" to AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT,
      "nika_appg_2_right" to AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT,
      "nika_appg_3_up" to AccessibilityService.GESTURE_3_FINGER_SWIPE_UP,
      "nika_appg_3_down" to AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN,
      "nika_appg_3_left" to AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT,
      "nika_appg_3_right" to AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT,
      "nika_appg_2_dtap" to AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP,
      "nika_appg_3_tap" to AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP,
      "nika_appg_3_dtap" to AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP,
    )

  const val SELECTED_KEY = "nika_app_selected"

  @JvmStatic
  fun prefKey(pkg: String, gestureId: Int): String = "nika_app:$pkg:$gestureId"

  private fun openPackage(service: AccessibilityService): String? {
    service.rootInActiveWindow?.packageName?.toString()?.let { return it }
    for (w in service.windows) {
      if (w.isActive || w.isFocused) {
        w.root?.packageName?.toString()?.let { return it }
      }
    }
    return null
  }

  /** Returns the action for this gesture in the open application, or [action] if none is set. */
  @JvmStatic
  fun override(service: AccessibilityService, gestureId: Int, action: String): String {
    try {
      val pkg = openPackage(service) ?: return action
      val value =
        SharedPreferencesUtils.getSharedPreferences(service)
          .getString(prefKey(pkg, gestureId), null) ?: return action
      if (value.isEmpty() || value == service.getString(R.string.shortcut_value_unassigned)) {
        return action
      }
      return value
    } catch (e: RuntimeException) {
      return action
    }
  }
}
