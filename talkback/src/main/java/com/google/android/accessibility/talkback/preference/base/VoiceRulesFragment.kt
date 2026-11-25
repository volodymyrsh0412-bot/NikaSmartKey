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
package com.google.android.accessibility.talkback.preference.base

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.utils.output.FailoverTextToSpeech
import com.google.android.accessibility.utils.output.NikaVoiceRules
import java.util.Locale

/**
 * Nika: voices by language and by alphabet (Cyrillic, Latin ...), from the voices of any installed
 * speech engine, each with its own speed and volume.
 */
class VoiceRulesFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  private val loaders = HashMap<String, TextToSpeech>()

  override fun getTitle(): CharSequence = getText(R.string.nika_vr_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  override fun onResume() {
    super.onResume()
    rebuild()
  }

  override fun onDestroy() {
    for (t in loaders.values) {
      try {
        t.shutdown()
      } catch (e: RuntimeException) {
        // skip
      }
    }
    loaders.clear()
    super.onDestroy()
  }

  private fun currentEngine(): String =
    try {
      context?.let { FailoverTextToSpeech.getSelectedEngine(it) } ?: ""
    } catch (e: RuntimeException) {
      ""
    }

  /** The installed speech engines: package name and the name to show. */
  private fun installedEngines(): List<Pair<String, String>> {
    val ctx = context ?: return emptyList()
    val result = ArrayList<Pair<String, String>>()
    try {
      val pm = ctx.packageManager
      for (info in pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)) {
        val pkg = info.serviceInfo?.packageName ?: continue
        if (result.none { it.first == pkg }) {
          result.add(Pair(pkg, info.loadLabel(pm).toString()))
        }
      }
    } catch (e: RuntimeException) {
      // skip
    }
    val now = currentEngine()
    if (now.isNotEmpty() && result.none { it.first == now }) result.add(Pair(now, now))
    return result.sortedBy { if (it.first == now) "" else it.second.lowercase() }
  }

  private fun engineName(engine: String): String =
    installedEngines().firstOrNull { it.first == engine }?.second ?: engine

  private fun scriptName(value: String): String =
    when (value) {
      "cyrillic" -> getString(R.string.nika_vr_script_cyrillic)
      "latin" -> getString(R.string.nika_vr_script_latin)
      "greek" -> getString(R.string.nika_vr_script_greek)
      "arabic" -> getString(R.string.nika_vr_script_arabic)
      "hebrew" -> getString(R.string.nika_vr_script_hebrew)
      else -> getString(R.string.nika_vr_script_cjk)
    }

  private fun ruleTitle(rule: NikaVoiceRules.Rule): String {
    val what =
      if (rule.kind == NikaVoiceRules.KIND_SCRIPT) scriptName(rule.value)
      else
        Locale.forLanguageTag(rule.value).let {
          it.getDisplayName(it).ifEmpty { rule.value } + " (" + rule.value + ")"
        }
    val engine = engineName(rule.engine.ifEmpty { currentEngine() })
    return getString(R.string.nika_vr_row2, what, rule.voice, engine, rule.rate, rule.volume)
  }

  private fun detectText(mode: String): String =
    getString(
      if (mode == NikaVoiceRules.DETECT_UNICODE) R.string.nika_vr_detect_unicode
      else R.string.nika_vr_detect_alphabet
    )

  private fun rebuild() {
    val ctx = context ?: return
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val note = Preference(group.context)
    note.title = getString(R.string.nika_vr_note2)
    note.isPersistent = false
    note.isSelectable = false
    group.addPreference(note)
    row(group, getString(R.string.nika_vr_detect_title), detectText(NikaVoiceRules.detectMode(ctx))) {
      chooseDetect()
    }
    row(group, getString(R.string.nika_vr_add), null) { addRule() }
    for (rule in NikaVoiceRules.load(ctx)) {
      row(group, ruleTitle(rule), getString(R.string.nika_vr_tap_edit)) { ruleMenu(rule) }
    }
  }

  private fun row(group: PreferenceGroup, title: String, summary: String?, click: () -> Unit) {
    val p = Preference(group.context)
    p.title = title
    p.summary = summary
    p.isPersistent = false
    p.setOnPreferenceClickListener {
      click()
      true
    }
    group.addPreference(p)
  }

  private fun chooseDetect() {
    val act = activity ?: return
    val modes = listOf(NikaVoiceRules.DETECT_ALPHABET, NikaVoiceRules.DETECT_UNICODE)
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vr_detect_title)
      .setItems(modes.map { detectText(it) }.toTypedArray()) { _, which ->
        val ctx = context ?: return@setItems
        NikaVoiceRules.setDetectMode(ctx, modes[which])
        rebuild()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun replaceRule(old: NikaVoiceRules.Rule?, new: NikaVoiceRules.Rule?) {
    val ctx = context ?: return
    val all =
      NikaVoiceRules.load(ctx)
        .filter { it != old && !(new != null && it.kind == new.kind && it.value == new.value) }
        .toMutableList()
    if (new != null) all.add(new)
    NikaVoiceRules.save(ctx, all)
    rebuild()
  }

  private fun ruleMenu(rule: NikaVoiceRules.Rule) {
    val act = activity ?: return
    val items =
      arrayOf(
        getString(R.string.nika_vr_menu_voice),
        getString(R.string.nika_vr_menu_rate),
        getString(R.string.nika_vr_menu_volume),
        getString(R.string.nika_vr_remove),
      )
    AlertDialog.Builder(act)
      .setTitle(ruleTitle(rule))
      .setItems(items) { _, which ->
        when (which) {
          0 -> withVoices(rule.engine.ifEmpty { currentEngine() }) { voices -> pickVoice(rule.engine, rule, rule.kind, rule.value, voicesFor(rule.kind, rule.value, voices)) }
          1 ->
            pickPercent(R.string.nika_vr_menu_rate, listOf(50, 60, 70, 80, 90, 100, 110, 125, 150, 175, 200, 250, 300)) {
              replaceRule(rule, rule.copy(rate = it))
            }
          2 ->
            pickPercent(R.string.nika_vr_menu_volume, listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10)) {
              replaceRule(rule, rule.copy(volume = it))
            }
          else -> replaceRule(rule, null)
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun pickPercent(title: Int, values: List<Int>, onPick: (Int) -> Unit) {
    val act = activity ?: return
    AlertDialog.Builder(act)
      .setTitle(title)
      .setItems(values.map { "$it%" }.toTypedArray()) { _, which -> onPick(values[which]) }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  /** Loads the voices of a speech engine, then calls [done] on the main thread. */
  private fun withVoices(engine: String, done: (List<Voice>) -> Unit) {
    val ctx = context?.applicationContext ?: return
    Toast.makeText(ctx, R.string.nika_vr_loading, Toast.LENGTH_SHORT).show()
    try {
      loaders.remove(engine)?.shutdown()
      var created: TextToSpeech? = null
      created =
        TextToSpeech(
          ctx,
          { status ->
            val list =
              if (status == TextToSpeech.SUCCESS) {
                try {
                  (created?.voices ?: emptySet<Voice>()).sortedBy { it.locale.toLanguageTag() + it.name }
                } catch (e: RuntimeException) {
                  emptyList()
                }
              } else {
                emptyList()
              }
            if (isAdded) done(list)
          },
          engine.ifEmpty { null },
        )
      loaders[engine] = created
    } catch (e: RuntimeException) {
      Toast.makeText(ctx, R.string.nika_vr_novoices, Toast.LENGTH_LONG).show()
    }
  }

  private fun voicesFor(kind: String, value: String, voices: List<Voice>): List<Voice> =
    if (kind == NikaVoiceRules.KIND_LANG) voices.filter { it.locale.language == value } else voices

  private fun addRule() {
    val act = activity ?: return
    val engines = installedEngines()
    if (engines.isEmpty()) {
      Toast.makeText(act, R.string.nika_vr_novoices, Toast.LENGTH_LONG).show()
      return
    }
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vr_pick_engine)
      .setItems(engines.map { it.second }.toTypedArray()) { _, which ->
        val engine = engines[which].first
        withVoices(engine) { voices -> chooseKind(engine, voices) }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseKind(engine: String, voices: List<Voice>) {
    val act = activity ?: return
    if (voices.isEmpty()) {
      Toast.makeText(act, R.string.nika_vr_novoices, Toast.LENGTH_LONG).show()
      return
    }
    // The kinds: every language that has voices, then the alphabets.
    val languages = voices.map { it.locale.language }.distinct().sorted()
    val scripts = listOf("cyrillic", "latin", "greek", "arabic", "hebrew", "cjk")
    val labels = ArrayList<String>()
    for (l in languages) {
      val loc = Locale.forLanguageTag(l)
      labels.add(getString(R.string.nika_vr_kind_lang, loc.getDisplayLanguage(loc).ifEmpty { l }, l))
    }
    for (s in scripts) labels.add(getString(R.string.nika_vr_kind_script, scriptName(s)))
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vr_add)
      .setItems(labels.toTypedArray()) { _, which ->
        if (which < languages.size) {
          pickVoice(engine, null, NikaVoiceRules.KIND_LANG, languages[which], voicesFor(NikaVoiceRules.KIND_LANG, languages[which], voices))
        } else {
          pickVoice(engine, null, NikaVoiceRules.KIND_SCRIPT, scripts[which - languages.size], voices)
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun pickVoice(
    engine: String,
    old: NikaVoiceRules.Rule?,
    kind: String,
    value: String,
    list: List<Voice>,
  ) {
    val act = activity ?: return
    if (list.isEmpty()) {
      Toast.makeText(act, R.string.nika_vr_novoices, Toast.LENGTH_LONG).show()
      return
    }
    val labels = list.map { it.name + " — " + it.locale.let { l -> l.getDisplayName(l) } }
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vr_pick_voice)
      .setItems(labels.toTypedArray()) { _, which ->
        val keepRate = old?.rate ?: 100
        val keepVolume = old?.volume ?: 100
        replaceRule(old, NikaVoiceRules.Rule(kind, value, list[which].name, engine, keepRate, keepVolume))
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
