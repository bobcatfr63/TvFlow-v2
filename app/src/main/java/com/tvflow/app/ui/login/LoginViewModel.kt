package com.tvflow.app.ui.login

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val sourceType: String = "XTREAM",
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

    fun setSourceType(value: String) { _state.value = _state.value.copy(sourceType = value, error = null) }
    fun useFrenchTestPlaylist() {
        _state.value = _state.value.copy(
            sourceType = "M3U_URL",
            server = "https://iptv-org.github.io/iptv/countries/fr.m3u",
            profileName = "TV France • iptv-org",
            error = null
        )
    }

    fun importLocalM3u(uri: Uri) {
        val current = _state.value
        viewModelScope.launch {
            _state.value = current.copy(loading = true, error = null)
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(out.size() + count <= 12_000_000) { "Fichier M3U trop volumineux" }
                            out.write(buffer, 0, count)
                        }
                        out.toByteArray()
                    } ?: error("Fichier M3U inaccessible")
                }
                repository.loginM3uFile(bytes, current.profileName.takeIf { it.isNotBlank() })
            }.onSuccess {
                _state.value = _state.value.copy(loading = false, success = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Import M3U impossible")
            }
        }
    }

    fun setProfileName(value: String) { _state.value = _state.value.copy(profileName = value, error = null) }
    fun setServer(value: String) {
        val parsed = if (_state.value.sourceType == "XTREAM") parseXtreamM3uUrl(value) else null
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
        if (current.sourceType == "M3U_FILE") {
            _state.value = current.copy(error = "Sélectionne un fichier M3U depuis cet appareil")
            return
        }
        if (current.server.isBlank() || (current.sourceType == "XTREAM" && (current.username.isBlank() || current.password.isBlank()))) {
            _state.value = current.copy(error = "Serveur, utilisateur et mot de passe sont obligatoires")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(loading = true, error = null)
            runCatching {
                if (current.sourceType == "M3U_URL") {
                    repository.loginM3uUrl(current.server.trim(), current.profileName.takeIf { it.isNotBlank() })
                } else {
                    repository.login(
                        XtreamCredentials(current.server, current.username, current.password, current.outputFormat),
                        current.profileName.takeIf { it.isNotBlank() }
                    )
                }
            }.onSuccess {
                _state.value = _state.value.copy(loading = false, success = true)
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Connexion impossible")
            }
        }
    }
}
