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

package com.google.android.accessibility.talkback.preference.base

import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.preference.Preference
import com.google.android.accessibility.talkback.R

/** Nika: the volume of the sound feedback, 0..100. One item; the slider opens in a window. */
class NikaVolumePreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {

  init {
    isPersistent = true
  }

  private fun value(): Int = getPersistedInt(50).coerceIn(0, 100)

  override fun getSummary(): CharSequence = context.getString(R.string.nika_vol_value, value())

  override fun onClick() {
    val bar = SeekBar(context)
    bar.max = 100
    bar.progress = value()
    bar.contentDescription = context.getString(R.string.nika_vol_title)
    bar.setOnSeekBarChangeListener(
      object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
          if (fromUser) {
            persistInt(progress)
            notifyChanged()
          }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {}

        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
      }
    )
    val box = LinearLayout(context)
    val pad = (16 * context.resources.displayMetrics.density).toInt()
    box.setPadding(pad, pad, pad, pad)
    box.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    AlertDialog.Builder(context)
      .setTitle(R.string.nika_vol_title)
      .setView(box)
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }
}
