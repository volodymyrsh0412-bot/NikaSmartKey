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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import androidx.annotation.StringRes
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.talkback.utils.SpeechRecognizerPerformer
import com.google.android.accessibility.talkback.utils.SpeechRecognizerPerformer.SpeechRecognizerRequester
import com.google.android.accessibility.utils.NikaBeep
import com.google.android.accessibility.utils.NikaDiag
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController
import org.json.JSONArray
import org.json.JSONObject

/** The online engines that can recognize the voice commands. */
object NikaVoiceEngines {
  const val ANDROID = "android"
  const val GROQ = "groq"
  const val OPENAI = "openai"
  const val GEMINI = "gemini"
  const val DEEPGRAM = "deepgram"

  class Engine(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val noteRes: Int,
    val keyUrl: String,
  )

  val ALL: List<Engine> =
    listOf(
      Engine(ANDROID, R.string.nika_vc_eng_android, R.string.nika_vc_eng_android_note, ""),
      Engine(GROQ, R.string.nika_vc_eng_groq, R.string.nika_vc_eng_groq_note, "https://console.groq.com/keys"),
      Engine(GEMINI, R.string.nika_vc_eng_gemini, R.string.nika_vc_eng_gemini_note, "https://aistudio.google.com/apikey"),
      Engine(OPENAI, R.string.nika_vc_eng_openai, R.string.nika_vc_eng_openai_note, "https://platform.openai.com/api-keys"),
      Engine(DEEPGRAM, R.string.nika_vc_eng_deepgram, R.string.nika_vc_eng_deepgram_note, "https://console.deepgram.com/"),
    )

  fun find(id: String): Engine = ALL.firstOrNull { it.id == id } ?: ALL[0]
}

/**
 * Nika: voice commands. A command has phrases and one action: a gesture action (also a macro),
 * an app to open, or a number to call. It starts by an action of a gesture, or by saying the
 * name of Nika (experimental).
 */
object NikaVoiceCmds {
  const val ACTION_LISTEN = "nika_voicecmd_listen"
  const val PREF_LIST = "nika_vc_list"
  const val PREF_ENGINE = "nika_vc_engine"
  const val PREF_TRIGGER = "nika_vc_trigger"
  const val PREF_LANG = "nika_vc_lang"
  const val PREF_WORD = "nika_vc_word"
  const val TRIGGER_GESTURE = "gesture"
  const val TRIGGER_VOICE = "voice"
  const val KIND_ACTION = "action"
  const val KIND_APP = "app"
  const val KIND_CALL = "call"
  const val KIND_CALL_PHONE = "callphone"

  class Cmd(
    val id: String,
    val phrases: List<String>,
    val kind: String,
    val value: String,
    val label: String,
  ) {
    val name: String
      get() = phrases.firstOrNull().orEmpty()
  }

  private val handler = Handler(Looper.getMainLooper())
  @Volatile private var busy = false
  private var wakeRunning = false

  private fun prefs(context: Context) = SharedPreferencesUtils.getSharedPreferences(context)

  // ---------------------------------------------------------------- settings

  fun engine(context: Context): String = prefs(context).getString(PREF_ENGINE, NikaVoiceEngines.ANDROID) ?: NikaVoiceEngines.ANDROID

  fun trigger(context: Context): String = prefs(context).getString(PREF_TRIGGER, TRIGGER_GESTURE) ?: TRIGGER_GESTURE

  /** Puts the chosen language of the recognizer of Android into its intent. */
  @JvmStatic
  fun applyLanguage(context: Context, intent: Intent?) {
    val p = prefs(context)
    if (intent == null || !p.contains(PREF_LANG)) return
    val tag =
      when (val l = (p.getString(PREF_LANG, "") ?: "").trim().lowercase()) {
        "" -> return
        "uk" -> "uk-UA"
        "ru" -> "ru-RU"
        "be" -> "be-BY"
        "pl" -> "pl-PL"
        else -> l
      }
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
  }

  fun language(context: Context): String = prefs(context).getString(PREF_LANG, "uk") ?: "uk"

  fun wakeWord(context: Context): String = prefs(context).getString(PREF_WORD, "Ніка")?.ifBlank { null } ?: "Ніка"

