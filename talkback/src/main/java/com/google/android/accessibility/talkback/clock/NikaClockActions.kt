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

package com.google.android.accessibility.talkback.clock

import android.content.Context
import androidx.annotation.StringRes
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService

/** Nika: the clock, stopwatch and timer actions that can be assigned to gestures and keys. */
object NikaClockActions {

  /** [value] is saved in the gesture preference, so it must never change once released. */
  class Action(val value: String, @StringRes val labelRes: Int)

  @JvmField
  val ACTIONS: List<Action> =
    listOf(
      Action("nika_clock_say", R.string.nika_act_clock_say),
      Action("nika_sw_toggle", R.string.nika_act_sw_toggle),
      Action("nika_sw_say", R.string.nika_act_sw_say),
      Action("nika_sw_reset", R.string.nika_act_sw_reset),
      Action("nika_timer_toggle", R.string.nika_act_timer_toggle),
      Action("nika_timer_say", R.string.nika_act_timer_say),
    )

  private fun find(value: String?): Action? = ACTIONS.firstOrNull { it.value == value }

  @JvmStatic fun isAction(value: String?): Boolean = find(value) != null

  @JvmStatic
  fun label(context: Context, value: String?): String? =
    find(value)?.let { context.getString(it.labelRes) }

  /** Runs the action. Returns false when it is not one of these. */
  @JvmStatic
  fun perform(service: TalkBackService, value: String): Boolean {
    val clock = NikaClock.get(service)
    clock.start()
    when (value) {
      "nika_clock_say" -> clock.sayTime()
      "nika_sw_toggle" -> clock.stopwatchToggle()
      "nika_sw_say" -> clock.stopwatchSay()
      "nika_sw_reset" -> clock.stopwatchReset()
      "nika_timer_toggle" -> clock.timerToggle()
      "nika_timer_say" -> clock.timerSay()
      else -> return false
    }
    return true
  }
}
