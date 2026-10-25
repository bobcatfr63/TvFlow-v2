package com.tvflow.app.ui.login

import android.graphics.Bitmap
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.runtime.remember
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.tvflow.app.BuildConfig

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onOpenDemo: (() -> Unit)? = null,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val isTv = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importLocalM3u(uri)
    }

    LaunchedEffect(state.success) {
        if (state.success) onLoggedIn()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("TVFlow • Smart Loading", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
                Text("📺 Chaînes TV : ${state.loadedChannels?.let { "$it enregistrées" } ?: "en attente"}")
                Spacer(Modifier.height(10.dp))
                Text("🎬 Films : ${state.receivedFilms?.let { "$it reçus" } ?: "réception en attente"} • ${state.loadedFilms?.let { "$it enregistrés" } ?: "enregistrement en attente"}")
                Spacer(Modifier.height(10.dp))
                Text("📺 Séries : ${state.receivedSeries?.let { "$it reçues" } ?: "réception en attente"} • ${state.loadedSeries?.let { "$it enregistrées" } ?: "enregistrement en attente"}")
                Spacer(Modifier.height(24.dp))
                Text(state.loadingStage.ifBlank { "Connexion au serveur IPTV…" })
                Spacer(Modifier.height(12.dp))
                Text("Les compteurs « reçus » apparaissent après le téléchargement de chaque liste ; les compteurs « enregistrés » après son stockage.", style = MaterialTheme.typography.bodySmall)
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)) {
            Text("TVFlow", style = MaterialTheme.typography.displayMedium)
            Text("Ajouter une source IPTV", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("XTREAM" to "Xtream", "M3U_URL" to "M3U URL", "M3U_FILE" to "Fichier M3U").forEach { (type, title) ->
                    if (state.sourceType == type) Button(onClick = { viewModel.setSourceType(type) }) { Text(title) }
                    else OutlinedButton(onClick = { viewModel.setSourceType(type) }) { Text(title) }
                }
                OutlinedButton(onClick = viewModel::useFrenchTestPlaylist, enabled = !state.loading) { Text("Test") }
            }
            if (state.sourceType == "XTREAM") state.pairingUrl?.let { pairingUrl ->
                Spacer(Modifier.height(if (isTv) 8.dp else 18.dp))
                Text("Connexion rapide par téléphone", style = MaterialTheme.typography.titleMedium)
                Text("Scanne ce QR code avec l’appareil photo de ton téléphone, puis colle ton lien Xtream. Téléphone et TV doivent être sur le même réseau.")
                val qrBitmap = remember(pairingUrl) { createQrBitmap(pairingUrl) }
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR code de connexion TVFlow",
                    modifier = Modifier.size(if (isTv) 164.dp else 240.dp).align(Alignment.CenterHorizontally)
                )
                Text("Le lien reste sur ton réseau local et le QR code ne contient pas tes identifiants.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(if (isTv) 12.dp else 28.dp))

            OutlinedTextField(
                value = state.profileName,
                onValueChange = viewModel::setProfileName,
                label = { Text("Nom du profil (facultatif)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (state.sourceType != "M3U_FILE") OutlinedTextField(
                value = state.server,
                onValueChange = viewModel::setServer,
                label = { Text(if (state.sourceType == "M3U_URL") "URL de la playlist M3U" else "Serveur ou lien Xtream get.php") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (state.sourceType == "XTREAM") OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::setUsername,
                label = { Text("Utilisateur") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            if (state.sourceType == "XTREAM") OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Mot de passe") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            if (state.sourceType == "M3U_URL" && state.server == "https://iptv-org.github.io/iptv/countries/fr.m3u") {
                Text("Source test : chaînes françaises publiques (iptv-org)", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            if (state.sourceType == "M3U_FILE") {
                Button(
                    onClick = { filePicker.launch(arrayOf("audio/x-mpegurl", "application/x-mpegURL", "text/plain", "application/octet-stream", "*/*")) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choisir un fichier .m3u") }
            } else Button(
                onClick = viewModel::login,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.loading) "Synchronisation du catalogue en cours…" else "Se connecter")
            }

            if (BuildConfig.IS_PLAY_STORE && onOpenDemo != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenDemo,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Découvrir TVFlow sans identifiants")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mode de démonstration hors ligne : aucun compte, serveur ou achat requis.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (state.loading) {
                Spacer(Modifier.height(20.dp))
                Text("Smart Loading • Préparation du catalogue", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                val stage = state.loadingStage
                val loadingFilms = stage.contains("films", ignoreCase = true)
                val loadingLive = stage.contains("chaînes", ignoreCase = true)
                val ready = stage.contains("Catalogue prêt") || stage.contains("Catalogue partiel")
                Text("📺 Chaînes TV : " + if (ready) "synchronisées" else if (loadingLive) "chargement en cours" else "en attente")
                Text("🎬 Films : " + if (ready) "synchronisés" else if (loadingFilms) "chargement en cours" else "en attente")
                Text("📺 Séries : " + if (ready) "synchronisées" else if (loadingFilms) "chargement en cours" else "en attente")
                Text(stage.ifBlank { "Connexion au serveur…" }, modifier = Modifier.padding(top = 12.dp))
                Text("Les nombres affichés proviennent du catalogue enregistré. Aucun pourcentage estimé.", style = MaterialTheme.typography.bodySmall)
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            }
        }
        }
    }
}
private fun createQrBitmap(content: String, size: Int = 512): Bitmap {
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}
