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

package com.google.android.accessibility.talkback.scripting

import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceScreen
import com.google.android.accessibility.material.preference.AccessibilitySuitePreferenceCategory
import com.google.android.accessibility.talkback.R

/** Nika: the page of the AI agent. Settings, permissions and commands; permissions and commands fold. */
class NikaAgentFragment : ScriptScreenFragment() {

  public override fun getTitle(): CharSequence = getText(R.string.nika_ai_agent_title)

  override fun state(): Any? = store.find(scriptId)?.let { it.manifest to it.revision }

  override fun build(screen: PreferenceScreen) {
    val script = orClose(store.find(scriptId)) ?: return
    val manifest = script.manifest
    screen.switch(getString(R.string.nika_agent_enabled), null, script.enabled) {
      store.setEnabled(scriptId, it)
    }
    if (manifest.settings.isNotEmpty()) {
      screen.link(
        getString(R.string.nika_agent_settings),
        null,
        ScriptSettingsFragment::class.java,
        scriptId,
      )
    }
    val permissions = foldable(screen, "nika_agent_cat_permissions", R.string.nika_agent_perm_title)
    val declared = ScriptPermission.entries.filter { it in manifest.permissions }
    permissions.info(getString(R.string.nika_agent_perm_note))
    for (permission in declared) {
      permissions.switch(requireContext().permissionText(permission), null, script.has(permission)) {
        store.setGranted(scriptId, permission, it)
      }
    }
    fold(permissions)
    if (manifest.commands.isNotEmpty()) {
      val commands = foldable(screen, "nika_agent_cat_commands", R.string.nika_agent_cmd_title)
      for (command in manifest.commands) {
        val summary =
          store
            .bindingsOf(scriptId, command)
            .filter { store.isBindingOn(scriptId, command.id, it) }
            .joinToString(", ") { requireContext().bindingText(it) }
            .ifEmpty { getString(R.string.script_binding_none) }
        commands.link(command.title, summary, ScriptCommandFragment::class.java, scriptId) {
          putString(ScriptCommandFragment.ARG_COMMAND, command.id)
        }
      }
      fold(commands)
    }
  }

  private fun foldable(
    screen: PreferenceGroup,
    key: String,
    title: Int,
  ): AccessibilitySuitePreferenceCategory {
    val category = AccessibilitySuitePreferenceCategory(requireContext())
    category.key = key
    category.title = getString(title)
    category.isIconSpaceReserved = false
    screen.addPreference(category)
    return category
  }

  private fun fold(category: AccessibilitySuitePreferenceCategory) =
    category.setCollapsible(
      getString(R.string.category_collapsed),
      getString(R.string.category_expanded),
    )
}
