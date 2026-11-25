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

package com.google.android.accessibility.talkback.contextmenu

import android.content.Context
import com.google.android.accessibility.utils.SharedPreferencesUtils

/**
 * Nika: the order of the items of the Nika menu, and which items are hidden, chosen by the person.
 * An item is known by the name of its id (for example "i:read_from_top") or, when it has none, by
 * its title ("t:..."). The list also holds the items that are switched on in the menu settings but
 * were not shown yet, so the screen is never empty.
 */
object NikaMenuOrder {
  const val ORDER_KEY = "nika_menu_order"
  const val HIDDEN_KEY = "nika_menu_hidden"
  const val TITLES_KEY = "nika_menu_titles"
  const val CUSTOM_KEY = "nika_menu_custom"
  private const val SEP = "\n"

  /** Menu settings check box key, its default, the title and the id name of the menu item. */
  private val SEEDS: List<Array<String>> =
    listOf(
      arrayOf("pref_show_context_menu_typo_suggestions_setting_key", "pref_show_context_menu_typo_suggestions_default", "title_edittext_typo_suggestions", "typo_suggestions_menu"),
      arrayOf("pref_show_context_menu_custom_action_setting_key", "pref_show_context_menu_custom_action_default", "title_custom_action", "custom_action_menu"),
      arrayOf("pref_show_context_menu_text_formatting_setting_key", "pref_show_context_menu_text_formatting_default", "title_text_formatting_menu", "text_formatting"),
      arrayOf("pref_show_context_menu_page_navigation_setting_key", "pref_show_context_menu_page_navigation_default", "title_viewpager_controls", "viewpager_menu"),
      arrayOf("pref_show_context_menu_labeling_setting_key", "pref_show_context_menu_labeling_default", "title_labeling_controls", "labeling_breakout_add_label"),
      arrayOf("pref_show_context_menu_image_caption_setting_key", "pref_show_context_menu_image_caption_default", "title_image_caption", "image_caption_menu"),
      arrayOf("pref_show_context_menu_summarize_view_setting_key", "pref_show_context_menu_summarize_view_default", "title_summarize_view", "summarize_view_menu"),
      arrayOf("pref_show_context_menu_granularity_setting_key", "pref_show_context_menu_granularity_default", "title_granularity", "granularity_menu"),
      arrayOf("pref_show_context_menu_read_from_top_setting_key", "pref_show_context_menu_read_from_top_default", "title_read_from_top", "read_from_top"),
      arrayOf("pref_show_context_menu_read_from_current_setting_key", "pref_show_context_menu_read_from_current_default", "shortcut_read_from_current", "read_from_current"),
      arrayOf("pref_show_context_menu_copy_last_spoken_phrase_setting_key", "pref_show_context_menu_copy_last_spoken_phrase_default", "title_copy_last_spoken_phrase", "copy_last_utterance_to_clipboard"),
      arrayOf("pref_show_context_menu_append_last_spoken_phrase_setting_key", "pref_show_context_menu_append_last_spoken_phrase_default", "title_append_last_spoken_phrase", "append_last_utterance_to_clipboard"),
      arrayOf("pref_show_context_menu_spell_last_spoken_phrase_setting_key", "pref_show_context_menu_spell_last_spoken_phrase_default", "title_spell_last_spoken_phrase", "spell_last_utterance"),
      arrayOf("pref_show_context_menu_repeat_last_spoken_phrase_setting_key", "pref_show_context_menu_repeat_last_spoken_phrase_default", "title_repeat_last_spoken_phrase", "repeat_last_utterance"),
      arrayOf("pref_show_context_menu_find_on_screen_setting_key", "pref_show_context_menu_find_on_screen_default", "title_show_screen_search", "screen_search"),
      arrayOf("pref_show_context_menu_voice_commands_setting_key", "pref_show_context_menu_voice_commands_default", "title_show_voice_commands", "voice_commands"),
      arrayOf("pref_show_context_menu_show_keyboard_shortcuts_setting_key", "pref_show_context_menu_show_keyboard_shortcuts_default", "title_show_keyboard_shortcuts", "show_keyboard_shortcuts"),
      arrayOf("pref_show_context_menu_talkback_settings_setting_key", "pref_show_context_menu_talkback_settings_default", "talkback_show_talkback_settings", "talkback_settings"),
      arrayOf("pref_show_context_menu_tts_settings_setting_key", "pref_show_context_menu_tts_settings_default", "tts_preferences_title", "tts_settings"),
      arrayOf("pref_show_context_menu_braille_display_settings_setting_key", "pref_show_context_menu_braille_display_settings_default", "title_show_braille_display_settings", "braille_display_settings"),
      arrayOf("pref_show_context_menu_pause_backtalk_setting_key", "pref_show_context_menu_pause_backtalk_default", "title_pause_backtalk", "pause_backtalk"),
      arrayOf("pref_show_context_menu_verbosity_setting_key", "pref_show_context_menu_verbosity_default", "title_pref_selector_verbosity", "verbosity"),
      arrayOf("pref_show_context_menu_inspect_item_setting_key", "pref_show_context_menu_inspect_item_default", "shortcut_inspect_item", "inspect_item"),
      arrayOf("pref_show_context_menu_copy_screen_tree_setting_key", "pref_show_context_menu_copy_screen_tree_default", "shortcut_copy_screen_tree", "copy_screen_tree"),
    )

