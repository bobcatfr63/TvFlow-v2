package com.tvflow.app.ui.login

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.success) {
        if (state.success) onLoggedIn()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
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
            Text("Connexion Xtream Codes", style = MaterialTheme.typography.titleLarge)
            state.pairingUrl?.let { pairingUrl ->
                Spacer(Modifier.height(18.dp))
                Text("Connexion rapide par téléphone", style = MaterialTheme.typography.titleMedium)
                Text("Scanne ce QR code avec l’appareil photo de ton téléphone, puis colle ton lien Xtream. Téléphone et TV doivent être sur le même réseau.")
                val qrBitmap = remember(pairingUrl) { createQrBitmap(pairingUrl) }
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR code de connexion TVFlow",
                    modifier = Modifier.size(240.dp).align(Alignment.CenterHorizontally)
                )
                Text("Le lien reste sur ton réseau local et le QR code ne contient pas tes identifiants.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = state.profileName,
                onValueChange = viewModel::setProfileName,
                label = { Text("Nom du profil (facultatif)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.server,
                onValueChange = viewModel::setServer,
                label = { Text("Serveur ou lien M3U get.php") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::setUsername,
                label = { Text("Utilisateur") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = { Text("Mot de passe") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = viewModel::login,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.loading) CircularProgressIndicator(modifier = Modifier.height(22.dp))
                else Text("Se connecter")
            }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
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
