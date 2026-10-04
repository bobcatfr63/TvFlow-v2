@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.tvflow.app.ui.common

import android.content.Context
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

fun iptvMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("VLC/3.0.21 LibVLC/3.0.21")
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(8_000)
        .setReadTimeoutMs(15_000)
        .setDefaultRequestProperties(mapOf("Accept" to "*/*"))

    return DefaultMediaSourceFactory(context)
        .setDataSourceFactory(httpFactory)
}