  private fun prefs(context: Context) = SharedPreferencesUtils.getSharedPreferences(context)

  @JvmStatic
  fun load(context: Context, key: String): MutableList<String> {
    val raw =
      try {
        prefs(context).getString(key, "") ?: ""
      } catch (e: RuntimeException) {
        ""
      }
    return if (raw.isEmpty()) mutableListOf() else raw.split(SEP).filter { it.isNotEmpty() }.toMutableList()
  }

  @JvmStatic
  fun save(context: Context, key: String, list: List<String>) {
    try {
      prefs(context).edit().putString(key, list.joinToString(SEP)).apply()
    } catch (e: RuntimeException) {
      // skip
    }
  }

  @JvmStatic
  fun isCustom(context: Context): Boolean =
    try {
      prefs(context).getBoolean(CUSTOM_KEY, false)
    } catch (e: RuntimeException) {
      false
    }

  @JvmStatic
  fun setCustom(context: Context) {
    try {
      prefs(context).edit().putBoolean(CUSTOM_KEY, true).apply()
    } catch (e: RuntimeException) {
      // skip
    }
  }

  private fun loadTitles(context: Context): MutableMap<String, String> {
    val map = LinkedHashMap<String, String>()
    for (line in load(context, TITLES_KEY)) {
      val i = line.indexOf('\t')
      if (i > 0) map[line.substring(0, i)] = line.substring(i + 1)
    }
    return map
  }

  private fun saveTitles(context: Context, map: Map<String, String>) =
    save(context, TITLES_KEY, map.map { it.key + "\t" + it.value.replace('\n', ' ') })

  /** The title to show for a key in the settings. */
  @JvmStatic
  fun titleOf(context: Context, key: String): String =
    loadTitles(context)[key] ?: key.substringAfter(":")

  private fun keyOf(context: Context, item: ContextMenuItem): String {
    val title = item.title?.toString() ?: ""
    return try {
      val id = item.itemId
      if (id > 0) "i:" + context.resources.getResourceEntryName(id) else "t:$title"
    } catch (e: Exception) {
      "t:$title"
    }
  }

  private fun res(context: Context, name: String, type: String): Int =
    context.resources.getIdentifier(name, type, context.packageName)

  /** Items that are switched on in the menu settings, as (key, title) in the settings order. */
  private fun switchedOn(context: Context): List<Pair<String, String>> {
    val result = ArrayList<Pair<String, String>>()
    val p = prefs(context)
    for (row in SEEDS) {
      try {
        val keyRes = res(context, row[0], "string")
        val defRes = res(context, row[1], "bool")
        val titleRes = res(context, row[2], "string")
        if (keyRes == 0 || titleRes == 0) continue
        val default = if (defRes != 0) context.resources.getBoolean(defRes) else true
        if (!p.getBoolean(context.getString(keyRes), default)) continue
        result.add(Pair("i:" + row[3], context.getString(titleRes)))
      } catch (e: Exception) {
        // skip this row
      }
    }
    return result
  }

  /** The list for the settings screen: the saved order plus the items that are switched on. */
  @JvmStatic
  fun listForScreen(context: Context): List<Pair<String, String>> {
    val order = load(context, ORDER_KEY)
    val titles = loadTitles(context)
    var changed = false
    for ((key, title) in switchedOn(context)) {
      if (!order.contains(key)) {
        order.add(key)
        changed = true
      }
      if (!titles.containsKey(key)) {
        titles[key] = title
        saveTitles(context, titles)
      }
    }
    if (changed) save(context, ORDER_KEY, order)
    return order.map { Pair(it, titles[it] ?: it.substringAfter(":")) }
  }

  /**
   * Remembers the items of the menu and, once the person has changed the order, puts the items in
   * that order. Hidden items are removed. Never throws.
   */
  @JvmStatic
  fun apply(context: Context, items: MutableList<ContextMenuItem>) {
    try {
      val stored = load(context, ORDER_KEY)
      val hidden = load(context, HIDDEN_KEY).toHashSet()
      val keys = items.map { keyOf(context, it) }
      val titles = loadTitles(context)
      var titlesChanged = false
      var changed = false
      for ((i, k) in keys.withIndex()) {
        val t = items[i].title?.toString() ?: ""
        if (t.isNotEmpty() && titles[k] != t) {
          titles[k] = t
          titlesChanged = true
        }
        if (stored.contains(k)) continue
        var at = -1
        for (j in i + 1 until keys.size) {
          val idx = stored.indexOf(keys[j])
          if (idx >= 0) {
            at = idx
            break
          }
        }
        if (at >= 0) stored.add(at, k) else stored.add(k)
        changed = true
      }
      if (titlesChanged) saveTitles(context, titles)
      if (changed) save(context, ORDER_KEY, stored)
      val custom = isCustom(context)
      val pairs = items.indices.map { Pair(items[it], keys[it]) }
      val sorted = if (custom) pairs.sortedBy { val p = stored.indexOf(it.second); if (p < 0) Int.MAX_VALUE else p } else pairs
      val result = sorted.filter { !hidden.contains(it.second) }.map { it.first }
      if (result.isEmpty()) return
      items.clear()
      items.addAll(result)
    } catch (e: RuntimeException) {
      // the menu keeps its normal order
    }
  }
}
