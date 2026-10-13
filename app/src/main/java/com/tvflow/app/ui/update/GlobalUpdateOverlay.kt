package com.tvflow.app.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tvflow.app.BuildConfig
import com.tvflow.app.data.UpdateDownloadState
import com.tvflow.app.data.InstalledReleaseNotes
import java.util.Locale

@Composable
fun GlobalUpdateOverlay(
    viewModel: GlobalUpdateViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val update = state.updateInfo ?: return
    val context = LocalContext.current
    val downloadState = state.downloadState
    val sizeMb = update.artifactSize / (1024f * 1024f)
    val readableNotes = remember(update.releaseNotes) {
        InstalledReleaseNotes.localizedItems(update.releaseNotes)
    }

    AlertDialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading) {
                viewModel.dismiss()
            }
        },
        title = {
            Text(
                "Mise à jour TVFlow disponible",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Nouvelle version : ${update.versionName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Version installée : ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Taille : %.1f Mo".format(Locale.getDefault(), sizeMb),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (readableNotes.isNotEmpty()) {
                    Text(
                        "Cette mise à jour apporte :",
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    readableNotes.forEach { note ->
                        Text(
                            "• $note",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                when (downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { downloadState.percent / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Téléchargement : ${downloadState.percent} %")
                    }
                    is UpdateDownloadState.Ready -> Text(
                        "APK vérifié et prêt à être installé.",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    is UpdateDownloadState.Error -> Text(
                        downloadState.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    UpdateDownloadState.Idle -> Text(
                        "Tu peux installer maintenant ou choisir Plus tard.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            UpdateDialogButton(
                text = when (downloadState) {
                    is UpdateDownloadState.Downloading -> "Téléchargement…"
                    is UpdateDownloadState.Ready -> "Installer"
                    is UpdateDownloadState.Error -> "Réessayer"
                    UpdateDownloadState.Idle -> "Mettre à jour"
                },
                enabled = downloadState !is UpdateDownloadState.Downloading,
                primary = true
            ) {
                if (downloadState is UpdateDownloadState.Ready) {
                    viewModel.install(context)
                } else {
                    viewModel.download()
                }
            }
        },
        dismissButton = {
            UpdateDialogButton(
                text = "Plus tard",
                enabled = downloadState !is UpdateDownloadState.Downloading,
                primary = false,
                onClick = viewModel::dismiss
            )
        }
    )
}

@Composable
private fun UpdateDialogButton(
    text: String,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val background = when {
        !enabled -> Color.Transparent
        focused -> Color.White
        primary -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.42f)
        focused -> MaterialTheme.colorScheme.background
        primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Text(
        text = text,
        modifier = Modifier
            .background(background, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                RoundedCornerShape(12.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
            .focusable(enabled = enabled, interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        fontWeight = FontWeight.Bold,
        color = foreground
    )
}
