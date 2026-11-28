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

/**
 * Nika: a line of the list of changes has the texts in three languages, divided by a tab: Ukrainian,
 * Russian, English. The text of the language of the phone is shown. A line without tabs is shown as it is.
 */
object NikaChangeLine {
  // Old lines that still use the old names of the app are hidden.
  private val OLD_NAME = Regex("Нік(?![а-яіїєґ])|Ник(?![а-я])")

  /** The text of the language of the phone only; an empty string when there is no text in that language. */
  @JvmStatic
  fun pick(context: Context, line: String): String {
    if (!line.contains("\t")) return line.trim()
    val p = line.split("\t")
    val uk = p.getOrElse(0) { "" }.trim()
    val ru = p.getOrElse(1) { "" }.trim()
    val en = p.getOrElse(2) { "" }.trim()
    val lang =
      try {
        context.resources.configuration.locales.get(0).language
      } catch (e: RuntimeException) {
        "en"
      }
    val text =
      when (lang) {
        "uk", "be" -> uk
        "ru" -> ru
        else -> en
      }
    return if (OLD_NAME.containsMatchIn(text)) "" else text
  }
}
