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

import android.media.AudioManager
import android.media.ToneGenerator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.LocaleSpan
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.talkback.actor.gemini.local.LocalModelManager
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

/**
 * Nika: translates the text of the focused element, the clipboard or the whole screen and says the
 * result. The translation is made by the model on the phone (the one downloaded for the on-device
 * AI) or by an online service with the user's own key. The actions are assigned to gestures and keys
 * through [NikaTranslatorActions].
 */
class NikaTranslator private constructor(private val service: TalkBackService) {

  private val worker =
    Executors.newSingleThreadExecutor { r -> Thread(r, "nika-translate").apply { isDaemon = true } }
  private val main = Handler(Looper.getMainLooper())

  private class Failure(message: String) : Exception(message)

  // ----- settings (the screens may write to either store) -----

  private fun stores(): List<SharedPreferences> {
    val list = ArrayList<SharedPreferences>(2)
    try {
      list.add(PreferenceManager.getDefaultSharedPreferences(service))
    } catch (e: RuntimeException) {
      // skip
    }
    try {
      val other = SharedPreferencesUtils.getSharedPreferences(service)
      if (list.none { it === other }) list.add(other)
    } catch (e: RuntimeException) {
      // skip
    }
    return list
  }

  private fun str(key: String, default: String = ""): String {
    for (store in stores()) {
      if (store.contains(key)) {
        return try {
          store.getString(key, default) ?: default
        } catch (e: ClassCastException) {
          default
        }
      }
    }
    return default
  }

  private fun bool(key: String, default: Boolean): Boolean {
    for (store in stores()) {
      if (store.contains(key)) {
        return try {
          store.getBoolean(key, default)
        } catch (e: ClassCastException) {
          default
        }
      }
    }
    return default
  }

  /** A key without the spaces and invisible characters that a paste can bring along. */
  private fun clean(text: String): String = text.filter { it in '!'..'~' }

  // ----- the actions -----

  fun translateFocus() {
    val text = focusText()
    if (text.isNullOrBlank()) {
      say(service.getString(R.string.nika_tr_nothing), true)
      return
    }
    start(text)
  }

  fun translateClipboard() {
    val text =
      try {
        val clipboard = service.getSystemService(ClipboardManager::class.java)
        clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(service)
          ?.toString()
      } catch (e: RuntimeException) {
        null
      }
    if (text.isNullOrBlank()) {
      say(service.getString(R.string.nika_tr_clipboard_empty), true)
      return
    }
    start(text)
  }

  fun translateLastSpoken() {
    val text =
      try {
        service.speechController.lastUtterance?.aggregateText?.toString()
      } catch (e: RuntimeException) {
        null
      }
    if (text.isNullOrBlank()) {
      say(service.getString(R.string.nika_tr_nothing), true)
      return
    }
    start(text)
  }

  fun translateScreen() {
    val text = screenText()
    if (text.isBlank()) {
      say(service.getString(R.string.nika_tr_nothing), true)
      return
    }
    start(text)
  }

