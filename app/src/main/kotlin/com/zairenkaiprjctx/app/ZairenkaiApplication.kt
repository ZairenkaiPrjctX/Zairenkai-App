// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.app.Application
import android.content.Context
import com.zairenkaiprjctx.app.update.UpdateCheckJob
import com.zairenkaiprjctx.app.update.UpdatePreferences

class ZairenkaiApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // The daily update check (Settings > Updates), unless the user turned it off. Scheduling
        // is idempotent; the job only reads GitHub's release list.
        val auto = UpdatePreferences(getSharedPreferences("zairenkai", Context.MODE_PRIVATE)).settings.value.autoCheck
        UpdateCheckJob.schedule(this, auto)
    }
}
