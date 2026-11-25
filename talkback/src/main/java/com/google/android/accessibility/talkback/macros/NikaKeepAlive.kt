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
package com.google.android.accessibility.talkback.macros

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.NikaDiag
import com.google.android.accessibility.utils.SharedPreferencesUtils

/**
 * Nika: protection from being closed. The service shows a quiet notification and runs as a
 * foreground service, so the system keeps it when all the apps are closed.
 */
object NikaKeepAlive {
  const val PREF = "nika_keepalive"
  private const val ID = 101
  private const val CHANNEL = "nika_keepalive"

  fun enabled(service: Service): Boolean =
    SharedPreferencesUtils.getSharedPreferences(service).getBoolean(PREF, false)

  /** Starts or stops the notification as the setting says, and starts the voice trigger. */
  @JvmStatic
  fun apply(service: Service) {
    NikaDiag.log("nika", "version 44 started")
    try {
      if (enabled(service)) start(service) else stop(service)
    } catch (e: Throwable) {
      NikaDiag.log("keepalive", "failed: $e")
    }
    try {
      if (service is TalkBackService) NikaVoiceCmds.applyTrigger(service)
    } catch (e: Throwable) {
      NikaDiag.log("voicecmd", "trigger failed: $e")
    }
  }

  private fun start(service: Service) {
    val manager = service.getSystemService(NotificationManager::class.java) ?: return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL, service.getString(R.string.nika_ka_channel), NotificationManager.IMPORTANCE_LOW)
    )
    val notification =
      Notification.Builder(service, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle(service.getString(R.string.nika_ka_notification))
        .setOngoing(true)
        .build()
    if (Build.VERSION.SDK_INT >= 34) {
      service.startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      service.startForeground(ID, notification)
    }
  }

  private fun stop(service: Service) {
    service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
  }
}
