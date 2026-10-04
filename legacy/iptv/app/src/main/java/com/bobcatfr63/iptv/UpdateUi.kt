package com.bobcatfr63.iptv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun UpdateUi(visible: Boolean = true) {
    val context = LocalContext.current.applicationContext
    val manager = remember { UpdateManager(context) }
    val scope = rememberCoroutineScope()

    var checking by remember { mutableStateOf(false) }
    var installing by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf<AppUpdate?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun check(silent: Boolean) {
        if (checking) return
        checking = true
        scope.launch {
            runCatching { manager.checkForUpdate() }
                .onSuccess {
                    update = it
                    if (!silent && it == null) message = "Bobcat IPTV est à jour."
                }
                .onFailure { if (!silent) message = it.message ?: "Vérification impossible" }
            checking = false
        }
    }

    LaunchedEffect(Unit) { check(silent = true) }

    // During playback the player owns the whole screen: no FAB, no updater dialog/overlay.
    if (!visible) return

    update?.let { available ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Mise à jour disponible") },
            text = {
                Text(buildString {
                    append("Bobcat IPTV ${available.versionName} est disponible.")
                    if (available.notes.isNotBlank()) {
                        append("\n\n")
                        append(available.notes)
                    }
                })
            },
            confirmButton = {
                Button(enabled = !installing, onClick = {
                    installing = true
                    scope.launch {
                        runCatching { manager.downloadAndInstall(available) }
                            .onSuccess { result ->
                                if (result == InstallResult.PERMISSION_REQUIRED) {
                                    message = "Autorise l’installation depuis Bobcat IPTV, puis appuie à nouveau sur Mettre à jour."
                                }
                            }
                            .onFailure { message = it.message ?: "Téléchargement impossible" }
                        installing = false
                    }
                }) { Text(if (installing) "Téléchargement…" else "Mettre à jour") }
            },
            dismissButton = { TextButton(onClick = { update = null }) { Text("Plus tard") } }
        )
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text("Bobcat IPTV") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }
        )
    }
}
