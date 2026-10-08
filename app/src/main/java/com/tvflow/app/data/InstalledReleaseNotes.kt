package com.tvflow.app.data

import com.tvflow.app.BuildConfig

object InstalledReleaseNotes {
    val versionName: String
        get() = BuildConfig.VERSION_NAME

    val items: List<String>
        get() = BuildConfig.RELEASE_NOTES
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(8)
            .toList()
}
