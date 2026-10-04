package com.tvflow.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.local.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(settingsStore: SettingsStore) : ViewModel() {
    val accent: StateFlow<String> = settingsStore.playerPreferences
        .map { it.accent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "BLUE")
}
