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

package com.google.android.accessibility.talkback.soundthemes

import android.content.Context
import com.google.android.accessibility.utils.SharedPreferencesUtils
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Nika: installs the sound theme "Nika iPhone" from the app's assets and, when the person has not
 * chosen another theme, makes it the sound theme in use. It happens once per version of the theme,
 * so a person who goes back to another theme is not overruled again.
 */
object NikaDefaultTheme {
  private const val ASSET = "nika-iphone-theme.zip"
  private const val PREFS = "nika_builtin_scripts"
  private const val KEY = "default_sound_theme_version"
  private const val VERSION = "1"
  private val running = AtomicBoolean(false)

  @JvmStatic
  fun ensureInstalled(context: Context) {
    val done = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (done.getString(KEY, null) == VERSION) return
    if (!running.compareAndSet(false, true)) return
    Thread {
        try {
          val file = File(context.cacheDir, "nika-iphone-theme.zip")
          context.assets.open(ASSET).use { input ->
            file.outputStream().use { input.copyTo(it) }
          }
          val prefs = SharedPreferencesUtils.getSharedPreferences(context)
          val useIt = SoundThemes.activeId(prefs) == SoundThemes.BACKTALK
          val staged = SoundThemes.stage(context, file, "Nika iPhone")
          SoundThemes.install(context, prefs, staged, useIt)
          file.delete()
          done.edit().putString(KEY, VERSION).apply()
        } catch (e: Exception) {
          // The screen reader works with its own sounds if the theme cannot be installed.
        } finally {
          running.set(false)
        }
      }
      .start()
  }
}
