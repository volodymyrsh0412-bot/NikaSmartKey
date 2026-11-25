package com.google.android.accessibility.talkback.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.accessibility.talkback.BuildConfig
import com.google.android.accessibility.material.preference.AccessibilitySuitePreferenceCategory
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.utils.FormFactorUtils
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper

/** Nika: the "What's new" and "Send feedback" items in the main settings. */
object NikaSettings {
  private const val FEEDBACK_TELEGRAM = "https://t.me/Volodymyr0412sh"
  private const val FEEDBACK_EMAIL = "volodymyrsh0412@gmail.com"
  private const val KEY_WHATS_NEW = "pref_nika_whats_new"
  private const val KEY_FEEDBACK = "pref_nika_feedback"

  @JvmStatic
  fun setUpPreferences(fragment: PreferenceFragmentCompat) {
    fragment
      .findPreference<AccessibilitySuitePreferenceCategory>(
        fragment.getString(R.string.pref_category_typing_key)
      )
      ?.setCollapsible(
        fragment.getString(R.string.category_collapsed),
        fragment.getString(R.string.category_expanded),
      )
    fragment
      .findPreference<AccessibilitySuitePreferenceCategory>(
        fragment.getString(R.string.pref_category_advanced_key)
      )
      ?.setCollapsible(
        fragment.getString(R.string.category_collapsed),
        fragment.getString(R.string.category_expanded),
      )
    fragment
      .findPreference<AccessibilitySuitePreferenceCategory>(
        fragment.getString(R.string.pref_category_apps_key)
      )
      ?.setCollapsible(
        fragment.getString(R.string.category_collapsed),
        fragment.getString(R.string.category_expanded),
      )
    // Gemini settings live in the "Artificial intelligence" section now.
    fragment
      .findPreference<Preference>(fragment.getString(R.string.pref_gemini_settings_key))
      ?.isVisible = false
    fragment.findPreference<Preference>(KEY_WHATS_NEW)?.setOnPreferenceClickListener {
      fragment.activity?.let { showWhatsNew(it) }
      true
    }
    fragment.findPreference<Preference>(KEY_FEEDBACK)?.setOnPreferenceClickListener {
      fragment.activity?.let { sendFeedback(it) }
      true
    }
  }

  /** On a watch there are no volume keys to set up, so that item of the key screen is removed. */
  @JvmStatic
  fun hideForWatch(fragment: PreferenceFragmentCompat) {
    if (FormFactorUtils.isAndroidWear()) {
      fragment.findPreference<Preference>("pref_nika_volume")?.let { it.parent?.removePreference(it) }
    }
  }

  private fun showWhatsNew(activity: Activity) {
    A11yAlertDialogWrapper.materialDialogBuilder(activity)
      .setTitle(R.string.nika_whats_new_title)
      .setMessage(R.string.nika_whats_new_message)
      .setPositiveButton(android.R.string.ok, null)
      .create()
      .show()
  }

  @JvmStatic
  fun sendFeedback(activity: Activity) {
    val subject = activity.getString(R.string.nika_feedback_subject, BuildConfig.COMMIT_COUNT)
    val body =
      activity.getString(
        R.string.nika_feedback_body,
        Build.MANUFACTURER,
        Build.MODEL,
        Build.VERSION.RELEASE,
        BuildConfig.COMMIT_COUNT,
      )
    val intent =
      Intent(Intent.ACTION_SENDTO)
        .setData(Uri.parse("mailto:"))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(FEEDBACK_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    A11yAlertDialogWrapper.materialDialogBuilder(activity)
      .setTitle(R.string.nika_feedback_choose_title)
      .setPositiveButton(activity.getString(R.string.nika_feedback_by_email)) { _, _ ->
        sendEmail(activity, intent)
      }
      .setNeutralButton(activity.getString(R.string.nika_feedback_by_telegram)) { _, _ ->
        try {
          activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_TELEGRAM)))
        } catch (e: ActivityNotFoundException) {
          sendEmail(activity, intent)
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .create()
      .show()
  }

  private fun sendEmail(activity: Activity, intent: Intent) {
    try {
      activity.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.setPrimaryClip(ClipData.newPlainText("email", FEEDBACK_EMAIL))
      Toast.makeText(
          activity,
          activity.getString(R.string.nika_feedback_copied, FEEDBACK_EMAIL),
          Toast.LENGTH_LONG,
        )
        .show()
    }
  }
}
