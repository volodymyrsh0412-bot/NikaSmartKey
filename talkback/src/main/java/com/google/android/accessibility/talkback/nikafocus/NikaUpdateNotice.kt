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

import android.os.Handler
import android.os.Looper
import com.google.android.accessibility.talkback.BuildConfig
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController

/** Nika: after an update, says the new version once. */
object NikaUpdateNotice {
  private const val KEY = "nika_last_build"
  private var done = false

  @JvmStatic
  fun check(service: TalkBackService) {
    if (done) return
    done = true
    Handler(Looper.getMainLooper())
      .postDelayed(
        {
          try {
            say(service)
          } catch (e: RuntimeException) {
            // skip
          }
        },
        4000L,
      )
  }

  private fun say(service: TalkBackService) {
    val prefs = SharedPreferencesUtils.getSharedPreferences(service)
    val now = BuildConfig.COMMIT_COUNT
    val last = prefs.getInt(KEY, 0)
    prefs.edit().putInt(KEY, now).apply()
    if (last == 0 || last == now) return
    val name = service.applicationInfo.loadLabel(service.packageManager).toString()
    service.speechController.speak(
      service.getString(R.string.nika_updated_notice, name, now),
      SpeechController.QUEUE_MODE_QUEUE,
      FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
      null,
      Performance.EVENT_ID_UNTRACKED,
    )
  }
}
