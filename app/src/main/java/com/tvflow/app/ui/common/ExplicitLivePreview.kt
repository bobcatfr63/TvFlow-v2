package com.tvflow.app.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Focus never starts playback. First press selects, second waits 2s, next opens fullscreen. */
internal class ExplicitLivePreview(
    private val scope: CoroutineScope,
    private val publish: (Int?) -> Unit
) {
    private var focused: Int? = null
    private var armed = false
    private var ready: Int? = null
    private var pending: Job? = null

    fun focus(id: Int) {
        if (focused == id) return
        cancel()
        focused = id
    }

    /** True only when a preview is already active and fullscreen may be opened. */
    fun press(id: Int): Boolean {
        focus(id)
        if (ready == id) return true
        if (!armed) {
            armed = true
        } else if (pending == null) {
            pending = scope.launch {
                delay(2_000L)
                if (focused == id) {
                    ready = id
                    publish(id)
                }
                pending = null
            }
        }
        return false
    }

    fun cancel() {
        pending?.cancel()
        pending = null
        focused = null
        armed = false
        ready = null
        publish(null)
    }
}
