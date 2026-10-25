package com.tvflow.app.ui.settings

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/** Keeps Settings usable even when a profile refresh is cancelled mid-request. */
internal suspend fun runSettingsBusyOperation(
    busy: MutableStateFlow<Boolean>,
    message: MutableStateFlow<String?>,
    successMessage: String?,
    block: suspend () -> Unit
) {
    busy.value = true
    try {
        block()
        message.value = successMessage
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        message.value = error.message ?: "Opération impossible"
    } finally {
        busy.value = false
    }
}