  fun keyPref(engine: String): String = "nika_vc_key_$engine"

  fun key(context: Context, engine: String): String {
    val p = prefs(context)
    val own = (p.getString(keyPref(engine), "") ?: "").filter { it in '!'..'~' }
    if (own.isNotEmpty() || engine != NikaVoiceEngines.GEMINI) return own
    return (p.getString("pref_gemini_api_key", "") ?: "").filter { it in '!'..'~' }
  }

  fun setKey(context: Context, engine: String, value: String) {
    prefs(context).edit().putString(keyPref(engine), value.filter { it in '!'..'~' }).apply()
  }

  // ---------------------------------------------------------------- commands

  @JvmStatic
  fun load(context: Context): MutableList<Cmd> {
    val list = ArrayList<Cmd>()
    try {
      val arr = JSONArray(prefs(context).getString(PREF_LIST, "[]"))
      for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val ph = o.getJSONArray("phrases")
        val phrases = ArrayList<String>()
        for (j in 0 until ph.length()) phrases.add(ph.getString(j))
        list.add(Cmd(o.getString("id"), phrases, o.getString("kind"), o.getString("value"), o.optString("label", "")))
      }
    } catch (e: Exception) {
      // a broken list is shown empty
    }
    return list
  }

  fun save(context: Context, list: List<Cmd>) {
    val arr = JSONArray()
    for (c in list) {
      arr.put(
        JSONObject()
          .put("id", c.id)
          .put("phrases", JSONArray(c.phrases))
          .put("kind", c.kind)
          .put("value", c.value)
          .put("label", c.label)
      )
    }
    prefs(context).edit().putString(PREF_LIST, arr.toString()).apply()
  }

  fun newId(): String = "c" + System.currentTimeMillis().toString(36)

  // ---------------------------------------------------------------- matching

  fun norm(s: String): String {
    val sb = StringBuilder()
    for (ch in s.lowercase()) {
      when {
        ch == 'ё' -> sb.append('е')
        ch == 'ґ' -> sb.append('г')
        ch.isLetterOrDigit() -> sb.append(ch)
        else -> sb.append(' ')
      }
    }
    return sb.toString().trim().replace(Regex("\\s+"), " ")
  }

  private fun similarity(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
      val cur = IntArray(b.length + 1)
      cur[0] = i
      for (j in 1..b.length) {
        cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
      }
      prev = cur
    }
    return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
  }

  /** A rough sound of a word in Latin letters, so "Джемени" and "Gemini" look alike. */
  private fun phon(s: String): String {
    val sb = StringBuilder()
    val t = s.replace("дж", "j").replace("ph", "f").replace("ts", "c")
    var i = 0
    while (i < t.length) {
      val ch = t[i]
      val next = if (i + 1 < t.length) t[i + 1] else ' '
      when (ch) {
        'а', 'я' -> sb.append('a')
        'е', 'є', 'э', 'e' -> sb.append('e')
        'и', 'і', 'ї', 'ы', 'й', 'y', 'i' -> sb.append('i')
        'о', 'o' -> sb.append('o')
        'у', 'ю', 'u' -> sb.append('u')
        'б', 'b' -> sb.append('b')
        'в', 'w', 'v' -> sb.append('v')
        'г', 'ґ', 'х', 'h' -> sb.append(if (ch == 'h') 'h' else 'g')
        'g' -> sb.append(if (next == 'e' || next == 'i' || next == 'y') 'j' else 'g')
        'д', 'd' -> sb.append('d')
        'ж', 'з', 'z', 'j' -> sb.append('j')
        'к', 'k', 'q' -> sb.append('k')
        'c' -> sb.append(if (next == 'e' || next == 'i' || next == 'y') 's' else 'k')
        'л', 'l' -> sb.append('l')
        'м', 'm' -> sb.append('m')
        'н', 'n' -> sb.append('n')
        'п', 'p' -> sb.append('p')
        'р', 'r' -> sb.append('r')
        'с', 's', 'ц', 'ч', 'ш', 'щ' -> sb.append('s')
        'т', 't' -> sb.append('t')
        'ф', 'f' -> sb.append('f')
        'x' -> sb.append("ks")
        'ь', 'ъ' -> {}
        ' ' -> sb.append(' ')
        else -> if (ch.isLetterOrDigit()) sb.append(ch)
      }
      i++
    }
    return sb.toString()
  }

  private fun scoreFor(said: String, phrase: String): Double {
    // The longer phrase that is really said wins, so "Открой" does not hide "Открой Телеграм".
    if (said == phrase) return 2.0 + phrase.length / 1000.0
    if (" $said ".contains(" $phrase ")) return 1.5 + phrase.length / 1000.0
    var best = similarity(said, phrase)
    val ps = phon(phrase)
    val ss = phon(said)
    best = maxOf(best, similarity(ss, ps))
    val words = ss.split(' ').filter { it.isNotEmpty() }
    val n = ps.split(' ').filter { it.isNotEmpty() }.size
    if (n in 1..words.size) {
      for (i in 0..words.size - n) best = maxOf(best, similarity(words.subList(i, i + n).joinToString(" "), ps))
    }
    return best
  }

  fun match(context: Context, said: String): Cmd? {
    val t = norm(said)
    if (t.isEmpty()) return null
    var best: Cmd? = null
    var bestScore = 0.0
    for (c in load(context)) {
      for (p in c.phrases) {
        val n = norm(p)
        if (n.isEmpty()) continue
        val score = scoreFor(t, n)
        if (score > bestScore) {
          bestScore = score
          best = c
        }
      }
    }
    return if (bestScore >= 0.78) best else null
  }

  // ---------------------------------------------------------------- actions of gestures

  const val ACT_PREFIX = "nika_vc:"

  @JvmStatic fun actionOf(id: String): String = ACT_PREFIX + id

  @JvmStatic fun isCmdAction(value: String?): Boolean = value != null && value.startsWith(ACT_PREFIX)

  @JvmStatic
  fun cmdLabel(context: Context, value: String?): String? {
    if (!isCmdAction(value)) return null
    val cmd = load(context).firstOrNull { it.id == value!!.removePrefix(ACT_PREFIX) }
    return context.getString(R.string.nika_vc_prefix, cmd?.name ?: "?")
  }

  @JvmStatic
  fun performCmd(service: TalkBackService, value: String): Boolean {
    val cmd = load(service).firstOrNull { it.id == value.removePrefix(ACT_PREFIX) }
    if (cmd != null) run(service, cmd)
    return true
  }

  // ---------------------------------------------------------------- running

  private fun say(service: TalkBackService, text: String) {
    try {
      service.speechController.speak(
        text,
        SpeechController.QUEUE_MODE_FLUSH_ALL,
        FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
        null,
        Performance.EVENT_ID_UNTRACKED,
      )
    } catch (e: RuntimeException) {
      // skip
    }
  }

  fun run(service: TalkBackService, cmd: Cmd) {
    NikaDiag.log("voicecmd", "run " + cmd.name + " / " + cmd.kind + " / " + cmd.value)
    try {
      when (cmd.kind) {
        KIND_ACTION -> service.nikaGestureController()?.performAction(cmd.value, Performance.EVENT_ID_UNTRACKED)
        KIND_APP -> {
          val intent = service.packageManager.getLaunchIntentForPackage(cmd.value)
          if (intent == null) {
            say(service, service.getString(R.string.nika_vc_no_app))
          } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
          }
        }
        KIND_CALL_PHONE -> {
          val uri = Uri.parse("tel:" + Uri.encode(cmd.value))
          try {
            val tm = service.getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            tm.placeCall(uri, Bundle())
          } catch (e: SecurityException) {
            say(service, service.getString(R.string.nika_vc_call_noperm))
            service.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          }
        }
        KIND_CALL -> {
          val uri = Uri.parse("tel:" + Uri.encode(cmd.value))
          try {
            service.startActivity(Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          } catch (e: SecurityException) {
            service.startActivity(Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
          }
        }
      }
    } catch (e: RuntimeException) {
      say(service, service.getString(R.string.nika_vc_failed))
    }
  }

  /** Handles the words that were said. Returns false if no command fits. */
  private fun handle(service: TalkBackService, said: String): Boolean {
    val cmd = match(service, said)
    if (cmd == null) {
      say(service, service.getString(R.string.nika_vc_unknown, said))
      return false
    }
    run(service, cmd)
    return true
  }

  /** Says hello with the name, then a beep, then listens once and runs the command that was said. */
  @JvmStatic
  fun listen(service: TalkBackService) {
    handler.post {
      if (busy) return@post
      busy = true
      val name = userName(service)
      val mode = greetMode(service)
      val hello =
        when {
          mode == GREET_BEEP -> ""
          mode == GREET_NAME && name.isNotEmpty() -> service.getString(R.string.nika_vc_greet, name)
          else -> service.getString(R.string.nika_vc_greet_plain)
        }
      if (hello.isNotEmpty()) say(service, hello)
      handler.postDelayed(
        {
          try {
            NikaBeep.play(service, NikaBeep.START)
            recognize(service, 6) { ok, text ->
              NikaBeep.play(service, NikaBeep.STOP)
              busy = false
              if (!ok) say(service, text) else if (text.isBlank()) say(service, service.getString(R.string.nika_vc_nothing)) else handle(service, text)
            }
          } catch (e: Throwable) {
            busy = false
            NikaDiag.log("voicecmd", "listen failed: $e")
          }
        },
        if (hello.isEmpty()) 150L else 900L + hello.length * 65L,
      )
    }
  }

  const val PREF_POWER = "nika_vc_power"
  const val PREF_NAME = "nika_vc_name"
  const val PREF_GREET = "nika_vc_greet_mode"
  const val GREET_NAME = "name"
  const val GREET_HELLO = "hello"
  const val GREET_BEEP = "beep"

  fun greetMode(context: Context): String = prefs(context).getString(PREF_GREET, GREET_NAME) ?: GREET_NAME
  const val POWER_OFF = "off"
  const val POWER_DOUBLE = "double"
  const val POWER_TRIPLE = "triple"

  fun power(context: Context): String = prefs(context).getString(PREF_POWER, POWER_OFF) ?: POWER_OFF

  fun userName(context: Context): String = (prefs(context).getString(PREF_NAME, "") ?: "").trim()

  private var screenReceiver: android.content.BroadcastReceiver? = null
  private var screenOwner: TalkBackService? = null
  private var presses = 0

  private val firePower = Runnable {
    val owner = screenOwner
    val n = presses
    presses = 0
    if (owner == null) return@Runnable
    val mode = power(owner)
    if ((mode == POWER_DOUBLE && n == 2) || (mode == POWER_TRIPLE && n == 3)) listen(owner)
  }

  /**
   * The power button cannot be read by a screen reader, but each press changes the screen: it goes
   * off or on. Two or three changes in a row are counted as a double or a triple press.
   */
  private fun applyPower(service: TalkBackService) {
    val mode = power(service)
    if (mode == POWER_OFF) {
      screenReceiver?.let {
        try {
          screenOwner?.unregisterReceiver(it)
        } catch (e: RuntimeException) {
          // skip
        }
      }
      screenReceiver = null
      screenOwner = null
      handler.removeCallbacks(firePower)
      return
    }
    screenOwner = service
    if (screenReceiver != null) return
    val receiver =
      object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          presses++
          handler.removeCallbacks(firePower)
          handler.postDelayed(firePower, 900)
        }
      }
    val filter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
    filter.addAction(Intent.ACTION_SCREEN_ON)
    service.registerReceiver(receiver, filter)
    screenReceiver = receiver
  }

  /** Called when the settings change and when the service starts. */
  @JvmStatic
  fun applyTrigger(service: TalkBackService) {
    handler.post {
      try {
        wakeRunning = false
        applyPower(service)
      } catch (e: Throwable) {
        NikaDiag.log("voicecmd", "trigger failed: $e")
      }
    }
  }

  /** True while the on-screen keyboard is open: it needs the microphone for voice typing. */
  private fun keyboardShown(service: TalkBackService): Boolean =
    try {
      service.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    } catch (e: Throwable) {
      false
    }

  /** Listens for the wake word (the recognizer of Google beeps at each start; it cannot be changed). */
  private fun listenQuiet(service: TalkBackService, seconds: Int, done: (Boolean, String) -> Unit) {
    NikaDiag.log("voicecmd", "wake listen")
    recognize(service, seconds, done)
  }

  private fun wakeCycle(service: TalkBackService) {
    if (!wakeRunning || trigger(service) != TRIGGER_VOICE) {
      wakeRunning = false
      return
    }
    val power = service.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (busy || !power.isInteractive || NikaBeep.externalMic() || keyboardShown(service)) {
      handler.postDelayed({ wakeCycle(service) }, 3000)
      return
    }
    busy = true
    listenQuiet(service, 4) { ok, text ->
      busy = false
      val word = norm(wakeWord(service))
      val heard = norm(text)
      val at = if (ok && word.isNotEmpty()) heard.indexOf(word) else -1
      if (at >= 0) {
        val rest = heard.substring(at + word.length).trim()
        NikaBeep.play(service, NikaBeep.START)
        if (rest.isNotEmpty()) {
          handle(service, rest)
          handler.postDelayed({ wakeCycle(service) }, 800)
        } else {
          busy = true
          listenQuiet(service, 6) { ok2, text2 ->
            NikaBeep.play(service, NikaBeep.STOP)
            busy = false
            if (ok2 && text2.isNotBlank()) handle(service, text2)
            handler.postDelayed({ wakeCycle(service) }, 800)
          }
        }
      } else {
        handler.postDelayed({ wakeCycle(service) }, if (ok) 1500 else 8000)
      }
    }
  }

  // ---------------------------------------------------------------- recognition

  /** [done] gets true and the text (maybe empty), or false and a message. Runs on the main thread. */
  private fun recognize(service: TalkBackService, seconds: Int, doneOuter: (Boolean, String) -> Unit) {
    NikaBeep.setOwnMic(true)
    val done: (Boolean, String) -> Unit = { ok, text ->
      NikaBeep.setOwnMic(false)
      doneOuter(ok, text)
    }
    val engine = engine(service)
    if (engine == NikaVoiceEngines.ANDROID) {
      recognizeAndroid(service, seconds, done)
      return
    }
    val key = key(service, engine)
    if (key.isEmpty()) {
      done(false, service.getString(R.string.nika_vc_nokey))
      return
    }
    Thread {
        var ok = true
        var result = ""
        try {
          val wav = NikaSpeechCloud.recordWav(service, seconds * 1000)
          if (wav != null) {
            result = NikaSpeechCloud.transcribe(engine, key, "", language(service), wav)
          }
        } catch (e: Exception) {
          ok = false
          result = service.getString(R.string.nika_vc_error, e.message ?: e.javaClass.simpleName)
          NikaDiag.log("voicecmd", "engine failed: $e")
        }
        handler.post { done(ok, result) }
      }
      .start()
  }

  private fun recognizeAndroid(service: TalkBackService, seconds: Int, done: (Boolean, String) -> Unit) {
    var finished = false
    var performer: SpeechRecognizerPerformer? = null
    var timer: Runnable? = null
    fun finish(ok: Boolean, text: String) {
      if (finished) return
      finished = true
      timer?.let(handler::removeCallbacks)
      try {
        performer?.reset()
      } catch (e: RuntimeException) {
        // gone already
      }
      done(ok, text)
    }
    val t = Runnable { finish(true, "") }
    timer = t
    handler.postDelayed(t, seconds * 1000L + 3000L)
    val recognizer = SpeechRecognizerPerformer(service, seconds * 1000, false)
    performer = recognizer
    recognizer.setListener(
      object : SpeechRecognizerRequester {
        override fun onResult(command: String?, isPartialResult: Boolean): Boolean {
          if (!isPartialResult) finish(true, command.orEmpty())
          return false
        }

        override fun onFeedbackEvent(eventId: Int) {
          when (eventId) {
            SpeechRecognizerRequester.EVENT_PLATFORM_NOT_SUPPORT,
            SpeechRecognizerRequester.EVENT_FEATURE_UNAVAILABLE ->
              finish(false, service.getString(R.string.nika_vc_na))
            SpeechRecognizerRequester.EVENT_MIC_PERMISSION_NOT_GRANTED ->
              finish(false, service.getString(R.string.nika_vc_noperm))
            else -> {}
          }
        }

        override fun onError(error: Int) {
          if (error == 7 || error == 6) finish(true, "") else finish(false, service.getString(R.string.nika_vc_error, error.toString()))
        }
      }
    )
    recognizer.doListening()
  }
}
