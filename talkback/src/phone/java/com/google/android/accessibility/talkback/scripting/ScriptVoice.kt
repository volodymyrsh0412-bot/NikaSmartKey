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

package com.google.android.accessibility.talkback.scripting

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.SpeechRecognizer
import com.google.android.accessibility.talkback.utils.SpeechRecognizerPerformer
import com.google.android.accessibility.talkback.utils.SpeechRecognizerPerformer.SpeechRecognizerRequester

/**
 * Nika: speech recognition for scripts, behind backtalk.voice.listen. It listens once, with the same
 * recognizer and the same microphone permission request that TalkBack's voice commands use.
 */
class ScriptVoice(private val context: Context) {
  private val handler = Handler(Looper.getMainLooper())
  private var performer: SpeechRecognizerPerformer? = null
  private var pending: ((Boolean, String) -> Unit)? = null
  private var timeout: Runnable? = null

  /**
   * Listens once. [done] gets true and the text (empty if nothing was heard), or false and a
   * message that says what went wrong.
   */
  fun listen(seconds: Int, done: (Boolean, String) -> Unit) {
    handler.post {
      if (pending != null) {
        done(false, "Voice input is already listening")
        return@post
      }
      pending = done
      arm(seconds * 1000L + 3000L)
      val recognizer = SpeechRecognizerPerformer(context, seconds * 1000, false)
      performer = recognizer
      recognizer.setListener(
        object : SpeechRecognizerRequester {
          override fun onResult(command: String?, isPartialResult: Boolean): Boolean {
            if (!isPartialResult) {
              finish(true, command.orEmpty())
            }
            return false
          }

          override fun onFeedbackEvent(eventId: Int) {
            when (eventId) {
              SpeechRecognizerRequester.EVENT_PLATFORM_NOT_SUPPORT,
              SpeechRecognizerRequester.EVENT_FEATURE_UNAVAILABLE ->
                finish(false, "Speech recognition is not available on this phone")
              SpeechRecognizerRequester.EVENT_OPERATION_FORBIDDEN_DURING_SUW ->
                finish(false, "Speech recognition is not allowed during the phone setup")
              SpeechRecognizerRequester.EVENT_MIC_PERMISSION_NOT_GRANTED ->
                finish(false, "The microphone permission was not granted")
              // The person answers the permission question, which can take a while.
              SpeechRecognizerRequester.EVENT_MIC_PERMISSION_REQUESTED -> arm(60_000L)
              else -> {}
            }
          }

          override fun onError(error: Int) {
            if (
              error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
              finish(true, "")
            } else {
              finish(false, "Speech recognition failed, error $error")
            }
          }
        }
      )
      recognizer.doListening()
    }
  }

  /** Stops listening, for example when the script is unloaded. */
  fun cancel() {
    handler.post { finish(false, "Voice input was cancelled") }
  }

  private fun arm(delayMs: Long) {
    timeout?.let(handler::removeCallbacks)
    val timer = Runnable { finish(true, "") }
    timeout = timer
    handler.postDelayed(timer, delayMs)
  }

  private fun finish(ok: Boolean, value: String) {
    val done = pending ?: return
    pending = null
    timeout?.let(handler::removeCallbacks)
    timeout = null
    try {
      performer?.reset()
    } catch (e: RuntimeException) {
      // The recognizer is gone already.
    }
    performer = null
    done(ok, value)
  }
}
