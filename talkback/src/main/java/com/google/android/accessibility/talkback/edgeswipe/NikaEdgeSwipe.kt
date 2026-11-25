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

package com.google.android.accessibility.talkback.edgeswipe

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Nika: one-finger swipes from the edges of the screen.
 *
 * Two independent groups, each with its own switch, sounds and edge band widths:
 * - "iPhone" gestures from the bottom and top edges (vertical swipes), with separate actions for
 *   the left and the right half of the screen;
 * - "edge" gestures from the left and right edges (horizontal swipes), with separate actions for
 *   the upper and the lower half of the screen.
 *
 * A swipe has a short and a long length. While the finger moves, rising sounds and the spoken name
 * of the action tell what a release would do. A touch that starts in an edge band is left to
 * TalkBack until the finger has really moved, so taps and exploring near the edge keep working.
 */
class NikaEdgeSwipe(
  private val service: TalkBackService,
  private val context: Context,
) {
  private enum class Edge {
    BOTTOM,
    TOP,
    LEFT,
    RIGHT,
  }

  private enum class Phase {
    IDLE,
    PENDING,
    CLAIMED,
  }

  private val handler = Handler(Looper.getMainLooper())
  private var phase = Phase.IDLE
  private var candidates = emptySet<Edge>()
  private var startX = 0f
  private var startY = 0f
  private var screenW = 0
  private var screenH = 0
  private var rightHalf = false
  private var lowerHalf = false
  private var edge: Edge? = null
  private var level = 0
  private var ticks = 0
  private var speakOn = true
  private var soundOn = true
  private var debug = false
  private var lastToast: Toast? = null

  private fun prefs() = SharedPreferencesUtils.getSharedPreferences(context)

  private fun intPref(key: String, default: Int): Int =
    prefs().getString(key, null)?.toIntOrNull() ?: default

  private fun actionPref(key: String, default: String): String =
    prefs().getString(key, null) ?: default

  /** Millimetres to pixels. The density is used because the reported physical dpi is often wrong. */
  private fun mmToPx(mm: Int): Float = mm * context.resources.displayMetrics.densityDpi / 25.4f

  private fun fullScreenSize() {
    val metrics = context.resources.displayMetrics
    screenW = metrics.widthPixels
    screenH = metrics.heightPixels
    try {
      if (Build.VERSION.SDK_INT >= 30) {
        val bounds =
          context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
        if (bounds != null && bounds.height() > 0 && bounds.width() > 0) {
          screenW = bounds.width()
          screenH = bounds.height()
        }
      }
    } catch (e: RuntimeException) {
      // Falls back to the display metrics.
    }
  }

  private fun note(text: String) {
    if (!debug) return
    try {
      lastToast?.cancel()
      val toast = Toast.makeText(context, text, Toast.LENGTH_SHORT)
      lastToast = toast
      toast.show()
    } catch (e: RuntimeException) {
      // A message is not worth a crash.
    }
  }

  private fun isIphoneEdge(e: Edge) = e == Edge.BOTTOM || e == Edge.TOP

  private fun bandMm(e: Edge): Int =
    when (e) {
      Edge.BOTTOM -> intPref(PREF_IPH_BAND_B, 10)
      Edge.TOP -> intPref(PREF_IPH_BAND_T, 10)
      Edge.LEFT -> intPref(PREF_SIDE_BAND_L, 10)
      Edge.RIGHT -> intPref(PREF_SIDE_BAND_R, 10)
    }

  /** Returns true while this touch belongs to an edge swipe, so TalkBack must ignore it. */
  fun onMotionEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        phase = Phase.IDLE
        val p = prefs()
        debug = p.getBoolean(PREF_DEBUG, false)
        val iphone = p.getBoolean(PREF_IPH_ENABLED, false)
        val side = p.getBoolean(PREF_SIDE_ENABLED, false)
        if (!iphone && !side) return false
        fullScreenSize()
        val x = event.rawX
        val y = event.rawY
        val found = HashSet<Edge>()
        if (iphone && y >= screenH - mmToPx(bandMm(Edge.BOTTOM))) found.add(Edge.BOTTOM)
        if (iphone && y <= mmToPx(bandMm(Edge.TOP))) found.add(Edge.TOP)
        if (side && x <= mmToPx(bandMm(Edge.LEFT))) found.add(Edge.LEFT)
        if (side && x >= screenW - mmToPx(bandMm(Edge.RIGHT))) found.add(Edge.RIGHT)
        if (found.isEmpty()) {
          note("touch x=${x.toInt()} y=${y.toInt()} of ${screenW}x${screenH}: not in an edge band")
          return false
        }
        candidates = found
        phase = Phase.PENDING
        startX = x
        startY = y
        rightHalf = x >= screenW / 2f
        lowerHalf = y >= screenH / 2f
        edge = null
        level = 0
        ticks = 0
        note("touch x=${x.toInt()} y=${y.toInt()} of ${screenW}x${screenH}: edge $found, waiting for a swipe")
        return false
      }
      MotionEvent.ACTION_MOVE -> {
        when (phase) {
          Phase.IDLE -> return false
          Phase.PENDING -> {
            if (!anyProgress(event.rawX, event.rawY)) return false
            phase = Phase.CLAIMED
            claimed()
            update(event.rawX, event.rawY)
            return true
          }
          Phase.CLAIMED -> {
            update(event.rawX, event.rawY)
            return true
          }
        }
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        val was = phase == Phase.CLAIMED
        phase = Phase.IDLE
        return was
      }
      MotionEvent.ACTION_UP -> {
        val was = phase
        phase = Phase.IDLE
        if (was == Phase.CLAIMED) {
          update(event.rawX, event.rawY)
          val action = currentAction()
          note("released: level $level, action ${action ?: "none"}")
          if (action != null) {
            service.performNikaKeyAction(action)
          }
          return true
        }
        return false
      }
      MotionEvent.ACTION_CANCEL -> {
        val was = phase == Phase.CLAIMED
        phase = Phase.IDLE
        return was
      }
      else -> return phase == Phase.CLAIMED
    }
  }

  /** True when the finger has gone far enough from the edge to be a swipe and not a tap. */
  private fun anyProgress(x: Float, y: Float): Boolean {
    val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    for (c in candidates) {
      if (progress(c, x, y) >= slop) return true
    }
    return false
  }

  private fun claimed() {
    val p = prefs()
    val iph = candidates.any { isIphoneEdge(it) }
    speakOn = if (iph) p.getBoolean(PREF_IPH_SPEAK, true) else p.getBoolean(PREF_SIDE_SPEAK, true)
    soundOn = if (iph) p.getBoolean(PREF_IPH_SOUND, true) else p.getBoolean(PREF_SIDE_SOUND, true)
    note("swipe from the edge started")
    if (soundOn) beep(400, 35)
  }

  /** How far the finger has gone from the edge into the screen, or 0 if it is going another way. */
  private fun progress(e: Edge, x: Float, y: Float): Float {
    val dx = x - startX
    val dy = y - startY
    return when (e) {
      Edge.BOTTOM -> if (-dy > abs(dx)) -dy else 0f
      Edge.TOP -> if (dy > abs(dx)) dy else 0f
      Edge.LEFT -> if (dx > abs(dy)) dx else 0f
      Edge.RIGHT -> if (-dx > abs(dy)) -dx else 0f
    }
  }

  private fun lengthMm(e: Edge, long: Boolean): Int =
    when (e) {
      Edge.BOTTOM ->
        if (long) intPref(PREF_IPH_B_LONG_LEN, 20) else intPref(PREF_IPH_B_SHORT_LEN, 10)
      Edge.TOP -> if (long) intPref(PREF_IPH_T_LONG_LEN, 25) else intPref(PREF_IPH_T_SHORT_LEN, 10)
      else -> if (long) intPref(PREF_SIDE_LONG_LEN, 25) else intPref(PREF_SIDE_SHORT_LEN, 10)
    }

  private fun actionFor(e: Edge, long: Boolean): String {
    val type = if (long) "long" else "short"
    return when (e) {
      Edge.BOTTOM,
      Edge.TOP -> {
        val row = if (e == Edge.BOTTOM) "b" else "t"
        val col = if (rightHalf) "r" else "l"
        val default =
          when {
            e == Edge.BOTTOM && !long -> "HOME"
            e == Edge.BOTTOM -> "OVERVIEW"
            !long == !rightHalf -> "NOTIFICATIONS"
            else -> "QUICK_SETTINGS"
          }
        actionPref("nika_iph_${row}${col}_${type}_act", default)
      }
      else -> {
        val side = if (e == Edge.LEFT) "l" else "r"
        val half = if (lowerHalf) "d" else "u"
        val default = if (e == Edge.LEFT && lowerHalf && !long) "BACK" else UNASSIGNED
        actionPref("nika_side_${side}${half}_${type}_act", default)
      }
    }
  }

  /** The action that a release now would run, or null. */
  private fun currentAction(): String? {
    val e = edge ?: return null
    if (level == 0) return null
    val action = actionFor(e, level == 2)
    return if (action == UNASSIGNED) null else action
  }

  private fun update(x: Float, y: Float) {
    var best: Edge? = null
    var bestProgress = 0f
    for (c in candidates) {
      val p = progress(c, x, y)
      if (p > bestProgress) {
        best = c
        bestProgress = p
      }
    }
    var newLevel = 0
    if (best != null) {
      val longOk =
        actionFor(best, true) != UNASSIGNED && bestProgress >= mmToPx(lengthMm(best, true))
      val shortOk =
        actionFor(best, false) != UNASSIGNED && bestProgress >= mmToPx(lengthMm(best, false))
      newLevel = if (longOk) 2 else if (shortOk) 1 else 0
    }
    // Rising ticks while the finger goes further, one every 5 mm.
    if (soundOn && best != null) {
      val k = (bestProgress / mmToPx(5)).toInt()
      if (k > ticks) {
        ticks = k
        if (newLevel == level) beep(450 + 60 * minOf(k, 24), 30)
      }
    }
    if (best != edge || newLevel != level) {
      val changedUp = newLevel > level
      edge = best
      level = newLevel
      if (level > 0 && best != null) {
        if (soundOn) beep(if (changedUp) 900 + 250 * level else 700, 90)
        if (speakOn) {
          val name = GestureShortcutMapping.getActionString(context, actionFor(best, level == 2))
          service
            .speechController
            .speak(
              name,
              SpeechController.QUEUE_MODE_FLUSH_ALL,
              FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
              null,
              Performance.EVENT_ID_UNTRACKED,
            )
        }
      }
    }
  }

  /** Plays a short sine tone. */
  private fun beep(freq: Int, ms: Int) {
    try {
      val rate = 22050
      val n = rate * ms / 1000
      val data = ShortArray(n)
      for (i in 0 until n) {
        val fade = minOf(1f, minOf(i, n - 1 - i) / (rate * 0.005f))
        data[i] = (sin(2.0 * PI * freq * i / rate) * 9000 * fade).toInt().toShort()
      }
      val track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(rate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(n * 2)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()
      track.write(data, 0, n)
      track.play()
      handler.postDelayed({ track.release() }, ms + 150L)
    } catch (e: RuntimeException) {
      // No sound is not worth a crash.
    }
  }

  companion object {
    const val UNASSIGNED = "UNASSIGNED"
    const val PREF_DEBUG = "nika_edge_debug"
    const val PREF_IPH_ENABLED = "nika_iph_enabled"
    const val PREF_IPH_SPEAK = "nika_iph_speak"
    const val PREF_IPH_SOUND = "nika_iph_sound"
    const val PREF_IPH_BAND_B = "nika_iph_band_b"
    const val PREF_IPH_BAND_T = "nika_iph_band_t"
    const val PREF_IPH_B_SHORT_LEN = "nika_iph_b_short_len"
    const val PREF_IPH_B_LONG_LEN = "nika_iph_b_long_len"
    const val PREF_IPH_T_SHORT_LEN = "nika_iph_t_short_len"
    const val PREF_IPH_T_LONG_LEN = "nika_iph_t_long_len"
    const val PREF_SIDE_ENABLED = "nika_side_enabled"
    const val PREF_SIDE_SPEAK = "nika_side_speak"
    const val PREF_SIDE_SOUND = "nika_side_sound"
    const val PREF_SIDE_BAND_L = "nika_side_band_l"
    const val PREF_SIDE_BAND_R = "nika_side_band_r"
    const val PREF_SIDE_SHORT_LEN = "nika_side_short_len"
    const val PREF_SIDE_LONG_LEN = "nika_side_long_len"
  }
}
