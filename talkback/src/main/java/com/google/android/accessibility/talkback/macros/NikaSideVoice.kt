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

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.google.android.accessibility.utils.SharedPreferencesUtils

/**
 * Nika: own voice, synthesizer and volume for the notifications and for the talking clock. If nothing
 * was changed in the settings, the text goes to the normal speech of Nika.
 */
object NikaSideVoice {
  const val NOTIF = "notif"
  const val CLOCK = "clock"

  private val handler = Handler(Looper.getMainLooper())

  private class Slot(val engine: String, val voice: String) {
    var tts: TextToSpeech? = null
    var ready = false
    val pending = ArrayList<Pair<String, Boolean>>()
  }

  private val slots = HashMap<String, Slot>()
  private val failed = HashSet<String>()
  private var counter = 0

  private fun prefs(context: Context) = SharedPreferencesUtils.getSharedPreferences(context)

  @JvmStatic fun engine(context: Context, src: String): String = prefs(context).getString("nika_side_${src}_engine", "") ?: ""

  @JvmStatic fun voice(context: Context, src: String): String = prefs(context).getString("nika_side_${src}_voice", "") ?: ""

  @JvmStatic fun volume(context: Context, src: String): Int = prefs(context).getInt("nika_side_${src}_vol", 100).coerceIn(0, 100)

  @JvmStatic
  fun set(context: Context, src: String, engine: String?, voice: String?, volume: Int?) {
    val e = prefs(context).edit()
    if (engine != null) e.putString("nika_side_${src}_engine", engine)
    if (voice != null) e.putString("nika_side_${src}_voice", voice)
    if (volume != null) e.putInt("nika_side_${src}_vol", volume)
    e.apply()
    failed.remove(src)
  }

  @JvmStatic
  fun isCustom(context: Context, src: String): Boolean =
    engine(context, src).isNotEmpty() || voice(context, src).isNotEmpty() || volume(context, src) < 100

  /** Speaks the text with the own voice. Returns false if the normal speech must do it. */
  @JvmStatic
  fun speak(context: Context, src: String, text: String, flush: Boolean): Boolean {
    try {
      if (text.isBlank() || src in failed || !isCustom(context, src)) return false
      val app = context.applicationContext
      handler.post {
        try {
          speakNow(app, src, text, flush)
        } catch (e: Throwable) {
          failed.add(src)
        }
      }
      return true
    } catch (e: Throwable) {
      return false
    }
  }

  private fun speakNow(app: Context, src: String, text: String, flush: Boolean) {
    var slot = slots[src]
    val engine = engine(app, src)
    val voice = voice(app, src)
    if (slot == null || slot.engine != engine || slot.voice != voice) {
      slot?.tts?.shutdown()
      val fresh = Slot(engine, voice)
      slots[src] = fresh
      slot = fresh
      fresh.pending.add(Pair(text, flush))
      val listener =
        TextToSpeech.OnInitListener { status ->
          handler.post {
            if (status != TextToSpeech.SUCCESS) {
              failed.add(src)
              fresh.tts?.shutdown()
              slots.remove(src)
              return@post
            }
            val tts = fresh.tts ?: return@post
            try {
              tts.setAudioAttributes(
                AudioAttributes.Builder()
                  .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                  .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                  .build()
              )
              if (voice.isNotEmpty()) {
                tts.voices?.firstOrNull { it.name == voice }?.let { tts.voice = it }
              }
            } catch (e: Throwable) {
              // the engine default stays
            }
            fresh.ready = true
            for ((t, f) in fresh.pending) say(app, src, tts, t, f)
            fresh.pending.clear()
          }
        }
      fresh.tts = if (engine.isEmpty()) TextToSpeech(app, listener) else TextToSpeech(app, listener, engine)
      return
    }
    val tts = slot.tts
    if (!slot.ready || tts == null) {
      slot.pending.add(Pair(text, flush))
    } else {
      say(app, src, tts, text, flush)
    }
  }

  private fun say(app: Context, src: String, tts: TextToSpeech, text: String, flush: Boolean) {
    val params = Bundle()
    params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume(app, src) / 100f)
    counter++
    tts.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, params, "nika_side_$counter")
  }
}
