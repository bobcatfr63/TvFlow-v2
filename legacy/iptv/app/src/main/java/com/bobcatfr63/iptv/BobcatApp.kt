package com.bobcatfr63.iptv

import android.app.Application

class BobcatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
