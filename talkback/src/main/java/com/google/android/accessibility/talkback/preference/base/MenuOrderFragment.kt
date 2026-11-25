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
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.contextmenu.NikaMenuOrder

/** Nika: order of the items of the Nika menu: move up, move down, hide or show. */
class MenuOrderFragment : NikaKeysBaseFragment(R.xml.menu_order_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_mo_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  private fun rebuild() {
    val ctx = context ?: return
    // An empty group from the XML is removed by the settings filter, so the rows go on the screen itself.
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val list = NikaMenuOrder.listForScreen(ctx)
    val order = list.map { it.first }
    val hidden = NikaMenuOrder.load(ctx, NikaMenuOrder.HIDDEN_KEY)
    if (order.isEmpty()) {
      val empty = Preference(ctx)
      empty.title = getString(R.string.nika_mo_empty)
      empty.isSelectable = false
      group.addPreference(empty)
      return
    }
    for ((i, entry) in list.withIndex()) {
      val title = entry.first
      val isHidden = hidden.contains(title)
      val p = MovePreference(ctx)
      p.title = entry.second
      p.summary = if (isHidden) getString(R.string.nika_mo_hidden) else null
      p.isPersistent = false
      p.up = if (i > 0) ({ move(title, -1) }) else null
      p.down = if (i < order.size - 1) ({ move(title, 1) }) else null
      p.toggle = { toggleHidden(title) }
      p.hiddenNow = isHidden
      p.setOnPreferenceClickListener {
        showMenu(p, title, entry.second, i, order.size, isHidden)
        true
      }
      group.addPreference(p)
    }
  }

  private fun showMenu(p: Preference, key: String, title: String, index: Int, size: Int, isHidden: Boolean) {
    val act = activity ?: return
    val labels = ArrayList<String>()
    val actions = ArrayList<() -> Unit>()
    if (index > 0) {
      labels.add(getString(R.string.nika_mo_up)); actions.add { move(key, -1) }
    }
    if (index < size - 1) {
      labels.add(getString(R.string.nika_mo_down)); actions.add { move(key, 1) }
    }
    labels.add(getString(if (isHidden) R.string.nika_mo_show else R.string.nika_mo_hide))
    actions.add { toggleHidden(key) }
    AlertDialog.Builder(act)
      .setTitle(title)
      .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun move(title: String, delta: Int) {
    val ctx = context ?: return
    NikaMenuOrder.listForScreen(ctx)
    val order = NikaMenuOrder.load(ctx, NikaMenuOrder.ORDER_KEY)
    val i = order.indexOf(title)
    val j = i + delta
    if (i < 0 || j < 0 || j >= order.size) return
    order.removeAt(i)
    order.add(j, title)
    NikaMenuOrder.save(ctx, NikaMenuOrder.ORDER_KEY, order)
    NikaMenuOrder.setCustom(ctx)
    rebuild()
  }

  private fun toggleHidden(title: String) {
    val ctx = context ?: return
    val hidden = NikaMenuOrder.load(ctx, NikaMenuOrder.HIDDEN_KEY)
    if (!hidden.remove(title)) hidden.add(title)
    NikaMenuOrder.save(ctx, NikaMenuOrder.HIDDEN_KEY, hidden)
    rebuild()
  }

  /** A row with the accessibility actions "move up", "move down" and "hide" or "show". */
  class MovePreference(context: android.content.Context) : Preference(context) {
    var up: (() -> Unit)? = null
    var down: (() -> Unit)? = null
    var toggle: (() -> Unit)? = null
    var hiddenNow = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
      super.onBindViewHolder(holder)
      val v: View = holder.itemView
      up?.let { f ->
        ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mo_up)) { _, _ -> f(); true }
      }
      down?.let { f ->
        ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mo_down)) { _, _ -> f(); true }
      }
      toggle?.let { f ->
        val label = context.getString(if (hiddenNow) R.string.nika_mo_show else R.string.nika_mo_hide)
        ViewCompat.addAccessibilityAction(v, label) { _, _ -> f(); true }
      }
    }
  }
}
