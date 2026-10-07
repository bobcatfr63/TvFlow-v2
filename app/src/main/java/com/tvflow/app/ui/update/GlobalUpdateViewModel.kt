package com.tvflow.app.ui.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.AppUpdateChecker
import com.tvflow.app.data.AppUpdateInfo
import com.tvflow.app.data.AppUpdateManager
import com.tvflow.app.data.UpdateDownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GlobalUpdateUiState(
    val updateInfo: AppUpdateInfo? = null,
    val downloadState: UpdateDownloadState = UpdateDownloadState.Idle
)

@HiltViewModel
class GlobalUpdateViewModel @Inject constructor(
    private val updateChecker: AppUpdateChecker,
    private val updateManager: AppUpdateManager
) : ViewModel() {
    private val _state = MutableStateFlow(GlobalUpdateUiState())
    val state: StateFlow<GlobalUpdateUiState> = _state.asStateFlow()

    init {
        checkForUpdate()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            repeat(3) { attempt ->
                val update = updateChecker.check()
                if (update != null) {
                    _state.value = _state.value.copy(updateInfo = update)
                    return@launch
                }
                if (attempt < 2) delay(2_000L)
            }
        }
    }

    fun dismiss() {
        _state.value = GlobalUpdateUiState()
    }

    fun download() {
        val info = _state.value.updateInfo ?: return
        if (_state.value.downloadState is UpdateDownloadState.Downloading) return

        viewModelScope.launch {
            _state.value = _state.value.copy(downloadState = UpdateDownloadState.Downloading(0))
            runCatching {
                updateManager.download(info) { percent ->
                    _state.value = _state.value.copy(
                        downloadState = UpdateDownloadState.Downloading(percent)
                    )
                }
            }.onSuccess { apk ->
                _state.value = _state.value.copy(
                    downloadState = UpdateDownloadState.Ready(apk.absolutePath)
                )
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    downloadState = UpdateDownloadState.Error(
                        error.message ?: "Échec de la mise à jour."
                    )
                )
            }
        }
    }

    fun install(context: Context) {
        val ready = _state.value.downloadState as? UpdateDownloadState.Ready ?: return
        runCatching {
            val intent = if (updateManager.canInstallPackages()) {
                updateManager.installIntent(ready.apkPath)
            } else {
                updateManager.unknownSourcesIntent() ?: updateManager.installIntent(ready.apkPath)
            }
            context.startActivity(intent)
        }.onFailure { error ->
            _state.value = _state.value.copy(
                downloadState = UpdateDownloadState.Error(
                    error.message ?: "Impossible d’ouvrir l’installation."
                )
            )
        }
    }
}
