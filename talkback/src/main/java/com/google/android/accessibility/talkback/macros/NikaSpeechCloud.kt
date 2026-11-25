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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Nika: speech recognition through online services (Groq, OpenAI, Gemini, Deepgram). The sound is
 * recorded here and sent as a small WAV file. Every call blocks, so call them off the main thread.
 */
object NikaSpeechCloud {
  private const val SAMPLE = 16000

  /** Records until a pause after speech, or [maxMs]. Null if nothing was said or no permission. */
  fun recordWav(context: Context, maxMs: Int): ByteArray? {
    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      throw IOException("no microphone permission")
    }
    val min = AudioRecord.getMinBufferSize(SAMPLE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
    if (min <= 0) throw IOException("no microphone")
    val record =
      AudioRecord(
        MediaRecorder.AudioSource.MIC,
        SAMPLE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        maxOf(min, SAMPLE),
      )
    if (record.state != AudioRecord.STATE_INITIALIZED) {
      record.release()
      throw IOException("the microphone is busy")
    }
    val pcm = ByteArrayOutputStream()
    val buf = ShortArray(SAMPLE / 50)
    var started = false
    var silentMs = 0
    var totalMs = 0
    var noise = 0.0
    var noiseCount = 0
    try {
      record.startRecording()
      while (totalMs < maxMs) {
        val n = record.read(buf, 0, buf.size)
        if (n <= 0) break
        totalMs += n * 1000 / SAMPLE
        var sum = 0.0
        for (i in 0 until n) sum += buf[i].toDouble() * buf[i]
        val rms = Math.sqrt(sum / n)
        if (totalMs <= 300) {
          noise += rms
          noiseCount++
        }
        val threshold = maxOf(500.0, 3.0 * if (noiseCount > 0) noise / noiseCount else 0.0)
        if (rms > threshold) {
          started = true
          silentMs = 0
        } else if (started) {
          silentMs += n * 1000 / SAMPLE
        }
        if (started || totalMs <= 300) {
          for (i in 0 until n) {
            pcm.write(buf[i].toInt() and 0xff)
            pcm.write((buf[i].toInt() shr 8) and 0xff)
          }
        }
        if (started && silentMs >= 1100) break
        if (!started && totalMs >= 5000) break
      }
    } finally {
      try {
        record.stop()
      } catch (e: RuntimeException) {
        // skip
      }
      record.release()
    }
    if (!started) return null
    return wav(pcm.toByteArray())
  }

  private fun wav(pcm: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    fun int(v: Int) {
      out.write(v and 0xff)
      out.write((v shr 8) and 0xff)
      out.write((v shr 16) and 0xff)
      out.write((v shr 24) and 0xff)
    }
    fun short(v: Int) {
      out.write(v and 0xff)
      out.write((v shr 8) and 0xff)
    }
    out.write("RIFF".toByteArray())
    int(36 + pcm.size)
    out.write("WAVEfmt ".toByteArray())
    int(16)
    short(1)
    short(1)
    int(SAMPLE)
    int(SAMPLE * 2)
    short(2)
    short(16)
    out.write("data".toByteArray())
    int(pcm.size)
    out.write(pcm)
    return out.toByteArray()
  }

  /** The text said in [wav]. [lang] is a code such as "uk"; [model] may be empty. */
  fun transcribe(engine: String, key: String, model: String, lang: String, wav: ByteArray): String =
    when (engine) {
      NikaVoiceEngines.GROQ ->
        openAiLike("https://api.groq.com/openai/v1/audio/transcriptions", key, model.ifEmpty { "whisper-large-v3-turbo" }, lang, wav)
      NikaVoiceEngines.OPENAI ->
        openAiLike("https://api.openai.com/v1/audio/transcriptions", key, model.ifEmpty { "whisper-1" }, lang, wav)
      NikaVoiceEngines.GEMINI -> gemini(key, model.ifEmpty { "gemini-2.5-flash" }, lang, wav)
      NikaVoiceEngines.DEEPGRAM -> deepgram(key, model.ifEmpty { "nova-2" }, lang, wav)
      else -> throw IOException("unknown engine")
    }

  private fun openAiLike(url: String, key: String, model: String, lang: String, wav: ByteArray): String {
    val boundary = "----nika" + System.currentTimeMillis()
    val conn = URL(url).openConnection() as HttpURLConnection
    try {
      conn.requestMethod = "POST"
      conn.doOutput = true
      conn.connectTimeout = 15000
      conn.readTimeout = 40000
      conn.setRequestProperty("Authorization", "Bearer $key")
      conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
      conn.outputStream.use { out ->
        val fields = linkedMapOf("model" to model, "response_format" to "json")
        if (lang.isNotEmpty()) fields["language"] = lang
        for ((k, v) in fields) {
          out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n").toByteArray())
        }
        out.write(
          ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"nika.wav\"\r\n" +
              "Content-Type: audio/wav\r\n\r\n")
            .toByteArray()
        )
        out.write(wav)
        out.write("\r\n--$boundary--\r\n".toByteArray())
      }
      return JSONObject(read(conn)).optString("text", "").trim()
    } finally {
      conn.disconnect()
    }
  }

  private fun gemini(key: String, model: String, lang: String, wav: ByteArray): String {
    val conn =
      URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
        .openConnection() as HttpURLConnection
    try {
      conn.requestMethod = "POST"
      conn.doOutput = true
      conn.connectTimeout = 15000
      conn.readTimeout = 40000
      conn.setRequestProperty("Content-Type", "application/json")
      val prompt =
        "Transcribe this short voice command exactly as spoken" +
          (if (lang.isNotEmpty()) " (language code: $lang)" else "") +
          ". Answer with the words only, no comments."
      val body =
        JSONObject()
          .put(
            "contents",
            JSONArray()
              .put(
                JSONObject()
                  .put(
                    "parts",
                    JSONArray()
                      .put(JSONObject().put("text", prompt))
                      .put(
                        JSONObject()
                          .put(
                            "inline_data",
                            JSONObject()
                              .put("mime_type", "audio/wav")
                              .put("data", Base64.encodeToString(wav, Base64.NO_WRAP)),
                          )
                      ),
                  )
              ),
          )
      conn.outputStream.use { it.write(body.toString().toByteArray()) }
      val parts =
        JSONObject(read(conn))
          .getJSONArray("candidates")
          .getJSONObject(0)
          .getJSONObject("content")
          .getJSONArray("parts")
      return parts.getJSONObject(0).optString("text", "").trim()
    } finally {
      conn.disconnect()
    }
  }

  private fun deepgram(key: String, model: String, lang: String, wav: ByteArray): String {
    val query = "model=$model&smart_format=true" + (if (lang.isNotEmpty()) "&language=$lang" else "")
    val conn = URL("https://api.deepgram.com/v1/listen?$query").openConnection() as HttpURLConnection
    try {
      conn.requestMethod = "POST"
      conn.doOutput = true
      conn.connectTimeout = 15000
      conn.readTimeout = 40000
      conn.setRequestProperty("Authorization", "Token $key")
      conn.setRequestProperty("Content-Type", "audio/wav")
      conn.outputStream.use { it.write(wav) }
      return JSONObject(read(conn))
        .getJSONObject("results")
        .getJSONArray("channels")
        .getJSONObject(0)
        .getJSONArray("alternatives")
        .getJSONObject(0)
        .optString("transcript", "")
        .trim()
    } finally {
      conn.disconnect()
    }
  }

  private fun read(conn: HttpURLConnection): String {
    val code = conn.responseCode
    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
    val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
    if (code !in 200..299) throw IOException("HTTP $code " + text.take(160))
    return text
  }
}
