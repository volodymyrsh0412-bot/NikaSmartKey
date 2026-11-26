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

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.talkback.clock.NikaClockActions
import com.google.android.accessibility.talkback.macros.NikaKeepAlive
import com.google.android.accessibility.talkback.macros.NikaMacroActions
import com.google.android.accessibility.talkback.macros.NikaMacros
import com.google.android.accessibility.talkback.macros.NikaVoiceCmds
import com.google.android.accessibility.talkback.macros.NikaVoiceEngines
import com.google.android.accessibility.talkback.nikafocus.NikaFocusActions
import com.google.android.accessibility.talkback.translate.NikaTranslatorActions
import com.google.android.accessibility.utils.SharedPreferencesUtils

/** Nika: voice commands: add a command, the created commands, and the settings of the commands. */
class VoiceCommandsFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_vc_title)

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
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val note = Preference(group.context)
    note.title = getString(R.string.nika_vc_note)
    note.isPersistent = false
    note.isSelectable = false
    group.addPreference(note)

    toggle(group, getString(R.string.nika_vc_settings), openSettings) {
      openSettings = !openSettings
      rebuild()
    }
    if (openSettings) {
      row(group, getString(R.string.nika_vc_name), NikaVoiceCmds.userName(ctx).ifEmpty { getString(R.string.nika_vc_name_empty) }) {
        askText(R.string.nika_vc_name, NikaVoiceCmds.userName(ctx)) { text ->
          pref().edit().putString(NikaVoiceCmds.PREF_NAME, text.trim()).apply()
          rebuild()
        }
      }

      row(
        group,
        getString(R.string.nika_vc_greet_mode),
        getString(
          when (NikaVoiceCmds.greetMode(ctx)) {
            NikaVoiceCmds.GREET_HELLO -> R.string.nika_vc_greet_mode_hello
            NikaVoiceCmds.GREET_BEEP -> R.string.nika_vc_greet_mode_beep
            else -> R.string.nika_vc_greet_mode_name
          }
        ),
      ) {
        chooseGreet()
      }

      val engine = NikaVoiceEngines.find(NikaVoiceCmds.engine(ctx))
      row(group, getString(R.string.nika_vc_engine), getString(engine.titleRes) + ". " + getString(engine.noteRes)) {
        chooseEngine()
      }
      row(group, getString(R.string.nika_vc_lang), languageName(NikaVoiceCmds.language(ctx))) { chooseLanguage() }
      if (engine.id != NikaVoiceEngines.ANDROID) {
        val has = NikaVoiceCmds.key(ctx, engine.id).isNotEmpty()
        row(group, getString(R.string.nika_vc_key), getString(if (has) R.string.nika_vc_key_set else R.string.nika_vc_key_empty)) {
          askText(R.string.nika_vc_key, "") { text ->
            NikaVoiceCmds.setKey(ctx, engine.id, text)
            rebuild()
          }
        }
        row(group, getString(R.string.nika_vc_getkey), engine.keyUrl) {
          try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(engine.keyUrl)))
          } catch (e: RuntimeException) {
            Toast.makeText(ctx, R.string.nika_ka_cannot_open, Toast.LENGTH_LONG).show()
          }
        }
      }
    }

    row(group, getString(R.string.nika_vc_add), null) { askPhrases(null) }

    val list = NikaVoiceCmds.load(ctx)
    toggle(group, getString(R.string.nika_vc_created, list.size), openList) {
      openList = !openList
      rebuild()
    }
    if (openList) {
      for (c in list) {
        row(group, c.phrases.joinToString("; "), c.label) { commandMenu(c) }
      }
    }
  }

  private var openSettings = false
  private var openList = false

  /** A folding header: it says whether the group below is open. */
  private fun toggle(group: PreferenceGroup, title: String, open: Boolean, click: () -> Unit) {
    val p = NikaHeadingPreference(group.context)
    p.title = title
    p.summary = getString(if (open) R.string.nika_vc_expanded else R.string.nika_vc_collapsed)
    p.isPersistent = false
    p.setOnPreferenceClickListener {
      click()
      true
    }
    group.addPreference(p)
  }

  private fun pref() = SharedPreferencesUtils.getSharedPreferences(requireContext())

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

  private fun applyTrigger() {
    TalkBackService.getInstance()?.let { NikaKeepAlive.apply(it) }
  }

  private fun choosePower() {
    val act = activity ?: return
    val ids = listOf(NikaVoiceCmds.POWER_OFF, NikaVoiceCmds.POWER_DOUBLE, NikaVoiceCmds.POWER_TRIPLE)
    val names =
      arrayOf(getString(R.string.nika_vc_power_off), getString(R.string.nika_vc_power_double), getString(R.string.nika_vc_power_triple))
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vc_power)
      .setItems(names) { _, which ->
        pref().edit().putString(NikaVoiceCmds.PREF_POWER, ids[which]).apply()
        applyTrigger()
        rebuild()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseGreet() {
    val act = activity ?: return
    val ids = listOf(NikaVoiceCmds.GREET_NAME, NikaVoiceCmds.GREET_HELLO, NikaVoiceCmds.GREET_BEEP)
    val names =
      arrayOf(
        getString(R.string.nika_vc_greet_mode_name),
        getString(R.string.nika_vc_greet_mode_hello),
        getString(R.string.nika_vc_greet_mode_beep),
      )
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vc_greet_mode)
      .setItems(names) { _, which ->
        pref().edit().putString(NikaVoiceCmds.PREF_GREET, ids[which]).apply()
        rebuild()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseEngine() {
    val act = activity ?: return
    val all = NikaVoiceEngines.ALL
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vc_engine)
      .setItems(all.map { getString(it.titleRes) + ". " + getString(it.noteRes) }.toTypedArray()) { _, which ->
        pref().edit().putString(NikaVoiceCmds.PREF_ENGINE, all[which].id).apply()
        rebuild()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun askText(title: Int, initial: String, onOk: (String) -> Unit) {
    val act = activity ?: return
    val edit = EditText(act)
    edit.inputType = InputType.TYPE_CLASS_TEXT
    edit.setText(initial)
    edit.setSelectAllOnFocus(true)
    val dialog =
      AlertDialog.Builder(act)
        .setTitle(title)
        .setView(edit)
        .setPositiveButton(android.R.string.ok) { _, _ -> onOk(edit.text.toString()) }
        .setNegativeButton(android.R.string.cancel, null)
        .setNeutralButton(R.string.nika_vc_clear, null)
        .create()
    dialog.show()
    // "Clear" empties the field and keeps the window open.
    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
      edit.setText("")
      edit.requestFocus()
    }
  }

  // ---------------------------------------------------------------- one command

  private fun save(old: NikaVoiceCmds.Cmd?, new: NikaVoiceCmds.Cmd?) {
    val ctx = context ?: return
    val list = NikaVoiceCmds.load(ctx)
    val at = list.indexOfFirst { it.id == old?.id }
    if (at >= 0) list.removeAt(at)
    if (new != null) list.add(if (at >= 0) at else list.size, new)
    NikaVoiceCmds.save(ctx, list)
    rebuild()
  }

  private fun commandMenu(c: NikaVoiceCmds.Cmd) {
    val act = activity ?: return
    val items =
      arrayOf(
        getString(R.string.nika_vc_test),
        getString(R.string.nika_vc_change_phrases),
        getString(R.string.nika_vc_change_action),
        getString(R.string.nika_vr_remove),
      )
    AlertDialog.Builder(act)
      .setTitle(c.name)
      .setItems(items) { _, which ->
        when (which) {
          0 -> TalkBackService.getInstance()?.let { NikaVoiceCmds.run(it, c) }
          1 -> askPhrases(c)
          2 -> askKind(c, c.phrases)
          else -> save(c, null)
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun askPhrases(old: NikaVoiceCmds.Cmd?) {
    askText(R.string.nika_vc_phrases, old?.phrases?.joinToString("; ") ?: "") { text ->
      val phrases = text.split(";", "\n").map { it.trim() }.filter { it.isNotEmpty() }
      if (phrases.isEmpty()) return@askText
      if (old != null) save(old, NikaVoiceCmds.Cmd(old.id, phrases, old.kind, old.value, old.label)) else askKind(null, phrases)
    }
  }

  private fun askKind(old: NikaVoiceCmds.Cmd?, phrases: List<String>) {
    val act = activity ?: return
    val items =
      arrayOf(
        getString(R.string.nika_vc_kind_action),
        getString(R.string.nika_vc_kind_macro),
        getString(R.string.nika_vc_kind_app),
        getString(R.string.nika_vc_kind_call),
      )
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vc_kind)
      .setItems(items) { _, which ->
        when (which) {
          0 -> pickAction(old, phrases)
          1 -> pickMacro(old, phrases)
          2 -> pickApp(old, phrases)
          else -> askNumber(old, phrases)
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private var pendingOld: NikaVoiceCmds.Cmd? = null
  private var pendingPhrases: List<String> = emptyList()

  private val pickPhone =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val uri = result.data?.data
      if (result.resultCode == Activity.RESULT_OK && uri != null) {
        try {
          requireContext()
            .contentResolver
            .query(
              uri,
              arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
              null,
              null,
              null,
            )
            ?.use { c ->
              if (c.moveToFirst()) {
                val number = (c.getString(0) ?: "").filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
                val name = c.getString(1) ?: number
                if (number.isNotEmpty()) askCallMode(pendingOld, pendingPhrases, number, name)
              }
            }
        } catch (e: RuntimeException) {
          Toast.makeText(requireContext(), R.string.nika_vc_no_contact, Toast.LENGTH_LONG).show()
        }
      }
    }

  private fun askNumber(old: NikaVoiceCmds.Cmd?, phrases: List<String>) {
    val act = activity ?: return
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vc_number_how)
      .setItems(arrayOf(getString(R.string.nika_vc_number_type), getString(R.string.nika_vc_number_contact))) { _, which ->
        if (which == 0) {
          askText(R.string.nika_vc_number, "") { text ->
            val number = text.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
            if (number.isNotEmpty()) askCallMode(old, phrases, number, number)
          }
        } else {
          pendingOld = old
          pendingPhrases = phrases
          try {
            pickPhone.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
          } catch (e: RuntimeException) {
            Toast.makeText(act, R.string.nika_vc_no_contact, Toast.LENGTH_LONG).show()
          }
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  /** How to call: straight through the phone (no question), or let the system ask which app. */
  private fun askCallMode(old: NikaVoiceCmds.Cmd?, phrases: List<String>, number: String, who: String) {
    val act = activity ?: return
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vc_mode_title)
      .setItems(arrayOf(getString(R.string.nika_vc_mode_phone), getString(R.string.nika_vc_mode_ask))) { _, which ->
        if (which == 0) {
          finish(old, phrases, NikaVoiceCmds.KIND_CALL_PHONE, number, getString(R.string.nika_vc_label_callphone, who))
        } else {
          finish(old, phrases, NikaVoiceCmds.KIND_CALL, number, getString(R.string.nika_vc_label_call, who))
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private val languages = listOf("" to "", "uk" to "Українська", "ru" to "Русский", "be" to "Беларуская", "pl" to "Polski")

  private fun languageName(code: String): String =
    languages.firstOrNull { it.first == code }?.let { if (it.first.isEmpty()) getString(R.string.nika_vc_lang_auto) else it.second }
      ?: code

  private fun chooseLanguage() {
    val act = activity ?: return
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vc_lang)
      .setItems(languages.map { languageName(it.first) }.toTypedArray()) { _, which ->
        pref().edit().putString(NikaVoiceCmds.PREF_LANG, languages[which].first).apply()
        rebuild()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun finish(old: NikaVoiceCmds.Cmd?, phrases: List<String>, kind: String, value: String, label: String) {
    save(old, NikaVoiceCmds.Cmd(old?.id ?: NikaVoiceCmds.newId(), phrases, kind, value, label))
  }

  private fun pickFrom(title: Int, names: List<String>, onPick: (Int) -> Unit) {
    val act = activity ?: return
    if (names.isEmpty()) {
      Toast.makeText(act, R.string.nika_vc_empty_list, Toast.LENGTH_LONG).show()
      return
    }
    AlertDialog.Builder(act)
      .setTitle(title)
      .setItems(names.toTypedArray()) { _, which -> onPick(which) }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun pickAction(old: NikaVoiceCmds.Cmd?, phrases: List<String>) {
    val res = resources
    val names = ArrayList<String>()
    val values = ArrayList<String>()
    val pairs =
      listOf(
        R.array.shortcut_basic_navigation to R.array.shortcut_value_basic_navigation,
        R.array.shortcut_system_actions to R.array.shortcut_value_system_actions,
        R.array.shortcut_reading_control to R.array.shortcut_value_reading_control,
        R.array.shortcut_menu_control to R.array.shortcut_value_menu_control,
        R.array.shortcut_text_editing to R.array.shortcut_value_text_editing,
      )
    for ((n, v) in pairs) {
      val labels = res.getStringArray(n)
      val vals = res.getStringArray(v)
      for (i in 0 until minOf(labels.size, vals.size)) {
        names.add(labels[i])
        values.add(vals[i])
      }
    }
    for (a in NikaFocusActions.ACTIONS) {
      names.add(getString(a.labelRes))
      values.add(a.value)
    }
    for (a in NikaClockActions.ACTIONS) {
      names.add(getString(a.labelRes))
      values.add(a.value)
    }
    for (a in NikaTranslatorActions.ACTIONS) {
      names.add(getString(a.labelRes))
      values.add(a.value)
    }
    pickFrom(R.string.nika_vc_kind_action, names) {
      finish(old, phrases, NikaVoiceCmds.KIND_ACTION, values[it], names[it])
    }
  }

  private fun pickMacro(old: NikaVoiceCmds.Cmd?, phrases: List<String>) {
    val ctx = context ?: return
    val macros = NikaMacros.load(ctx)
    pickFrom(R.string.nika_vc_kind_macro, macros.map { it.name }) {
      finish(
        old,
        phrases,
        NikaVoiceCmds.KIND_ACTION,
        NikaMacroActions.actionOf(macros[it].id),
        getString(R.string.nika_vc_label_macro, macros[it].name),
      )
    }
  }

  private fun pickApp(old: NikaVoiceCmds.Cmd?, phrases: List<String>) {
    val ctx = context ?: return
    val pm = ctx.packageManager
    val apps =
      pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { Pair(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .distinctBy { it.second }
        .sortedBy { it.first.lowercase() }
    pickFrom(R.string.nika_vc_kind_app, apps.map { it.first }) {
      finish(old, phrases, NikaVoiceCmds.KIND_APP, apps[it].second, getString(R.string.nika_vc_label_app, apps[it].first))
    }
  }
}
