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
package com.google.android.accessibility.talkback.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import com.google.android.accessibility.talkback.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Nika: the full copy in one zip file. It holds the settings (settings.json), the sound themes
 * and the scripts. On restore the person chooses which groups to bring back.
 */
object NikaBackup {
  const val FILE_NAME = "nika_backup.zip"
  private const val SETTINGS = "settings.json"
  private const val THEMES = "sound_themes/"
  private const val SCRIPTS = "scripts/"

  /** The groups, in the order of the choice list. */
  val GROUPS =
    listOf(
      "main" to R.string.nika_bk_g_main,
      "gestures" to R.string.nika_bk_g_gestures,
      "macros" to R.string.nika_bk_g_macros,
      "speech" to R.string.nika_bk_g_speech,
      "notif" to R.string.nika_bk_g_notif,
      "themes" to R.string.nika_bk_g_themes,
      "scripts" to R.string.nika_bk_g_scripts,
    )

  private val GESTURE_PREFIXES = listOf("pref_shortcut", "nika_app:", "nika_motion_", "nika_iph_", "nika_side_", "nika_gesture_profiles")

  /** The group a setting belongs to. */
  fun groupOf(store: String, key: String): String =
    when {
      store != SettingsBackup.STORE_MAIN -> "main"
      key == "pref_sound_theme" -> "themes"
      key.startsWith("nika_macro") -> "macros"
      key.startsWith("nika_notif") -> "notif"
      GESTURE_PREFIXES.any { key.startsWith(it) } -> "gestures"
      key.contains("tts") || key.contains("voice") || key.contains("speech") || key.contains("nika_vr") ||
        key.contains("language_switch") || key.contains("nika_voice_rules") -> "speech"
      else -> "main"
    }

  private fun themesDir(context: Context): File {
    val storage = ContextCompat.createDeviceProtectedStorageContext(context) ?: context
    return File(storage.filesDir, "sound_themes")
  }

  private fun scriptsDir(context: Context): File = File(context.filesDir, "scripts")

  fun export(context: Context, out: OutputStream) {
    ZipOutputStream(out).use { zip ->
      zip.putNextEntry(ZipEntry(SETTINGS))
      zip.write(SettingsBackup.export(SettingsBackup.stores(context)).toByteArray())
      zip.closeEntry()
      addDir(zip, themesDir(context), THEMES)
      addDir(zip, scriptsDir(context), SCRIPTS)
    }
  }

  private fun addDir(zip: ZipOutputStream, dir: File, prefix: String) {
    if (!dir.isDirectory) return
    dir.walkTopDown().filter { it.isFile }.forEach { f ->
      if (f.length() > 20_000_000L) return@forEach
      val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
      zip.putNextEntry(ZipEntry(prefix + rel))
      f.inputStream().use { it.copyTo(zip) }
      zip.closeEntry()
    }
  }

  /** What a backup file holds: the settings text and the file entries. */
  class Content(val settings: String?, val files: Map<String, ByteArray>)

  /** Reads a zip (or an old plain json) backup. Returns null if it is neither. */
  fun read(input: InputStream): Content? {
    val bytes = input.readBytes()
    if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
      var settings: String? = null
      val files = HashMap<String, ByteArray>()
      ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
          val e = zip.nextEntry ?: break
          if (e.isDirectory) continue
          val buf = ByteArrayOutputStream()
          zip.copyTo(buf)
          if (e.name == SETTINGS) settings = buf.toString("UTF-8") else files[e.name] = buf.toByteArray()
        }
      }
      return if (settings == null) null else Content(settings, files)
    }
    val text = bytes.decodeToString()
    return try {
      JSONObject(text)
      Content(text, emptyMap())
    } catch (e: JSONException) {
      null
    }
  }

  /** The groups that have something in the backup. */
  fun available(content: Content): List<String> {
    val found = LinkedHashSet<String>()
    try {
      val stores = JSONObject(content.settings ?: "{}").getJSONObject("stores")
      for (name in stores.keys()) {
        val entries = stores.getJSONObject(name)
        for (key in entries.keys()) found.add(groupOf(name, key))
      }
    } catch (e: JSONException) {
      // nothing
    }
    if (content.files.keys.any { it.startsWith(THEMES) }) found.add("themes")
    if (content.files.keys.any { it.startsWith(SCRIPTS) }) found.add("scripts")
    return GROUPS.map { it.first }.filter { it in found }
  }

  /** Restores the chosen groups. Returns false if the settings could not be read. */
  fun restore(context: Context, content: Content, chosen: Set<String>): Boolean {
    val stores = SettingsBackup.stores(context)
    try {
      val backup = JSONObject(content.settings ?: return false)
      val storesJson = backup.getJSONObject("stores")
      for ((name, prefs) in stores) {
        val entries = storesJson.optJSONObject(name) ?: continue
        applyStore(name, prefs, entries, chosen)
      }
    } catch (e: JSONException) {
      return false
    }
    if ("themes" in chosen) restoreFiles(content, THEMES, themesDir(context))
    if ("scripts" in chosen) restoreFiles(content, SCRIPTS, scriptsDir(context))
    return true
  }

  private fun applyStore(name: String, prefs: SharedPreferences, entries: JSONObject, chosen: Set<String>) {
    val editor = prefs.edit()
    for (key in prefs.all.keys.toList()) {
      if (SettingsBackup.isPortable(key) && groupOf(name, key) in chosen && !entries.has(key)) editor.remove(key)
    }
    for (key in entries.keys()) {
      if (!SettingsBackup.isPortable(key) || groupOf(name, key) !in chosen) continue
      val e = entries.getJSONObject(key)
      when (e.getString("t")) {
        "bool" -> editor.putBoolean(key, e.getBoolean("v"))
        "int" -> editor.putInt(key, e.getInt("v"))
        "long" -> editor.putLong(key, e.getLong("v"))
        "float" -> editor.putFloat(key, e.getDouble("v").toFloat())
        "string" -> editor.putString(key, e.getString("v"))
        "set" -> {
          val a: JSONArray = e.getJSONArray("v")
          editor.putStringSet(key, (0 until a.length()).map { a.getString(it) }.toSet())
        }
      }
    }
    editor.apply()
  }

  private fun restoreFiles(content: Content, prefix: String, dir: File) {
    val entries = content.files.filterKeys { it.startsWith(prefix) }
    if (entries.isEmpty()) return
    dir.deleteRecursively()
    dir.mkdirs()
    val base = dir.canonicalFile
    for ((name, data) in entries) {
      val target = File(dir, name.removePrefix(prefix)).canonicalFile
      // A name that leads out of the folder is skipped.
      if (!target.path.startsWith(base.path + File.separator)) continue
      target.parentFile?.mkdirs()
      target.writeBytes(data)
    }
  }
}
