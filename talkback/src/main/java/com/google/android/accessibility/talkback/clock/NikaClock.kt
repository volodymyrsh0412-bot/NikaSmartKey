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

package com.google.android.accessibility.talkback.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.preference.PreferenceManager
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController
import java.util.Calendar
import java.util.Locale

/**
 * Nika: the talking clock (speaks the time on a schedule, with quiet hours, also when the screen is
 * off), a countdown timer and a stopwatch. The alarms are set with AlarmManager, so they work while
 * the phone sleeps. The actions are assigned to gestures through [NikaClockActions].
 */
class NikaClock private constructor(private val service: TalkBackService) {

  private val handler = Handler(Looper.getMainLooper())
  private val alarmManager: AlarmManager? = service.getSystemService(AlarmManager::class.java)
  private var started = false

  // Stopwatch: the time that has already run, and when the current run began (0 = paused).
  private var swAccumulated = 0L
  private var swStartedAt = 0L

  // Timer: when it ends (elapsedRealtime), 0 = not running.
  private var timerEndAt = 0L
  private var timerLength = 0L
  private var ringsLeft = 0

  // Spoken progress (every few seconds) of the stopwatch and of the timer.
  private var progressLock: PowerManager.WakeLock? = null
  private var lastSwSpoken = -1L
  private var lastTimerSpoken = -1L

