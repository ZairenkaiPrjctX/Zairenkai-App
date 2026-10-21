// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 ZairenkaiPrjctX contributors
package com.zairenkaiprjctx.app

import android.app.Application

class ZairenkaiApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
