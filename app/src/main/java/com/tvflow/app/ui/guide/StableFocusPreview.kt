package com.tvflow.app.ui.guide

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Main-thread owner of the single pending preview, shared by channel and EPG focus. */
internal class StableFocusPreview(
    private val scope: CoroutineScope,
    private val publish: (Int?) -> Unit
) {
    private var pending: Job? = null
    private var focused: Int? = null

    fun focus(id: Int) {
        if (focused == id) return
        cancel()
        focused = id
        pending = scope.launch {
            delay(2_000)
            if (focused == id) publish(id)
        }
    }

    fun blur(id: Int) { if (focused == id) cancel() }

    fun cancel() {
        pending?.cancel()
        pending = null
        focused = null
        publish(null)
    }

    fun playNow(id: Int) {
        pending?.cancel()
        focused = id
        publish(id)
    }
}
