package com.tvflow.app.ui.startup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.local.CredentialsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartupState {
    data object Loading : StartupState
    data object LoggedOut : StartupState
    data object LoggedIn : StartupState
}

@HiltViewModel
class StartupViewModel @Inject constructor(
    credentialsStore: CredentialsStore,
    private val preloader: StartupPreloader
) : ViewModel() {
    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = if (credentialsStore.credentials.first() == null) {
                StartupState.LoggedOut
            } else {
                StartupState.LoggedIn
            }
            if (_state.value == StartupState.LoggedIn) {
                Log.i("TVFlowStartup", "Credentials present, scheduling selected-category prefetch")
                preloader.start()
            } else {
                Log.i("TVFlowStartup", "No IPTV profile configured; prefetch intentionally skipped")
            }
        }
    }
}
