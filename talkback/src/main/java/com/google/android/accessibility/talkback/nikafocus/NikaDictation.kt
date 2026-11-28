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

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.accessibility.AccessibilityNodeInfo
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.talkback.macros.NikaVoiceCmds
import com.google.android.accessibility.utils.NikaDiag
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/**
 * Nika: voice typing into the text field with the speech recognizer of Google. It does not need the
 * keyboard of Google. The first press starts listening, the second one stops it. The words that were
 * heard are put into the field at the cursor.
 */
object NikaDictation {
  const val VALUE = "nika_dictate"
  private val handler = Handler(Looper.getMainLooper())
  private var recognizer: SpeechRecognizer? = null
  private var listening = false
  private var started = false

  @JvmStatic fun isAction(value: String?): Boolean = value == VALUE

  @JvmStatic
  fun perform(service: TalkBackService): Boolean {
    try {
      if (listening) {
        stop()
        return true
      }
      val node = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
      if (node == null || !node.isEditable) {
        say(service, service.getString(R.string.nika_dict_nofield))
        return true
      }
      if (!SpeechRecognizer.isRecognitionAvailable(service)) {
        say(service, service.getString(R.string.nika_dict_nospeech))
        return true
      }
      val r = SpeechRecognizer.createSpeechRecognizer(service)
      recognizer = r
      listening = true
      started = false
      r.setRecognitionListener(
        object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {}

          override fun onBeginningOfSpeech() {}

          override fun onRmsChanged(rmsdB: Float) {}

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {}

          override fun onPartialResults(partialResults: Bundle?) {}

          override fun onEvent(eventType: Int, params: Bundle?) {}

          override fun onError(error: Int) {
            NikaDiag.log("dictate", "error $error")
            cleanup()
            say(service, service.getString(R.string.nika_dict_fail))
          }

          override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim() ?: ""
            cleanup()
            if (text.isEmpty()) {
              say(service, service.getString(R.string.nika_dict_fail))
            } else {
              insert(service, text)
            }
          }
        }
      )
      val intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
          .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
      val lang = NikaVoiceCmds.language(service)
      if (lang.isNotBlank()) intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
      started = true
      r.startListening(intent)
    } catch (e: RuntimeException) {
      NikaDiag.log("dictate", "failed: $e")
      cleanup()
    }
    return true
  }

  private fun stop() {
    val r = recognizer
    if (r != null && started) {
      r.stopListening()
    } else {
      cleanup()
    }
  }

  private fun cleanup() {
    listening = false
    started = false
    try {
      recognizer?.destroy()
    } catch (e: RuntimeException) {
      // skip
    }
    recognizer = null
  }

  private fun insert(service: TalkBackService, text: String) {
    val node = service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    if (node == null || !node.isEditable) {
      say(service, service.getString(R.string.nika_dict_nofield))
      return
    }
    val old = if (node.isShowingHintText) "" else node.text?.toString() ?: ""
    var a = node.textSelectionStart
    var b = node.textSelectionEnd
    if (a < 0 || b < 0) {
      a = old.length
      b = old.length
    }
    val s = minOf(a, b).coerceIn(0, old.length)
    val e = maxOf(a, b).coerceIn(0, old.length)
    val sep = if (s > 0 && !old[s - 1].isWhitespace()) " " else ""
    val ins = sep + text
    val args = Bundle()
    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, old.substring(0, s) + ins + old.substring(e))
    if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
      val sel = Bundle()
      sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, s + ins.length)
      sel.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, s + ins.length)
      node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, sel)
      say(service, service.getString(R.string.nika_dict_done, text))
    } else {
      say(service, service.getString(R.string.nika_dict_fail))
    }
  }

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
}
