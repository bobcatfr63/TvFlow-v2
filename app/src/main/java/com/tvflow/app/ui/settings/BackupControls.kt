package com.tvflow.app.ui.settings

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvflow.app.data.backup.PortableBackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val manager: PortableBackupManager,
    private val application: Application
) : ViewModel() {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    private var prepared: String? = null

    fun prepareExport(onReady: () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                prepared = withContext(Dispatchers.IO) { manager.exportPlain() }
                onReady()
            } catch (_: Exception) { message.value = "Impossible de préparer la sauvegarde." }
            finally { busy.value = false }
        }
    }

    fun writeExport(uri: android.net.Uri) {
        val data = prepared ?: return
        prepared = null
        viewModelScope.launch {
            busy.value = true
            try {
                withContext(Dispatchers.IO) {
                    application.contentResolver.openOutputStream(uri)?.use {
                        it.write(data.toByteArray(Charsets.UTF_8))
                    } ?: error("Impossible d'écrire le fichier")
                }
                message.value = "Sauvegarde enregistrée. Attention : identifiants IPTV et éventuelle clé TMDB non chiffrés."
            } catch (_: Exception) { message.value = "Enregistrement impossible." }
            finally { busy.value = false }
        }
    }

    fun restore(uri: android.net.Uri, password: String) {
        viewModelScope.launch {
            busy.value = true
            try {
                val count = try {
                    withContext(Dispatchers.IO) {
                        val bytes = application.contentResolver.openInputStream(uri)?.use { input ->
                            val buffer = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (true) {
                                val n = input.read(chunk)
                                if (n < 0) break
                                require(buffer.size() + n < 24 * 1024 * 1024)
                                buffer.write(chunk, 0, n)
                            }
                            buffer.toString("UTF-8")
                        } ?: error("Fichier indisponible")
                        manager.importBackup(bytes, password.takeIf { it.isNotBlank() }?.toCharArray())
                    }
                } finally { }
                message.value = "$count profil(s) restauré(s). Actualise le catalogue pour retrouver les chaînes."
            } catch (_: Exception) {
                message.value = "Restauration impossible : vérifie le fichier. Une ancienne sauvegarde chiffrée exige son mot de passe."
            } finally { busy.value = false }
        }
    }
}

@Composable
fun BackupControls(viewModel: BackupViewModel = hiltViewModel()) {
    var password by remember { mutableStateOf("") }
    var showLegacyPassword by remember { mutableStateOf(false) }
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) viewModel.writeExport(uri)
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restore(uri, password)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Sauvegarde de migration", style = MaterialTheme.typography.titleMedium)
        Text("Profils IPTV, favoris, organisation des catégories et clé API TMDB si configurée. Aucune saisie de mot de passe pour les nouvelles sauvegardes. Attention : le fichier contient tes identifiants IPTV et éventuelle clé TMDB non chiffrés.")
        TextButton(onClick = { showLegacyPassword = !showLegacyPassword }) {
            Text(if (showLegacyPassword) "Masquer l’option ancienne sauvegarde" else "Restaurer une ancienne sauvegarde chiffrée ?")
        }
        if (showLegacyPassword) {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mot de passe de l’ancienne sauvegarde") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                viewModel.prepareExport() { saveFile.launch("TVFlow-backup.tvfb") }
            }) { Text("Sauvegarder") }
            OutlinedButton(enabled = !busy, onClick = {
                openFile.launch(arrayOf("*/*"))
            }) { Text("Restaurer") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