  private fun focusText(): String? {
    return try {
      val root = service.rootInActiveWindow ?: return null
      val node = root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY) ?: return null
      val own = node.text?.toString()
      val selStart = node.textSelectionStart
      val selEnd = node.textSelectionEnd
      if (own != null && selStart >= 0 && selEnd > selStart && selEnd <= own.length) {
        own.substring(selStart, selEnd)
      } else if (!own.isNullOrBlank()) {
        own
      } else {
        node.contentDescription?.toString() ?: collect(node, 60)
      }
    } catch (e: RuntimeException) {
      null
    }
  }

  private fun screenText(): String {
    return try {
      val root = service.rootInActiveWindow ?: return ""
      collect(root, 400)
    } catch (e: RuntimeException) {
      ""
    }
  }

  /** The visible texts of [root] and its children, one per line. */
  private fun collect(root: AccessibilityNodeInfo, limit: Int): String {
    val lines = LinkedHashSet<String>()
    var visited = 0
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.addLast(root)
    while (stack.isNotEmpty() && visited < limit) {
      val node = stack.removeLast()
      visited++
      if (!node.isVisibleToUser) continue
      val label = (node.text ?: node.contentDescription)?.toString()?.trim()
      if (!label.isNullOrEmpty()) lines.add(label)
      for (i in node.childCount - 1 downTo 0) {
        node.getChild(i)?.let { stack.addLast(it) }
      }
    }
    return lines.joinToString("\n")
  }

  // ----- translating -----

  private fun start(source: String) {
    val text = if (source.length > MAX_CHARS) source.substring(0, MAX_CHARS) else source
    when (str("nika_tr_prefix", "word")) {
      "none" -> {}
      "sound" -> {
        try {
          com.google.android.accessibility.utils.NikaBeep.play(service, com.google.android.accessibility.utils.NikaBeep.TRANSLATE)
        } catch (e: RuntimeException) {
          // skip
        }
      }
      else -> say(service.getString(R.string.nika_tr_working), true)
    }
    worker.execute {
      try {
        val code = str("nika_tr_lang", "uk")
        val result = translate(text, code)
        main.post { deliver(result, code) }
      } catch (e: Failure) {
        main.post { say(e.message ?: service.getString(R.string.nika_tr_failed), true) }
      } catch (e: Exception) {
        main.post {
          say(service.getString(R.string.nika_tr_failed) + ": " + (e.message ?: e.javaClass.simpleName), true)
        }
      }
    }
  }

  private fun deliver(result: String, code: String) {
    if (bool("nika_tr_copy", false)) {
      try {
        service.getSystemService(ClipboardManager::class.java)
          ?.setPrimaryClip(ClipData.newPlainText("translation", result))
      } catch (e: RuntimeException) {
        // skip
      }
    }
    val spoken = SpannableString(result)
    try {
      spoken.setSpan(
        LocaleSpan(Locale.forLanguageTag(code)),
        0,
        spoken.length,
        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
      )
    } catch (e: RuntimeException) {
      // speak without the language
    }
    say(spoken, true)
  }

  private fun languageName(code: String): String =
    LANGUAGES[code] ?: Locale.forLanguageTag(code).getDisplayLanguage(Locale.ENGLISH).ifEmpty { code }

  private fun translate(text: String, code: String): String {
    val language = languageName(code)
    val mode = str("nika_tr_mode", "auto")
    val provider = str("nika_tr_provider", "gemini")
    val system =
      "You are a translator. Translate the user's text into $language. " +
        "Answer with the translation only: no comments, no quotes, no explanations."
    return when (mode) {
      "local" -> local(text, language)
      "online" -> online(provider, system, text)
      else -> {
        val hasKey = hasKey(provider)
        if (!hasKey) {
          local(text, language)
        } else {
          try {
            online(provider, system, text)
          } catch (e: Failure) {
            if (localReady()) local(text, language) else throw e
          } catch (e: java.io.IOException) {
            if (localReady()) local(text, language) else throw Failure(netMessage(e))
          }
        }
      }
    }
  }

  private fun netMessage(e: Exception): String =
    service.getString(R.string.nika_tr_no_network) + " (" + (e.message ?: e.javaClass.simpleName) + ")"

  private fun localReady(): Boolean =
    try {
      LocalModelManager.get(service).isReady()
    } catch (e: RuntimeException) {
      false
    }

  private fun local(text: String, language: String): String {
    val manager = LocalModelManager.get(service)
    if (!manager.isReady()) throw Failure(service.getString(R.string.nika_tr_no_local))
    val model = manager.llm() ?: throw Failure(service.getString(R.string.nika_tr_no_local))
    val answer =
      model.generate(
        "Translate the following text into $language. Reply with the translation only.\n\n$text",
        null,
      )
    val out = answer.trim()
    if (out.isEmpty()) throw Failure(service.getString(R.string.nika_tr_failed))
    return out
  }

  // ----- online services -----

  private fun keyOf(provider: String): String {
    val own = clean(str("nika_tr_${provider}_key"))
    if (own.isNotEmpty()) return own
    if (provider == "gemini") {
      // The key that Describe image already uses.
      for (store in stores()) {
        val shared = clean(store.getString("pref_gemini_api_key", null).orEmpty())
        if (shared.isNotEmpty()) return shared
      }
    }
    return ""
  }

  private fun hasKey(provider: String): Boolean =
    when (provider) {
      "vivo" -> str("nika_tr_nvdacn_user").isNotBlank() && str("nika_tr_nvdacn_pass").isNotBlank()
      "custom" -> str("nika_tr_custom_base").isNotBlank() && keyOf("custom").isNotEmpty()
      else -> keyOf(provider).isNotEmpty()
    }

  private fun modelOf(provider: String): String =
    str("nika_tr_${provider}_model").trim().ifEmpty { DEFAULT_MODELS[provider].orEmpty() }

  private fun online(provider: String, system: String, text: String): String {
    if (!hasKey(provider)) throw Failure(service.getString(R.string.nika_tr_no_key))
    val out =
      when (provider) {
        "claude" -> claude(system, text)
        "gemini" -> gemini(system, text)
        "vivo" -> vivo(system, text)
        else -> openAiLike(provider, system, text)
      }
    val trimmed = out.trim()
    if (trimmed.isEmpty()) throw Failure(service.getString(R.string.nika_tr_failed))
    return trimmed
  }

  private fun claude(system: String, text: String): String {
    val body =
      JSONObject()
        .put("model", modelOf("claude"))
        .put("max_tokens", 4000)
        .put("system", system)
        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
    val reply =
      post(
        "https://api.anthropic.com/v1/messages",
        mapOf(
          "content-type" to "application/json",
          "x-api-key" to keyOf("claude"),
          "anthropic-version" to "2023-06-01",
        ),
        body.toString(),
        "Claude",
      )
    val parts = reply.optJSONArray("content") ?: throw Failure("Claude: empty answer")
    val sb = StringBuilder()
    for (i in 0 until parts.length()) {
      val p = parts.getJSONObject(i)
      if (p.optString("type") == "text") sb.append(p.optString("text")).append('\n')
    }
    return sb.toString()
  }

  private fun gemini(system: String, text: String): String {
    val body =
      JSONObject()
        .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        .put(
          "contents",
          JSONArray()
            .put(
              JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))
            ),
        )
        .put("generationConfig", JSONObject().put("temperature", 0))
    val reply =
      post(
        "https://generativelanguage.googleapis.com/v1beta/models/" +
          URLEncoder.encode(modelOf("gemini"), "UTF-8") +
          ":generateContent",
        mapOf("content-type" to "application/json", "x-goog-api-key" to keyOf("gemini")),
        body.toString(),
        "Gemini",
      )
    val candidates = reply.optJSONArray("candidates")
    val content = candidates?.optJSONObject(0)?.optJSONObject("content")
    val parts = content?.optJSONArray("parts") ?: throw Failure("Gemini: empty answer")
    val sb = StringBuilder()
    for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text")).append('\n')
    return sb.toString()
  }

  private fun openAiLike(provider: String, system: String, text: String): String {
    val base =
      (if (provider == "custom") str("nika_tr_custom_base").trim() else OPENAI_BASES[provider].orEmpty())
        .trimEnd('/')
    val model = modelOf(provider)
    if (base.isEmpty() || model.isEmpty()) throw Failure(service.getString(R.string.nika_tr_no_key))
    val body =
      JSONObject()
        .put("model", model)
        .put(
          "messages",
          JSONArray()
            .put(JSONObject().put("role", "system").put("content", system))
            .put(JSONObject().put("role", "user").put("content", text)),
        )
    val reply =
      post(
        "$base/chat/completions",
        mapOf("content-type" to "application/json", "authorization" to "Bearer " + keyOf(provider)),
        body.toString(),
        provider,
      )
    val message =
      reply.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        ?: throw Failure("$provider: empty answer")
    return message.optString("content")
  }

  private fun vivo(system: String, text: String): String {
    val user = str("nika_tr_nvdacn_user").trim()
    val pass = str("nika_tr_nvdacn_pass").trim()
    val appId = "3046775094"
    val uri = "/vivogpt/completions"
    val requestId = UUID.randomUUID().toString()
    val timestamp = (System.currentTimeMillis() / 1000).toString()
    val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
    val random = SecureRandom()
    val nonce = String(CharArray(8) { chars[random.nextInt(chars.length)] })
    val signed =
      "x-ai-gateway-app-id:$appId\nx-ai-gateway-timestamp:$timestamp\nx-ai-gateway-nonce:$nonce"
    val signing = "POST\n$uri\nrequestId=$requestId\n$appId\n$timestamp\n$signed"
    val signUrl =
      "https://nvdacn.com/api/?user=" + URLEncoder.encode(user, "UTF-8") +
        "&pass=" + URLEncoder.encode(pass, "UTF-8") + "&name=vivo&action=signature"
    val sign = post(signUrl, mapOf("content-type" to "text/plain"), signing, "NVDACN", 15000)
    if (sign.optInt("code") != 200 || !sign.has("data")) {
      throw Failure("NVDACN: " + sign.optString("data", "denied") + " (" + sign.optInt("code") + ")")
    }
    val body =
      JSONObject()
        .put("model", modelOf("vivo"))
        .put("sessionId", UUID.randomUUID().toString())
        .put("provider", "vivo")
        .put(
          "messages",
          JSONArray()
            .put(
              JSONObject()
                .put("role", "user")
                .put("content", system + "\n\n" + text)
                .put("contentType", "text")
            ),
        )
        .put("extra", JSONObject().put("temperature", 0).put("max_tokens", 1024))
    val reply =
      post(
        "https://api-ai.vivo.com.cn$uri?requestId=$requestId",
        mapOf(
          "content-type" to "application/json",
          "X-AI-GATEWAY-APP-ID" to appId,
          "X-AI-GATEWAY-TIMESTAMP" to timestamp,
          "X-AI-GATEWAY-NONCE" to nonce,
          "X-AI-GATEWAY-SIGNED-HEADERS" to "x-ai-gateway-app-id;x-ai-gateway-timestamp;x-ai-gateway-nonce",
          "X-AI-GATEWAY-SIGNATURE" to sign.get("data").toString(),
        ),
        body.toString(),
        "Vivo",
      )
    if (reply.has("code") && reply.optInt("code") != 0) {
      throw Failure("Vivo: " + reply.optString("msg") + " (" + reply.optInt("code") + ")")
    }
    return reply.optJSONObject("data")?.optString("content").orEmpty()
  }

  /** Sends a POST and returns the JSON answer. Failures of the service become [Failure]. */
  private fun post(
    url: String,
    headers: Map<String, String>,
    body: String,
    what: String,
    timeoutMs: Int = 60000,
  ): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "POST"
      connection.connectTimeout = 15000
      connection.readTimeout = timeoutMs
      connection.doOutput = true
      for ((k, v) in headers) connection.setRequestProperty(k, v)
      connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      val status = connection.responseCode
      val stream = if (status in 200..299) connection.inputStream else connection.errorStream
      val raw =
        stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }
          ?: ""
      if (status !in 200..299) {
        var detail = raw.take(200)
        try {
          val err = JSONObject(raw).opt("error")
          if (err is JSONObject) detail = err.optString("message", detail)
          else if (err is String) detail = err
        } catch (e: org.json.JSONException) {
          // keep the raw text
        }
        throw Failure("$what: $status. $detail")
      }
      return try {
        JSONObject(raw)
      } catch (e: org.json.JSONException) {
        throw Failure("$what: " + raw.take(150))
      }
    } finally {
      connection.disconnect()
    }
  }

  // ----- speech -----

  private fun say(text: CharSequence, interrupt: Boolean) {
    try {
      service.speechController.speak(
        text,
        if (interrupt) SpeechController.QUEUE_MODE_FLUSH_ALL else SpeechController.QUEUE_MODE_QUEUE,
        FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
        null,
        Performance.EVENT_ID_UNTRACKED,
      )
    } catch (e: RuntimeException) {
      // skip
    }
  }

  companion object {
    private const val MAX_CHARS = 6000

    private val LANGUAGES =
      mapOf(
        "uk" to "Ukrainian", "en" to "English", "ru" to "Russian", "be" to "Belarusian",
        "pl" to "Polish", "de" to "German", "fr" to "French", "es" to "Spanish", "it" to "Italian",
        "pt" to "Portuguese", "cs" to "Czech", "sk" to "Slovak", "tr" to "Turkish", "ar" to "Arabic",
        "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean",
      )

    private val DEFAULT_MODELS =
      mapOf(
        "claude" to "claude-sonnet-5-5", "gemini" to "gemini-3.8-flash", "openai" to "gpt-5-mini",
        "grok" to "grok-4", "deepseek" to "deepseek-chat", "mistral" to "mistral-large-latest",
        "groq" to "llama-3.3-70b-versatile", "openrouter" to "openrouter/auto",
        "vivo" to "BlueLM-Vision-Aid",
      )

    private val OPENAI_BASES =
      mapOf(
        "openai" to "https://api.openai.com/v1",
        "grok" to "https://api.x.ai/v1",
        "deepseek" to "https://api.deepseek.com",
        "mistral" to "https://api.mistral.ai/v1",
        "groq" to "https://api.groq.com/openai/v1",
        "openrouter" to "https://openrouter.ai/api/v1",
      )

    @Volatile private var instance: NikaTranslator? = null

    @JvmStatic
    @Synchronized
    fun get(service: TalkBackService): NikaTranslator {
      val current = instance
      if (current != null && current.service === service) return current
      val created = NikaTranslator(service)
      instance = created
      return created
    }
  }
}