  private val receiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        try {
          when (intent?.action) {
            ACTION_CLOCK -> onClockAlarm()
            ACTION_TIMER -> onTimerAlarm()
          }
        } catch (e: RuntimeException) {
          // The clock must never break the screen reader.
        }
      }
    }

  // The listener must be kept in a field: preferences hold it only weakly.
  private val prefListener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (key != null && key.startsWith("nika_clock_")) {
        handler.post {
          scheduleClock()
          scheduleProgress()
        }
      }
    }

  fun start() {
    com.google.android.accessibility.talkback.soundthemes.NikaDefaultTheme.ensureInstalled(service)
    com.google.android.accessibility.talkback.gesture.NikaMotion.get(service).start()
    if (started) return
    started = true
    val filter = IntentFilter().apply {
      addAction(ACTION_CLOCK)
      addAction(ACTION_TIMER)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      service.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      service.registerReceiver(receiver, filter)
    }
    for (store in stores()) store.registerOnSharedPreferenceChangeListener(prefListener)
    scheduleClock()
  }

  fun stop() {
    com.google.android.accessibility.talkback.gesture.NikaMotion.get(service).stop()
    if (!started) return
    started = false
    try {
      service.unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
      // already removed
    }
    for (store in stores()) store.unregisterOnSharedPreferenceChangeListener(prefListener)
    handler.removeCallbacksAndMessages(null)
    releaseProgressLock()
    cancel(pending(ACTION_CLOCK))
    cancel(pending(ACTION_TIMER))
  }

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

  private fun str(key: String, default: String): String {
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

  private fun int(key: String, default: Int): Int = str(key, default.toString()).toIntOrNull() ?: default

  // ----- alarms -----

  private fun pending(action: String): PendingIntent =
    PendingIntent.getBroadcast(
      service,
      if (action == ACTION_CLOCK) 1 else 2,
      Intent(action).setPackage(service.packageName),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

  private fun cancel(pi: PendingIntent) {
    alarmManager?.cancel(pi)
  }

  private fun setAlarm(type: Int, at: Long, pi: PendingIntent) {
    val am = alarmManager ?: return
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
        am.setAndAllowWhileIdle(type, at, pi)
      } else {
        am.setExactAndAllowWhileIdle(type, at, pi)
      }
    } catch (e: SecurityException) {
      am.setAndAllowWhileIdle(type, at, pi)
    }
  }

  // ----- talking clock -----

  private fun interval(): Int {
    val v = int("nika_clock_interval", 30)
    return if (v in 1..60 && 60 % v == 0) v else 30
  }

  private var clockTarget = 0L
  private var lastSpokenTarget = 0L

  private val clockTask = Runnable { fireClock() }

  private fun scheduleClock() {
    cancel(pending(ACTION_CLOCK))
    handler.removeCallbacks(clockTask)
    clockTarget = 0L
    if (!bool("nika_clock_enabled", true)) return
    val step = interval()
    val now = System.currentTimeMillis()
    val cal = Calendar.getInstance()
    // Start from a moment 20 s later, so a tick that comes a little early is not scheduled again.
    cal.timeInMillis = now + 20_000L
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.add(Calendar.MINUTE, step - (cal.get(Calendar.MINUTE) % step))
    clockTarget = cal.timeInMillis
    setAlarm(AlarmManager.RTC_WAKEUP, clockTarget, pending(ACTION_CLOCK))
    // While the phone is awake this is exact; the alarm covers the sleeping phone.
    handler.postDelayed(clockTask, (clockTarget - now).coerceAtLeast(0L))
  }

  private fun inQuiet(hour: Int): Boolean {
    if (!bool("nika_clock_quiet", true)) return false
    val from = int("nika_clock_quiet_from", 23)
    val to = int("nika_clock_quiet_to", 7)
    if (from == to) return false
    return if (from < to) hour in from until to else hour >= from || hour < to
  }

  private fun onClockAlarm() = fireClock()

  private fun fireClock() {
    val target = clockTarget
    if (target == 0L) return
    val now = System.currentTimeMillis()
    if (now < target - 20_000L) {
      // The system woke us too early: wait for the real time.
      scheduleClock()
      return
    }
    if (target == lastSpokenTarget) {
      scheduleClock()
      return
    }
    lastSpokenTarget = target
    val cal = Calendar.getInstance()
    // Up to a minute late is still the scheduled minute; later than that says the real time.
    cal.timeInMillis = if (now - target <= 60_000L) target else now
    if (!inQuiet(cal.get(Calendar.HOUR_OF_DAY))) {
      speak(timeAndBattery(cal), false)
    }
    scheduleClock()
  }

  private fun timeText(hour: Int, minute: Int): String {
    if (minute == 0) {
      if (hour == 0) return service.getString(R.string.nika_clock_midnight)
      return service.resources.getQuantityString(R.plurals.nika_clock_hours, hour, hour)
    }
    return String.format(Locale.ROOT, "%d:%02d", hour, minute)
  }

  private fun batteryText(): String? {
    if (!bool("nika_clock_battery", true)) return null
    return try {
      val bm = service.getSystemService(BatteryManager::class.java)
      val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: return null
      if (level <= 0 || level > 100) return null
      var text = service.getString(R.string.nika_clock_battery, level)
      val status =
        service.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
          ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
      if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
        text += ", " + service.getString(R.string.nika_clock_charging)
      }
      text
    } catch (e: RuntimeException) {
      null
    }
  }

  private fun timeAndBattery(cal: Calendar): String {
    val parts = ArrayList<String>(2)
    parts.add(timeText(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)))
    batteryText()?.let { parts.add(it) }
    return parts.joinToString(". ")
  }

  /** Says the time and the battery now (quiet hours do not apply). */
  fun sayTime() {
    speak(timeAndBattery(Calendar.getInstance()), true)
  }

  // ----- stopwatch -----

  private fun stopwatchElapsed(): Long =
    swAccumulated + if (swStartedAt != 0L) SystemClock.elapsedRealtime() - swStartedAt else 0L

  fun stopwatchToggle() {
    if (swStartedAt != 0L) {
      swAccumulated = stopwatchElapsed()
      swStartedAt = 0L
      speak(
        service.getString(R.string.nika_sw_paused) + ". " + durationText(swAccumulated),
        true,
      )
    } else {
      val fresh = swAccumulated == 0L
      swStartedAt = SystemClock.elapsedRealtime()
      speak(
        service.getString(if (fresh) R.string.nika_sw_started else R.string.nika_sw_resumed),
        true,
      )
    }
    lastSwSpoken = -1L
    scheduleProgress()
  }

  fun stopwatchSay() {
    val elapsed = stopwatchElapsed()
    if (elapsed == 0L && swStartedAt == 0L) {
      speak(service.getString(R.string.nika_sw_zero), true)
    } else {
      speak(durationText(elapsed), true)
    }
  }

  fun stopwatchReset() {
    swAccumulated = 0L
    swStartedAt = 0L
    speak(service.getString(R.string.nika_sw_reset_done), true)
    lastSwSpoken = -1L
    scheduleProgress()
  }

  // ----- countdown timer -----

  fun timerToggle() {
    if (timerEndAt != 0L) {
      stopTimer()
      speak(service.getString(R.string.nika_timer_stopped), true)
      scheduleProgress()
      return
    }
    val minutes = int("nika_clock_timer_minutes", 5).coerceIn(1, 600)
    timerLength = minutes * 60_000L
    timerEndAt = SystemClock.elapsedRealtime() + timerLength
    ringsLeft = 0
    setAlarm(AlarmManager.ELAPSED_REALTIME_WAKEUP, timerEndAt, pending(ACTION_TIMER))
    speak(service.getString(R.string.nika_timer_started, durationText(timerLength)), true)
    lastTimerSpoken = -1L
    scheduleProgress()
  }

  fun timerSay() {
    if (timerEndAt == 0L) {
      speak(service.getString(R.string.nika_timer_idle), true)
      return
    }
    val left = (timerEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    speak(service.getString(R.string.nika_timer_left, durationText(left)), true)
  }

  private fun stopTimer() {
    timerEndAt = 0L
    ringsLeft = 0
    cancel(pending(ACTION_TIMER))
    handler.removeCallbacks(ringTask)
  }

  private val ringTask =
    object : Runnable {
      override fun run() {
        if (ringsLeft <= 0) return
        ringsLeft--
        ring()
        if (ringsLeft > 0) handler.postDelayed(this, 5_000L)
      }
    }

  private fun onTimerAlarm() {
    if (timerEndAt == 0L) return
    timerEndAt = 0L
    scheduleProgress()
    val power = service.getSystemService(PowerManager::class.java)
    try {
      power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nika:timer")?.acquire(20_000L)
    } catch (e: RuntimeException) {
      // skip
    }
    ringsLeft = 3
    handler.removeCallbacks(ringTask)
    handler.post(ringTask)
  }

  private fun ring() {
    try {
      ToneGenerator(AudioManager.STREAM_ALARM, 100).startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
    } catch (e: RuntimeException) {
      // skip
    }
    try {
      val vibrator = service.getSystemService(Vibrator::class.java)
      vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
    } catch (e: RuntimeException) {
      // skip
    }
    speak(service.getString(R.string.nika_timer_done), true)
  }

  // ----- spoken progress -----

  private val progressTask = Runnable { progressTick() }

  private fun releaseProgressLock() {
    try {
      progressLock?.let { if (it.isHeld) it.release() }
    } catch (e: RuntimeException) {
      // skip
    }
  }

  /** Plans the next spoken progress. While it waits, the phone stays awake, also when locked. */
  private fun scheduleProgress() {
    handler.removeCallbacks(progressTask)
    val swStep = if (swStartedAt != 0L) int("nika_clock_sw_progress", 0) * 1000L else 0L
    val timerStep = if (timerEndAt != 0L) int("nika_clock_timer_progress", 0) * 1000L else 0L
    var next = Long.MAX_VALUE
    if (swStep > 0L) next = minOf(next, swStep - stopwatchElapsed() % swStep)
    if (timerStep > 0L) {
      val left = (timerEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
      val rest = left % timerStep
      next = minOf(next, if (rest == 0L) timerStep else rest)
    }
    if (next == Long.MAX_VALUE) {
      releaseProgressLock()
      return
    }
    try {
      if (progressLock == null) {
        progressLock =
          service.getSystemService(PowerManager::class.java)?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "nika:progress",
          )
        progressLock?.setReferenceCounted(false)
      }
      progressLock?.acquire(next + 5_000L)
    } catch (e: RuntimeException) {
      // skip
    }
    handler.postDelayed(progressTask, next.coerceAtLeast(50L))
  }

  private fun progressTick() {
    try {
      val swStep = int("nika_clock_sw_progress", 0) * 1000L
      if (swStartedAt != 0L && swStep > 0L) {
        val elapsed = stopwatchElapsed()
        val rounded = (elapsed + swStep / 2) / swStep * swStep
        if (rounded > 0L && Math.abs(elapsed - rounded) < 400L && rounded != lastSwSpoken) {
          lastSwSpoken = rounded
          speak(durationText(rounded), false)
        }
      }
      val timerStep = int("nika_clock_timer_progress", 0) * 1000L
      if (timerEndAt != 0L && timerStep > 0L) {
        val left = (timerEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val rounded = (left + timerStep / 2) / timerStep * timerStep
        if (rounded > 0L && Math.abs(left - rounded) < 400L && rounded != lastTimerSpoken) {
          lastTimerSpoken = rounded
          speak(durationText(rounded), false)
        }
      }
    } catch (e: RuntimeException) {
      // The clock must never break the screen reader.
    }
    scheduleProgress()
  }

  // ----- speech -----

  /** "1 h 5 min 3 s" in the language of the phone. */
  private fun durationText(ms: Long): String {
    val total = ms / 1000
    val h = (total / 3600).toInt()
    val m = ((total % 3600) / 60).toInt()
    val s = (total % 60).toInt()
    val parts = ArrayList<String>(3)
    if (h > 0) parts.add(service.getString(R.string.nika_dur_h, h))
    if (m > 0) parts.add(service.getString(R.string.nika_dur_m, m))
    if (s > 0 || parts.isEmpty()) parts.add(service.getString(R.string.nika_dur_s, s))
    return parts.joinToString(" ")
  }

  private fun speak(text: String, interrupt: Boolean) {
    if (com.google.android.accessibility.talkback.macros.NikaSideVoice.speak(service, "clock", text, interrupt)) return
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
    private const val ACTION_CLOCK = "ua.nika.app.CLOCK_TICK"
    private const val ACTION_TIMER = "ua.nika.app.TIMER_DONE"

    @Volatile private var instance: NikaClock? = null

    @JvmStatic
    @Synchronized
    fun get(service: TalkBackService): NikaClock {
      val current = instance
      if (current != null && current.service === service) return current
      current?.stop()
      val created = NikaClock(service)
      instance = created
      return created
    }

    @JvmStatic
    @Synchronized
    fun shutdown() {
      instance?.stop()
      instance = null
    }
  }
}
