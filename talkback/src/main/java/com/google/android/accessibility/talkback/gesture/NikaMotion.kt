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

package com.google.android.accessibility.talkback.gesture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.SharedPreferencesUtils
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Nika: gestures made with the whole phone: one shake, two shakes and three taps on the back. They
 * are found with the accelerometer, which is read only while the screen is on and at least one of
 * these gestures has an action. The back taps are a guess from the acceleration, so they depend on
 * the phone and the sensitivity setting.
 */
class NikaMotion private constructor(private val service: TalkBackService) : SensorEventListener {

  private val handler = Handler(Looper.getMainLooper())
  private val sensors = service.getSystemService(SensorManager::class.java)
  private var listening = false
  private var started = false

  // Slow average of the acceleration, which is gravity.
  private var gx = 0f
  private var gy = 0f
  private var gz = 0f
  private var haveGravity = false

  // Shake: strong moves of the phone close together form one burst.
  private var peakTimes = LongArray(8)
  private var peakCount = 0
  private var lastPeak = 0L
  private var burstOpen = false
  private var burstsSeen = 0
  private var lastShakeEnd = 0L
  private var seriesStart = 0L
  private var longFired = false

  // Back taps.
  private var tapTimes = LongArray(3)
  private var tapCount = 0
  private var lastTap = 0L

  private var lastFired = 0L

  // A slower average of gravity along the screen axis; a big gap to the quick one means the phone is turning.
  private var gzSlow = 0f
  private var turnUntil = 0L

