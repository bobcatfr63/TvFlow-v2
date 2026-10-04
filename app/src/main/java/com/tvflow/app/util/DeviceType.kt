package com.tvflow.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

fun Context.isTelevisionUi(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
