package com.tvflow.app.connect

import android.content.res.Configuration
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun TvFlowSendButton(
    transfer: () -> PlaybackTransfer?,
    modifier: Modifier = Modifier,
    onSent: () -> Unit = {}
) {
    val context = LocalContext.current
    val isTv = androidx.compose.ui.platform.LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    if (isTv) return

    val scope = rememberCoroutineScope()
    var discovering by remember { mutableStateOf(false) }
    var choices by remember { mutableStateOf<List<TvFlowTv>>(emptyList()) }

    fun sendTo(tv: TvFlowTv) {
        val payload = transfer() ?: return
        scope.launch {
            val ok = TvFlowConnect.send(tv, payload)
            Toast.makeText(
                context,
                if (ok) "Lecture envoyée vers " + tv.name else "Envoi vers TVFlow impossible",
                Toast.LENGTH_SHORT
            ).show()
            if (ok) onSent()
        }
    }

    Button(
        onClick = {
            if (discovering) return@Button
            discovering = true
            scope.launch {
                val tvs = TvFlowConnect.discoverTvs(context)
                discovering = false
                when (tvs.size) {
                    0 -> Toast.makeText(context, "Aucune TVFlow TV trouvée", Toast.LENGTH_SHORT).show()
                    1 -> sendTo(tvs.first())
                    else -> choices = tvs
                }
            }
        },
        modifier = modifier,
        enabled = !discovering,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(if (discovering) "…" else "TV")
    }

    if (choices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { choices = emptyList() },
            title = { Text("Envoyer vers TVFlow") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    choices.forEach { tv ->
                        TextButton(
                            onClick = {
                                choices = emptyList()
                                sendTo(tv)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(tv.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { choices = emptyList() }) { Text("Annuler") }
            }
        )
    }
}