  private val prefListener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (key != null && key.startsWith("nika_motion_")) handler.post { update() }
    }

  private val screenReceiver =
    object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        handler.post { update() }
      }
    }

  private fun prefs(): SharedPreferences = SharedPreferencesUtils.getSharedPreferences(service)

  private fun action(key: String): String? {
    val value = prefs().getString(key, null)
    if (value.isNullOrEmpty() || value == service.getString(R.string.shortcut_value_unassigned)) {
      return null
    }
    return value
  }

  fun start() {
    if (started) return
    started = true
    prefs().registerOnSharedPreferenceChangeListener(prefListener)
    val filter = IntentFilter().apply {
      addAction(Intent.ACTION_SCREEN_ON)
      addAction(Intent.ACTION_SCREEN_OFF)
    }
    service.registerReceiver(screenReceiver, filter)
    update()
  }

  fun stop() {
    if (!started) return
    started = false
    try {
      service.unregisterReceiver(screenReceiver)
    } catch (e: IllegalArgumentException) {
      // already removed
    }
    prefs().unregisterOnSharedPreferenceChangeListener(prefListener)
    handler.removeCallbacksAndMessages(null)
    listen(false)
  }

  /** Reads the sensor only when it is needed. */
  private fun update() {
    val power = service.getSystemService(PowerManager::class.java)
    val wanted =
      started &&
        power?.isInteractive != false &&
        (action(SHAKE1) != null ||
          action(SHAKE2) != null ||
          action(SHAKEL) != null ||
          action(BACK2) != null ||
          action(BACK3) != null)
    listen(wanted)
  }

  private fun listen(on: Boolean) {
    if (on == listening) return
    val manager = sensors ?: return
    try {
      if (on) {
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        haveGravity = false
        peakCount = 0
        tapCount = 0
        burstOpen = false
        burstsSeen = 0
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        listening = true
      } else {
        manager.unregisterListener(this)
        listening = false
        handler.removeCallbacks(singleShake)
        handler.removeCallbacks(tapDone)
      }
    } catch (e: RuntimeException) {
      listening = false
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

  private fun level(): Int =
    when (prefs().getString(SENS, "1")) {
      "0" -> 0
      "2" -> 2
      else -> 1
    }

  override fun onSensorChanged(event: SensorEvent) {
    try {
      val now = SystemClock.elapsedRealtime()
      val x = event.values[0]
      val y = event.values[1]
      val z = event.values[2]
      if (!haveGravity) {
        gx = x; gy = y; gz = z
        gzSlow = z
        turnUntil = now + 1000
        haveGravity = true
        return
      }
      // The average follows slow changes (holding the phone), not quick ones (shakes and taps).
      val k = 0.1f
      gx += k * (x - gx)
      gy += k * (y - gy)
      gz += k * (z - gz)
      gzSlow += 0.02f * (z - gzSlow)
      if (abs(gz - gzSlow) > 2.5f) turnUntil = now + 1200
      val dx = x - gx
      val dy = y - gy
      val dz = z - gz
      val strength = sqrt(dx * dx + dy * dy + dz * dz)
      val lv = level()
      detectShake(now, strength, lv)
      detectBackTap(now, dx, dy, dz, strength, lv)
    } catch (e: RuntimeException) {
      // A sensor reading must never break the screen reader.
    }
  }

  // ----- shake -----

  private fun detectShake(now: Long, strength: Float, lv: Int) {
    val threshold = floatArrayOf(16f, 12f, 8f)[lv]
    if (strength > threshold && now - lastPeak > 70) {
      if (peakCount > 0 && now - lastPeak > 500) peakCount = 0
      if (peakCount == 0) seriesStart = now
      if (peakCount < peakTimes.size) peakTimes[peakCount++] = now
      lastPeak = now
      if (peakCount >= 4) burstOpen = true
    }
    if (burstOpen && !longFired && now - seriesStart >= 1500 && action(SHAKEL) != null) {
      longFired = true
      handler.removeCallbacks(singleShake)
      burstsSeen = 0
      fire(SHAKEL)
    }
    if (burstOpen && now - lastPeak > 350) {
      burstOpen = false
      peakCount = 0
      tapCount = 0
      handler.removeCallbacks(tapDone)
      if (longFired) {
        longFired = false
        lastShakeEnd = now
      } else {
        onBurstEnd(now)
      }
    }
  }

  private val singleShake = Runnable {
    burstsSeen = 0
    fire(SHAKE1)
  }

  private fun onBurstEnd(now: Long) {
    burstsSeen += 1
    if (burstsSeen == 1) {
      if (action(SHAKE2) != null) {
        handler.postDelayed(singleShake, 1000)
      } else {
        burstsSeen = 0
        fire(SHAKE1)
      }
    } else {
      handler.removeCallbacks(singleShake)
      burstsSeen = 0
      fire(SHAKE2)
    }
    lastShakeEnd = now
  }

  // ----- three taps on the back -----

  private fun detectBackTap(now: Long, dx: Float, dy: Float, dz: Float, strength: Float, lv: Int) {
    if (action(BACK2) == null && action(BACK3) == null) return
    // Turning the phone over (for example screen down on the table) gives a push that looks like
    // a tap, so taps are not counted while the phone turns or lies with the screen down.
    if (now < turnUntil || gz < -4f) {
      tapCount = 0
      handler.removeCallbacks(tapDone)
      return
    }
    // A tap on the back is a short push along the axis that goes through the screen.
    val threshold = floatArrayOf(5.5f, 4f, 2.8f)[lv]
    val side = max(abs(dx), abs(dy))
    if (abs(dz) < threshold || abs(dz) < side * 1.6f || strength > 3 * threshold + 12f) return
    if (burstOpen || now - lastShakeEnd < 600) return
    if (now - lastTap < 90) return
    if (tapCount > 0 && now - lastTap > 650) tapCount = 0
    lastTap = now
    tapCount += 1
    handler.removeCallbacks(tapDone)
    if (tapCount >= 3 || !higherAssigned(tapCount)) {
      tapDone.run()
    } else {
      // A longer series may follow, so wait a little before using this one.
      handler.postDelayed(tapDone, 450)
    }
  }

  /** Whether a series with more taps than [count] has an action. */
  private fun higherAssigned(count: Int): Boolean =
    (count < 2 && (action(BACK2) != null || action(BACK3) != null)) ||
      (count < 3 && action(BACK3) != null)

  private val tapDone = Runnable {
    val count = tapCount
    tapCount = 0
    when (count) {
      2 -> fire(BACK2)
      else -> if (count >= 3) fire(BACK3)
    }
  }

  private fun fire(key: String) {
    val now = SystemClock.elapsedRealtime()
    if (now - lastFired < 800) return
    val value = action(key) ?: return
    lastFired = now
    handler.post {
      try {
        service.performNikaKeyAction(value)
      } catch (e: RuntimeException) {
        // skip
      }
    }
  }

  companion object {
    const val SHAKE1 = "nika_motion_shake1"
    const val SHAKE2 = "nika_motion_shake2"
    const val SHAKEL = "nika_motion_shakel"
    const val BACK2 = "nika_motion_back2"
    const val BACK3 = "nika_motion_back3"
    const val SENS = "nika_motion_sens"

    @Volatile private var instance: NikaMotion? = null

    @JvmStatic
    @Synchronized
    fun get(service: TalkBackService): NikaMotion {
      val current = instance
      if (current != null && current.service === service) return current
      current?.stop()
      val created = NikaMotion(service)
      instance = created
      return created
    }
  }
}
