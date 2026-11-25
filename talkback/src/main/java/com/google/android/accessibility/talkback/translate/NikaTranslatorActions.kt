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


package com.google.android.accessibility.talkback.translate

import android.content.Context
import androidx.annotation.StringRes
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService

/** Nika: the translator actions that can be assigned to gestures and keys. */
object NikaTranslatorActions {

  /** [value] is saved in the gesture preference, so it must never change once released. */
  class Action(val value: String, @StringRes val labelRes: Int)

  @JvmField
  val ACTIONS: List<Action> =
    listOf(
      Action("nika_tr_last", R.string.nika_act_tr_last),
      Action("nika_tr_focus", R.string.nika_act_tr_focus),
      Action("nika_tr_clipboard", R.string.nika_act_tr_clipboard),
      Action("nika_tr_screen", R.string.nika_act_tr_screen),
    )

  private fun find(value: String?): Action? = ACTIONS.firstOrNull { it.value == value }

  @JvmStatic fun isAction(value: String?): Boolean = find(value) != null

  @JvmStatic
  fun label(context: Context, value: String?): String? =
    find(value)?.let { context.getString(it.labelRes) }

  @JvmStatic
  fun perform(service: TalkBackService, value: String): Boolean {
    val translator = NikaTranslator.get(service)
    when (value) {
      "nika_tr_last" -> translator.translateLastSpoken()
      "nika_tr_focus" -> translator.translateFocus()
      "nika_tr_clipboard" -> translator.translateClipboard()
      "nika_tr_screen" -> translator.translateScreen()
      else -> return false
    }
    return true
  }
}
