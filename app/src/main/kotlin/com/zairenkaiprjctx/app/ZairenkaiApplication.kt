package com.zairenkaiprjctx.app

import android.app.Application

class ZairenkaiApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
