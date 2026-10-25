package com.tvflow.app.ui.search

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Device-local successful Groq responses, NOT the shared provider quota. */
@Singleton
class AiUsageCounter @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("tvflow_ai_usage", Context.MODE_PRIVATE)
    private val guard = Any()
    private val _today = MutableStateFlow(0)
    val today: StateFlow<Int> = _today

    init { refresh() }

    fun refresh() = synchronized(guard) {
        val key = LocalDate.now().toString()
        _today.value = if (preferences.getString("day", "") == key)
            preferences.getInt("successes", 0) else 0
    }

    fun recordSuccess() = synchronized(guard) {
        val key = LocalDate.now().toString()
        val current = if (preferences.getString("day", "") == key)
            preferences.getInt("successes", 0) else 0
        val next = (current + 1).coerceAtMost(1_000_000)
        preferences.edit().putString("day", key).putInt("successes", next).apply()
        _today.value = next
    }
}
