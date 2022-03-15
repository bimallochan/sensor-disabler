/*
 * Copyright 2013 two forty four a.m. LLC <http://www.twofortyfouram.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * <http://www.apache.org/licenses/LICENSE-2.0>
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package com.wardellbagby.sensordisabler.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wardellbagby.sensordisabler.bundle.BundleScrubber
import com.wardellbagby.sensordisabler.util.ProUtil
import com.wardellbagby.sensordisabler.util.saveSettings

class TaskerActionReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {

    if (taskerFireSettings != intent.action) {
      return
    }

    if (!ProUtil.isPro(context)) {
      Log.w(
        "Sensor Disabler: Tasker",
        "Not firing Tasker change for Sensor Disabler as Pro is not enabled."
      )
      return
    }

    BundleScrubber.scrub(intent)
    val bundle = intent.getBundleExtra(extraBundleKey) ?: return
    BundleScrubber.scrub(bundle)

    val sensor = bundle.getSensor(context) ?: return
    val modificationType = bundle.getModificationType(sensor) ?: return

    sensor.saveSettings(context, modificationType)
  }
}