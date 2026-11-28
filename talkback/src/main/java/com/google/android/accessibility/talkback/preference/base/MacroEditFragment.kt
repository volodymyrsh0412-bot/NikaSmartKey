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

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.macros.NikaMacros

/** Nika: the steps of one macro: add, move, delete, record. */
class MacroEditFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  private val macroId: String
    get() = arguments?.getString(ARG_ID).orEmpty()

  override fun getTitle(): CharSequence =
    context?.let { NikaMacros.find(it, macroId)?.name } ?: getText(R.string.nika_mac_title)

  private var exportName = ""

  private val exportFile =
    registerForActivityResult(
      androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
      val ctx = context
      if (uri != null && ctx != null) {
        try {
          val m = NikaMacros.find(ctx, macroId)
          if (m != null) {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(NikaMacros.toJson(listOf(m)).toByteArray()) }
            android.widget.Toast.makeText(ctx, R.string.nika_mac_exported, android.widget.Toast.LENGTH_SHORT).show()
          }
        } catch (e: Exception) {
          android.widget.Toast.makeText(ctx, R.string.nika_mac_file_error, android.widget.Toast.LENGTH_SHORT).show()
        }
      }
    }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  override fun onResume() {
    super.onResume()
    rebuild()
  }

  private fun rebuild() {
    val ctx = context ?: return
    // An empty group from the XML is removed by the settings filter, so the rows go on the screen itself.
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val macro = NikaMacros.find(ctx, macroId)
    if (macro == null) return
    val recording = NikaMacros.recordingId(ctx) == macro.id

    row(group, getString(R.string.nika_mac_rename, macro.name), null) { askText(getString(R.string.nika_mac_name_hint), macro.name, false) { v -> update { it.name = v } } }
    row(
      group,
      getString(if (recording) R.string.nika_mac_record_on else R.string.nika_mac_record_off),
      getString(R.string.nika_mac_record_summary),
    ) {
      NikaMacros.setRecording(ctx, if (recording) "" else macro.id)
      rebuild()
    }
    row(group, getString(R.string.nika_mac_run_now), getString(R.string.nika_mac_run_now_summary)) {
      val id = macro.id
      android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
        { com.google.android.accessibility.talkback.macros.NikaMacroActions.runNow(id) },
        3000L,
      )
      android.widget.Toast.makeText(ctx, R.string.nika_mac_run_now_summary, android.widget.Toast.LENGTH_SHORT).show()
    }
    row(group, getString(R.string.nika_mac_add_step), null) { chooseStepType() }
    row(
      group,
      getString(R.string.nika_mac_conds),
      if (macro.conds.isEmpty()) getString(R.string.nika_mac_conds_none)
      else macro.conds.joinToString("; ") { condText(it) },
    ) {
      manageConditions(macro)
    }
    row(group, getString(R.string.nika_mac_gest_title), getString(R.string.nika_mac_gest_summary)) {
      val act = activity
      if (act != null) {
        AlertDialog.Builder(act)
          .setTitle(R.string.nika_mac_gest_title)
          .setMessage(R.string.nika_mac_gest_info)
          .setPositiveButton(android.R.string.ok, null)
          .show()
      }
    }

    for ((i, step) in macro.steps.withIndex()) {
      val p = StepPreference(ctx)
      p.title = describe(step)
      p.isPersistent = false
      p.up = if (i > 0) ({ move(i, -1) }) else null
      p.down = if (i < macro.steps.size - 1) ({ move(i, 1) }) else null
      p.remove = { removeAt(i) }
      p.setOnPreferenceClickListener {
        showStepMenu(p.title.toString(), i, macro.steps.size)
        true
      }
      group.addPreference(p)
    }
    row(group, getString(R.string.nika_mac_export), null) {
      exportName = macro.name
      exportFile.launch(macro.name + ".json")
    }
    row(group, getString(R.string.nika_mac_delete_macro), null) {
      val act = activity
      if (act != null) {
        AlertDialog.Builder(act)
          .setTitle(macro.name)
          .setMessage(R.string.nika_mac_delete_macro)
          .setPositiveButton(android.R.string.ok) { _, _ ->
            val all = NikaMacros.load(ctx)
            all.removeAll { it.id == macro.id }
            NikaMacros.save(ctx, all)
            if (NikaMacros.recordingId(ctx) == macro.id) NikaMacros.setRecording(ctx, "")
            act.onBackPressedDispatcher.onBackPressed()
          }
          .setNegativeButton(android.R.string.cancel, null)
          .show()
      }
    }
  }

  private fun row(group: PreferenceGroup, title: String, summary: String?, click: () -> Unit) {
    val p = Preference(group.context)
    p.title = title
    p.summary = summary
    p.isPersistent = false
    p.setOnPreferenceClickListener {
      click()
      true
    }
    group.addPreference(p)
  }

  private fun update(change: (NikaMacros.Macro) -> Unit) {
    val ctx = context ?: return
    val all = NikaMacros.load(ctx)
    val m = all.firstOrNull { it.id == macroId } ?: return
    change(m)
    NikaMacros.save(ctx, all)
    rebuild()
  }

  private fun move(index: Int, delta: Int) =
    update { m ->
      val j = index + delta
      if (index in m.steps.indices && j in m.steps.indices) {
        val s = m.steps.removeAt(index)
        m.steps.add(j, s)
      }
    }

  private fun removeAt(index: Int) = update { m -> if (index in m.steps.indices) m.steps.removeAt(index) }

  private fun describe(s: NikaMacros.Step): String =
    when (s.type) {
      NikaMacros.CLICK -> getString(R.string.nika_mac_d_click, s.b.ifEmpty { s.a })
      NikaMacros.CLICK_TEXT -> getString(R.string.nika_mac_d_clicktext, s.a)
      NikaMacros.WAIT -> getString(R.string.nika_mac_d_wait, s.n.toString())
      NikaMacros.SAY -> getString(R.string.nika_mac_d_say, s.a)
      NikaMacros.TYPE -> getString(R.string.nika_mac_d_type, s.a)
      NikaMacros.APP -> getString(R.string.nika_mac_d_app, appLabel(s.a))
      NikaMacros.GLOBAL -> getString(R.string.nika_mac_d_global, globalName(s.n))
      NikaMacros.HOLD -> getString(R.string.nika_mac_d_hold, s.b.ifEmpty { s.a })
      NikaMacros.RELEASE ->
        if (s.a.isEmpty() || s.a == "none" || s.n <= 0) {
          getString(R.string.nika_mac_d_release_still)
        } else {
          getString(R.string.nika_mac_d_release_move, dirName(s.a), s.n)
        }
      NikaMacros.IF -> getString(R.string.nika_mac_d_if, condText(s))
      NikaMacros.ELSE_IF -> getString(R.string.nika_mac_d_elseif, condText(s))
      NikaMacros.ELSE -> getString(R.string.nika_mac_t_else)
      NikaMacros.END_IF -> getString(R.string.nika_mac_t_endif)
      NikaMacros.REPEAT -> getString(R.string.nika_mac_d_repeat, s.n)
      NikaMacros.REPEAT_WHILE -> getString(R.string.nika_mac_d_repeatwhile, condText(s))
      NikaMacros.END_REPEAT -> getString(R.string.nika_mac_t_endrepeat)
      NikaMacros.BREAK -> getString(R.string.nika_mac_t_break)
      NikaMacros.STOP -> getString(R.string.nika_mac_t_stop)
      NikaMacros.WAIT_UNTIL -> getString(R.string.nika_mac_d_waituntil, condText(s), s.n)
      NikaMacros.TAP_POS -> getString(R.string.nika_mac_d_tappos, s.a, s.b)
      NikaMacros.SELECT -> getString(R.string.nika_mac_d_select, s.a)
      NikaMacros.SAY_OBJECT ->
        if (s.a.isEmpty()) getString(R.string.nika_mac_d_sayobject_focus)
        else getString(R.string.nika_mac_d_sayobject, s.a)
      else -> s.type
    }

  private fun condText(s: NikaMacros.Step): String =
    when (s.a) {
      NikaMacros.COND_SEE -> getString(R.string.nika_mac_c_see_d, s.b)
      NikaMacros.COND_NOT_SEE -> getString(R.string.nika_mac_c_notsee_d, s.b)
      NikaMacros.COND_APP -> getString(R.string.nika_mac_c_app_d, appLabel(s.b))
      NikaMacros.COND_NOT_APP -> getString(R.string.nika_mac_c_notapp_d, appLabel(s.b))
      else -> s.a
    }

  /** Asks for a condition and gives its kind and value. */
  private fun chooseCondition(done: (String, String) -> Unit) {
    val act = activity ?: return
    val kinds = arrayOf(NikaMacros.COND_SEE, NikaMacros.COND_NOT_SEE, NikaMacros.COND_APP, NikaMacros.COND_NOT_APP)
    val labels =
      arrayOf(
        getString(R.string.nika_mac_c_see),
        getString(R.string.nika_mac_c_notsee),
        getString(R.string.nika_mac_c_app),
        getString(R.string.nika_mac_c_notapp),
      )
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_mac_c_title)
      .setItems(labels) { _, which ->
        val kind = kinds[which]
        if (kind == NikaMacros.COND_SEE || kind == NikaMacros.COND_NOT_SEE) {
          askText(labels[which], "", false) { v -> done(kind, v) }
        } else {
          chooseApp { pkg -> done(kind, pkg) }
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  /** The conditions "run only when": add one, or remove them all. */
  private fun manageConditions(macro: NikaMacros.Macro) {
    val act = activity ?: return
    val b = AlertDialog.Builder(act).setTitle(R.string.nika_mac_conds)
    if (macro.conds.isNotEmpty()) {
      b.setMessage(macro.conds.joinToString("\n") { condText(it) })
      b.setNeutralButton(R.string.nika_mac_conds_clear) { _, _ -> update { it.conds.clear() } }
    }
    b.setPositiveButton(R.string.nika_mac_conds_add) { _, _ ->
      chooseCondition { k, v -> update { it.conds.add(NikaMacros.Step("cond", k, v)) } }
    }
    b.setNegativeButton(android.R.string.cancel, null)
    b.show()
  }

  private fun dirName(dir: String): String =
    when (dir) {
      "up" -> getString(R.string.nika_mac_dir_up)
      "down" -> getString(R.string.nika_mac_dir_down)
      "left" -> getString(R.string.nika_mac_dir_left)
      "right" -> getString(R.string.nika_mac_dir_right)
      else -> getString(R.string.nika_mac_dir_none)
    }

  private fun chooseRelease() {
    val act = activity ?: return
    val dirs = arrayOf("none", "up", "down", "left", "right")
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_mac_t_release)
      .setItems(dirs.map { dirName(it) }.toTypedArray()) { _, which ->
        val dir = dirs[which]
        if (dir == "none") {
          add(NikaMacros.Step(NikaMacros.RELEASE, a = "none", n = 0))
        } else {
          askText(getString(R.string.nika_mac_distance_hint), "20", true) { v ->
            add(NikaMacros.Step(NikaMacros.RELEASE, a = dir, n = (v.toIntOrNull() ?: 20).coerceIn(1, 40)))
          }
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun appLabel(pkg: String): String =
    try {
      val pm = requireContext().packageManager
      pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
      pkg
    }

  private val globals: List<Pair<Int, Int>> =
    listOf(
      AccessibilityService.GLOBAL_ACTION_BACK to R.string.nika_mac_g_back,
      AccessibilityService.GLOBAL_ACTION_HOME to R.string.nika_mac_g_home,
      AccessibilityService.GLOBAL_ACTION_RECENTS to R.string.nika_mac_g_recents,
      AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS to R.string.nika_mac_g_notifications,
    )

  private fun globalName(code: Int): String =
    globals.firstOrNull { it.first == code }?.let { getString(it.second) } ?: code.toString()

  private fun showStepMenu(title: String, index: Int, size: Int) {
    val act = activity ?: return
    val labels = ArrayList<String>()
    val actions = ArrayList<() -> Unit>()
    if (index > 0) { labels.add(getString(R.string.nika_mo_up)); actions.add { move(index, -1) } }
    if (index < size - 1) { labels.add(getString(R.string.nika_mo_down)); actions.add { move(index, 1) } }
    labels.add(getString(R.string.nika_mac_delete)); actions.add { removeAt(index) }
    AlertDialog.Builder(act)
      .setTitle(title)
      .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseStepType() {
    val shortSteps = intArrayOf(0, 1, 2, 3, 4, 5, 16)
    val act = activity ?: return
    val labels =
      arrayOf(
        getString(R.string.nika_mac_t_wait),
        getString(R.string.nika_mac_t_say),
        getString(R.string.nika_mac_t_type),
        getString(R.string.nika_mac_t_app),
        getString(R.string.nika_mac_t_clicktext),
        getString(R.string.nika_mac_t_global),
        getString(R.string.nika_mac_t_hold),
        getString(R.string.nika_mac_t_release),
        getString(R.string.nika_mac_t_if),
        getString(R.string.nika_mac_t_elseif),
        getString(R.string.nika_mac_t_else),
        getString(R.string.nika_mac_t_endif),
        getString(R.string.nika_mac_t_repeat),
        getString(R.string.nika_mac_t_repeatwhile),
        getString(R.string.nika_mac_t_endrepeat),
        getString(R.string.nika_mac_t_break),
        getString(R.string.nika_mac_t_stop),
        getString(R.string.nika_mac_t_waituntil),
        getString(R.string.nika_mac_t_tappos),
        getString(R.string.nika_mac_t_select),
        getString(R.string.nika_mac_t_sayobject),
      )
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_mac_add_step)
      .setItems(Array(shortSteps.size) { labels[shortSteps[it]] }) { _, shortIndex ->
        val which = shortSteps[shortIndex]
        when (which) {
          0 -> askText(labels[0], "2", true) { v -> add(NikaMacros.Step(NikaMacros.WAIT, n = v.toIntOrNull() ?: 1)) }
          1 -> askText(labels[1], "", false) { v -> add(NikaMacros.Step(NikaMacros.SAY, a = v)) }
          2 -> askText(labels[2], "", false) { v -> add(NikaMacros.Step(NikaMacros.TYPE, a = v)) }
          3 -> chooseApp { pkg -> add(NikaMacros.Step(NikaMacros.APP, a = pkg)) }
          4 -> askText(labels[4], "", false) { v -> add(NikaMacros.Step(NikaMacros.CLICK_TEXT, a = v)) }
          5 -> chooseGlobal()
          6 -> askText(labels[6], "", false) { v -> add(NikaMacros.Step(NikaMacros.HOLD, a = v)) }
          7 -> chooseRelease()
          8 -> chooseCondition { k, v -> add(NikaMacros.Step(NikaMacros.IF, a = k, b = v)) }
          9 -> chooseCondition { k, v -> add(NikaMacros.Step(NikaMacros.ELSE_IF, a = k, b = v)) }
          10 -> add(NikaMacros.Step(NikaMacros.ELSE))
          11 -> add(NikaMacros.Step(NikaMacros.END_IF))
          12 -> askText(labels[12], "3", true) { v -> add(NikaMacros.Step(NikaMacros.REPEAT, n = (v.toIntOrNull() ?: 1).coerceIn(1, 1000))) }
          13 -> chooseCondition { k, v -> add(NikaMacros.Step(NikaMacros.REPEAT_WHILE, a = k, b = v)) }
          14 -> add(NikaMacros.Step(NikaMacros.END_REPEAT))
          15 -> add(NikaMacros.Step(NikaMacros.BREAK))
          16 -> add(NikaMacros.Step(NikaMacros.STOP))
          18 ->
            askText(getString(R.string.nika_mac_tappos_x), "50", true) { x ->
              askText(getString(R.string.nika_mac_tappos_y), "50", true) { y ->
                add(
                  NikaMacros.Step(
                    NikaMacros.TAP_POS,
                    a = (x.toIntOrNull() ?: 50).coerceIn(0, 100).toString(),
                    b = (y.toIntOrNull() ?: 50).coerceIn(0, 100).toString(),
                  )
                )
              }
            }
          19 -> askText(labels[19], "", false) { v -> add(NikaMacros.Step(NikaMacros.SELECT, a = v)) }
          20 -> askText(labels[20], "", false) { v -> add(NikaMacros.Step(NikaMacros.SAY_OBJECT, a = v)) }
          17 ->
            chooseCondition { k, v ->
              askText(getString(R.string.nika_mac_wait_seconds), "10", true) { sec ->
                add(NikaMacros.Step(NikaMacros.WAIT_UNTIL, a = k, b = v, n = (sec.toIntOrNull() ?: 10).coerceIn(1, 120)))
              }
            }
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseGlobal() {
    val act = activity ?: return
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_mac_t_global)
      .setItems(globals.map { getString(it.second) }.toTypedArray()) { _, which ->
        add(NikaMacros.Step(NikaMacros.GLOBAL, n = globals[which].first))
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseApp(onPick: (String) -> Unit) {
    val act = activity ?: return
    val pm = act.packageManager
    val found = ArrayList<Pair<String, String>>()
    val seen = HashSet<String>()
    try {
      val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      for (info in pm.queryIntentActivities(intent, 0)) {
        val pkg = info.activityInfo?.packageName ?: continue
        if (seen.add(pkg)) found.add(Pair(info.loadLabel(pm).toString(), pkg))
      }
    } catch (e: RuntimeException) {
      // an empty list
    }
    found.sortBy { it.first.lowercase() }
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_mac_t_app)
      .setItems(found.map { it.first }.toTypedArray()) { _, which ->
        onPick(found[which].second)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun add(step: NikaMacros.Step) = update { it.steps.add(step) }

  private fun askText(title: String, initial: String, number: Boolean, done: (String) -> Unit) {
    val act = activity ?: return
    val edit = EditText(act)
    edit.setText(initial)
    if (number) edit.inputType = InputType.TYPE_CLASS_NUMBER
    AlertDialog.Builder(act)
      .setTitle(title)
      .setView(edit)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val v = edit.text.toString().trim()
        if (v.isNotEmpty()) done(v)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  /** A row with the accessibility actions "move up", "move down" and "delete". */
  class StepPreference(context: android.content.Context) : Preference(context) {
    var up: (() -> Unit)? = null
    var down: (() -> Unit)? = null
    var remove: (() -> Unit)? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
      super.onBindViewHolder(holder)
      val v: View = holder.itemView
      up?.let { f -> ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mo_up)) { _, _ -> f(); true } }
      down?.let { f -> ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mo_down)) { _, _ -> f(); true } }
      remove?.let { f -> ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mac_delete)) { _, _ -> f(); true } }
    }
  }

  companion object {
    const val ARG_ID = "nika_macro_id"
  }
}
