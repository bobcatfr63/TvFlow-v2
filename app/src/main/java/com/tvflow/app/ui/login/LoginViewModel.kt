package com.tvflow.app.ui.login

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.repository.XtreamRepository
import com.tvflow.app.domain.XtreamCredentials
import com.tvflow.app.domain.parseXtreamM3uUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val profileName: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val outputFormat: String = "ts",
    val pairingUrl: String? = null,
    val loading: Boolean = false,
    val success: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: XtreamRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()
    private val pairingServer = LocalPairingServer(context) { link ->
        viewModelScope.launch {
            setServer(link)
            login()
        }
    }

    init {
        _state.value = _state.value.copy(pairingUrl = pairingServer.start())
    }

    fun setProfileName(value: String) { _state.value = _state.value.copy(profileName = value, error = null) }
    fun setServer(value: String) {
        val parsed = parseXtreamM3uUrl(value)
        _state.value = if (parsed != null) {
            _state.value.copy(
                server = parsed.serverUrl,
                username = parsed.username,
                password = parsed.password,
                outputFormat = parsed.outputFormat,
                error = null
            )
        } else {
            _state.value.copy(server = value, error = null)
        }
    }
    fun setUsername(value: String) { _state.value = _state.value.copy(username = value, error = null) }
    fun setPassword(value: String) { _state.value = _state.value.copy(password = value, error = null) }

    override fun onCleared() {
        pairingServer.stop()
        super.onCleared()
    }

    fun login() {
        val current = _state.value
        if (current.server.isBlank() || current.username.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(error = "Serveur, utilisateur et mot de passe sont obligatoires")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(loading = true, error = null)
            runCatching {
                repository.login(
                    XtreamCredentials(
                        current.server,
                        current.username,
                        current.password,
                        current.outputFormat
                    ),
                    current.profileName.takeIf { it.isNotBlank() }
                )
            }.onSuccess {
                _state.value = _state.value.copy(loading = false, success = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Connexion impossible")
            }
        }
    }
}
