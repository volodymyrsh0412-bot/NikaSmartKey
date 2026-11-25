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
package com.google.android.accessibility.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.content.ContentValues
import android.view.accessibility.AccessibilityEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Nika: a diary of what the screen reader does (events, speech, vibration, gestures, errors). It
 * is kept in a file of the app and can be saved to the folder Download/Nika to be sent for a look.
 */
object NikaDiag {
  const val PREF_ON = "nika_diag_on"
  private const val MAX_BYTES = 1_500_000L

  private val executor = Executors.newSingleThreadExecutor()
  private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

  @Volatile private var appContext: Context? = null
  @Volatile private var on = true
  @Volatile private var checkedAt = 0L
  @Volatile private var lastContentEvent = 0L
  private var crashHooked = false

  @JvmStatic
  fun init(context: Context) {
    if (appContext == null) appContext = context.applicationContext ?: context
    val ctx = appContext ?: return
    if (!crashHooked) {
      crashHooked = true
      try {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
          try {
            writeNow("CRASH", "thread " + t.name + ": " + android.util.Log.getStackTraceString(e))
          } catch (x: Throwable) {
            // nothing more can be done
          }
          previous?.uncaughtException(t, e)
        }
      } catch (e: RuntimeException) {
        // skip
      }
      log("start", "Nika diary on. " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")")
    }
    refresh(ctx)
  }

  private fun refresh(ctx: Context) {
    val now = SystemClock.elapsedRealtime()
    if (now - checkedAt < 2000) return
    checkedAt = now
    try {
      on = SharedPreferencesUtils.getSharedPreferences(ctx).getBoolean(PREF_ON, true)
    } catch (e: RuntimeException) {
      on = true
    }
  }

  private fun logFile(): File? = appContext?.let { File(it.filesDir, "nika-diary.txt") }

  @JvmStatic
  fun log(tag: String, message: String) {
    val ctx = appContext ?: return
    refresh(ctx)
    if (!on) return
    val line = stamp.format(Date()) + " [" + tag + "] " + message.replace("\n", " | ")
    executor.execute { append(line) }
  }

  private fun writeNow(tag: String, message: String) {
    append(stamp.format(Date()) + " [" + tag + "] " + message.replace("\n", " | "))
  }

  @Synchronized
  private fun append(line: String) {
    try {
      val f = logFile() ?: return
      if (f.length() > MAX_BYTES) {
        val old = File(f.parentFile, "nika-diary-old.txt")
        old.delete()
        f.renameTo(old)
      }
      f.appendText(line + "\n")
    } catch (e: Exception) {
      // a diary never breaks the screen reader
    }
  }

  /** Writes down an accessibility event the screen reader got. */
  @JvmStatic
  fun event(context: Context, e: AccessibilityEvent?) {
    if (e == null) return
    init(context)
    if (!on) return
    try {
      val type = e.eventType
      if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastContentEvent < 1000) return
        lastContentEvent = now
      }
      val sb = StringBuilder(AccessibilityEvent.eventTypeToString(type))
      sb.append(' ').append(e.packageName ?: "?").append(' ').append(e.className ?: "?")
      if (!e.isPassword) {
        val text = e.text?.joinToString(" ") ?: ""
        if (text.isNotEmpty()) sb.append(" text=\"").append(text.take(60)).append('"')
        val d = e.contentDescription
        if (!d.isNullOrEmpty()) sb.append(" desc=\"").append(d.toString().take(60)).append('"')
      }
      if (type == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
        sb.append(" sel=").append(e.fromIndex).append("..").append(e.toIndex)
      }
      log("event", sb.toString())
    } catch (x: RuntimeException) {
      // skip
    }
  }

  /** Writes down a vibration with the few calls that led to it. */
  @JvmStatic
  fun vibration(what: String) {
    if (appContext == null || !on) return
    val frames =
      Throwable().stackTrace
        .filter { it.className.contains("accessibility") && !it.className.contains("NikaDiag") }
        .drop(1)
        .take(4)
        .joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber }
    log("vibrate", what + " from " + frames)
  }

  @JvmStatic
  fun size(): Long = logFile()?.length() ?: 0L

  @JvmStatic
  fun clear() {
    executor.execute {
      try {
        logFile()?.delete()
        File(logFile()?.parentFile, "nika-diary-old.txt").delete()
      } catch (e: Exception) {
        // skip
      }
    }
  }

  /** The whole diary: the old part first. */
  private fun readAll(): String {
    val f = logFile() ?: return ""
    val old = File(f.parentFile, "nika-diary-old.txt")
    return (if (old.exists()) old.readText() else "") + (if (f.exists()) f.readText() else "")
  }

  /** Saves the diary to Download/Nika. Returns the file name, or null if it did not work. */
  @JvmStatic
  fun saveToDownloads(context: Context): String? {
    return try {
      val text = readAll()
      if (text.isEmpty()) return null
      val name = "nika-diary-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".txt"
      if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues()
        values.put(MediaStore.Downloads.DISPLAY_NAME, name)
        values.put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Nika")
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } ?: return null
      } else {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Nika")
        dir.mkdirs()
        File(dir, name).writeText(text)
      }
      name
    } catch (e: Exception) {
      null
    }
  }
}
