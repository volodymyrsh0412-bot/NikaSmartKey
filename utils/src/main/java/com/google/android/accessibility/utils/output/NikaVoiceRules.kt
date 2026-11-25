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
package com.google.android.accessibility.utils.output

import android.content.Context
import com.google.android.accessibility.utils.SharedPreferencesUtils
import java.util.Locale

/**
 * Nika: voices chosen by language or by alphabet. Each rule says "text in this language (or in
 * this alphabet) is spoken with this voice". A rule may also name the speech engine of the voice
 * and its own speed and volume (percent). Rules are kept as lines
 * "kind TAB value TAB voice TAB engine TAB speed TAB volume" (the last three are optional).
 */
object NikaVoiceRules {
  const val PREF = "nika_voice_rules"
  const val PREF_DETECT = "nika_voice_detect"
  const val DETECT_ALPHABET = "alphabet"
  const val DETECT_UNICODE = "unicode"
  const val KIND_LANG = "lang"
  const val KIND_SCRIPT = "script"
  private const val MAX_RUNS = 40

  data class Rule(
    val kind: String,
    val value: String,
    val voice: String,
    val engine: String = "",
    val rate: Int = 100,
    val volume: Int = 100,
  )

  @Volatile private var cachedRaw: String? = null
  @Volatile private var cachedRules: List<Rule> = emptyList()

  @JvmStatic
  fun load(context: Context): List<Rule> {
    val raw = SharedPreferencesUtils.getSharedPreferences(context).getString(PREF, "") ?: ""
    if (raw == cachedRaw) return cachedRules
    val rules = ArrayList<Rule>()
    for (line in raw.split("\n")) {
      val p = line.split("\t")
      if (p.size >= 3 && p[0].isNotEmpty() && p[1].isNotEmpty() && p[2].isNotEmpty()) {
        rules.add(
          Rule(
            p[0],
            p[1],
            p[2],
            p.getOrElse(3) { "" },
            (p.getOrNull(4)?.toIntOrNull() ?: 100).coerceIn(30, 300),
            (p.getOrNull(5)?.toIntOrNull() ?: 100).coerceIn(0, 100),
          )
        )
      }
    }
    cachedRules = rules
    cachedRaw = raw
    return rules
  }

  @JvmStatic
  fun save(context: Context, rules: List<Rule>) {
    val raw =
      rules.joinToString("\n") {
        listOf(it.kind, it.value, it.voice, it.engine, it.rate.toString(), it.volume.toString())
          .joinToString("\t")
      }
    SharedPreferencesUtils.getSharedPreferences(context).edit().putString(PREF, raw).apply()
  }

  @JvmStatic
  fun detectMode(context: Context): String =
    SharedPreferencesUtils.getSharedPreferences(context).getString(PREF_DETECT, DETECT_ALPHABET)
      ?: DETECT_ALPHABET

  @JvmStatic
  fun setDetectMode(context: Context, mode: String) {
    SharedPreferencesUtils.getSharedPreferences(context).edit().putString(PREF_DETECT, mode).apply()
  }

  /** The name of the alphabet of a letter, or null for letters of other alphabets. */
  private fun scriptOfChar(cp: Int): String? =
    when (Character.UnicodeScript.of(cp)) {
      Character.UnicodeScript.CYRILLIC -> "cyrillic"
      Character.UnicodeScript.LATIN -> "latin"
      Character.UnicodeScript.GREEK -> "greek"
      Character.UnicodeScript.ARABIC -> "arabic"
      Character.UnicodeScript.HEBREW -> "hebrew"
      Character.UnicodeScript.HAN,
      Character.UnicodeScript.HIRAGANA,
      Character.UnicodeScript.KATAKANA -> "cjk"
      else -> null
    }

  /** The alphabet most of the letters of [text] belong to: "cyrillic", "latin", ... or null. */
  @JvmStatic
  fun scriptOf(text: CharSequence?): String? {
    if (text.isNullOrEmpty()) return null
    val counts = HashMap<String, Int>()
    var i = 0
    val limit = minOf(text.length, 300)
    while (i < limit) {
      val cp = Character.codePointAt(text, i)
      i += Character.charCount(cp)
      if (!Character.isLetter(cp)) continue
      val name = scriptOfChar(cp) ?: continue
      counts[name] = (counts[name] ?: 0) + 1
    }
    return counts.maxByOrNull { it.value }?.key
  }

  /**
   * Where the runs of letters of different alphabets start in [text] (the first one is 0), when
   * the detection is by Unicode and there are rules by alphabet. Otherwise null.
   */
  @JvmStatic
  fun runStarts(context: Context, text: CharSequence?): List<Int>? =
    try {
      runStartsUnsafe(context, text)
    } catch (e: Throwable) {
      null
    }

  private fun runStartsUnsafe(context: Context, text: CharSequence?): List<Int>? {
    if (text.isNullOrEmpty() || text.length > 3000) return null
    if (detectMode(context) != DETECT_UNICODE) return null
    if (load(context).none { it.kind == KIND_SCRIPT }) return null
    val starts = ArrayList<Int>()
    starts.add(0)
    var current: String? = null
    var i = 0
    while (i < text.length) {
      val cp = Character.codePointAt(text, i)
      val at = i
      i += Character.charCount(cp)
      if (!Character.isLetter(cp)) continue
      val name = scriptOfChar(cp) ?: continue
      if (current != null && name != current) {
        starts.add(at)
        if (starts.size > MAX_RUNS) return null
      }
      current = name
    }
    return if (starts.size > 1) starts else null
  }

  /** The rule to speak [text] with, or null if no rule applies. */
  @JvmStatic
  fun ruleFor(context: Context, text: CharSequence?, locale: Locale?): Rule? {
    val rules = load(context)
    if (rules.isEmpty()) return null
    if (locale != null) {
      val tag = locale.toLanguageTag()
      val exact = rules.firstOrNull { it.kind == KIND_LANG && it.value.equals(tag, true) }
      if (exact != null) return exact
      val byLanguage =
        rules.firstOrNull { it.kind == KIND_LANG && it.value.equals(locale.language, true) }
      if (byLanguage != null) return byLanguage
    }
    val script = scriptOf(text) ?: return null
    return rules.firstOrNull { it.kind == KIND_SCRIPT && it.value == script }
  }
}
