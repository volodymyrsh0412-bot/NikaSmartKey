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
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.macros.NikaSideVoice

/** Nika: own synthesizer, voice and volume for the notifications and for the talking clock. */
class NikaSideVoiceFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  private val loaders = ArrayList<TextToSpeech>()

  override fun getTitle(): CharSequence = getText(R.string.nika_sv_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  override fun onResume() {
    super.onResume()
    rebuild()
  }

  override fun onDestroy() {
    for (t in loaders) {
      try {
        t.shutdown()
      } catch (e: RuntimeException) {
        // skip
      }
    }
    loaders.clear()
    super.onDestroy()
  }

  private fun rebuild() {
    val ctx = context ?: return
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val note = Preference(group.context)
    note.title = getString(R.string.nika_sv_note)
    note.isPersistent = false
    note.isSelectable = false
    group.addPreference(note)
    section(group, NikaSideVoice.NOTIF, R.string.nika_sv_notif)
    section(group, NikaSideVoice.CLOCK, R.string.nika_sv_clock)
  }

  private fun engineName(engine: String): String {
    if (engine.isEmpty()) return getString(R.string.nika_sv_default)
    val pm = requireContext().packageManager
    return try {
      pm.getApplicationLabel(pm.getApplicationInfo(engine, 0)).toString()
    } catch (e: Exception) {
      engine
    }
  }

  private fun section(group: PreferenceGroup, src: String, title: Int) {
    val ctx = requireContext()
    val head = Preference(group.context)
    head.title = getString(title)
    head.isPersistent = false
    head.isSelectable = false
    group.addPreference(head)
    row(group, getString(R.string.nika_sv_engine), engineName(NikaSideVoice.engine(ctx, src))) { chooseEngine(src) }
    row(group, getString(R.string.nika_sv_voice), NikaSideVoice.voice(ctx, src).ifEmpty { getString(R.string.nika_sv_default) }) {
      chooseVoice(src)
    }
    row(group, getString(R.string.nika_sv_volume), getString(R.string.nika_vol_value, NikaSideVoice.volume(ctx, src))) {
      chooseVolume(src)
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

  private fun chooseEngine(src: String) {
    val act = activity ?: return
    val pm = act.packageManager
    val ids = ArrayList<String>()
    val names = ArrayList<String>()
    ids.add("")
    names.add(getString(R.string.nika_sv_default))
    try {
      for (info in pm.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)) {
        val pkg = info.serviceInfo?.packageName ?: continue
        if (pkg !in ids) {
          ids.add(pkg)
          names.add(info.loadLabel(pm).toString())
        }
      }
    } catch (e: RuntimeException) {
      // skip
    }
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_sv_engine)
      .setItems(names.toTypedArray()) { _, which ->
        // A voice belongs to its engine, so it is reset together with the engine.
        NikaSideVoice.set(act, src, ids[which], "", null)
        rebuild()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseVoice(src: String) {
    val act = activity ?: return
    val engine = NikaSideVoice.engine(act, src)
    Toast.makeText(act, R.string.nika_sv_loading, Toast.LENGTH_SHORT).show()
    var loader: TextToSpeech? = null
    val listener =
      TextToSpeech.OnInitListener { status ->
        act.runOnUiThread {
          val tts = loader
          val voices =
            if (status == TextToSpeech.SUCCESS && tts != null) {
              try {
                (tts.voices ?: emptySet()).sortedBy { it.name }
              } catch (e: RuntimeException) {
                emptyList()
              }
            } else {
              emptyList()
            }
          if (voices.isEmpty()) {
            Toast.makeText(act, R.string.nika_sv_novoices, Toast.LENGTH_LONG).show()
            return@runOnUiThread
          }
          val ids = ArrayList<String>()
          val names = ArrayList<String>()
          ids.add("")
          names.add(getString(R.string.nika_sv_default))
          for (v in voices) {
            ids.add(v.name)
            names.add(v.name + " (" + v.locale.toLanguageTag() + ")")
          }
          AlertDialog.Builder(act)
            .setTitle(R.string.nika_sv_voice)
            .setItems(names.toTypedArray()) { _, which ->
              NikaSideVoice.set(act, src, null, ids[which], null)
              rebuild()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        }
      }
    val tts = if (engine.isEmpty()) TextToSpeech(act, listener) else TextToSpeech(act, listener, engine)
    loader = tts
    loaders.add(tts)
  }

  private fun chooseVolume(src: String) {
    val act = activity ?: return
    val bar = SeekBar(act)
    bar.max = 100
    bar.progress = NikaSideVoice.volume(act, src)
    bar.contentDescription = getString(R.string.nika_sv_volume)
    bar.setOnSeekBarChangeListener(
      object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (fromUser) NikaSideVoice.set(act, src, null, null, progress)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
      }
    )
    val box = LinearLayout(act)
    val pad = (16 * act.resources.displayMetrics.density).toInt()
    box.setPadding(pad, pad, pad, pad)
    box.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_sv_volume)
      .setView(box)
      .setPositiveButton(android.R.string.ok) { _, _ -> rebuild() }
      .show()
  }
}
