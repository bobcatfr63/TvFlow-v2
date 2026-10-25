package com.tvflow.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tvflow.app.ui.search.AiGatewayConfig
import com.tvflow.app.ui.search.GroqIntentClient
import kotlinx.coroutines.launch

@Composable
fun AiGatewayControls() {
    val context = LocalContext.current
    val config = remember(context) { AiGatewayConfig(context) }
    var endpoint by remember { mutableStateOf(config.endpoint) }
    var token by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("TVFlow AI 2.0 — Groq facultatif")
        Text("Sans passerelle disponible, la recherche locale reste active. Configure uniquement ton accès privé.")
        Text("Accès TVFlow AI Cloud (bêta privée)")
        OutlinedTextField(value = inviteCode, onValueChange = { inviteCode = it }, singleLine = true,
            label = { Text("Code d'invitation") }, modifier = Modifier.fillMaxWidth())
        SettingsButton(enabled = !checking && inviteCode.isNotBlank(), onClick = {
            checking = true
            status = "Activation TVFlow AI Cloud en cours…"
            scope.launch {
                val activated = com.tvflow.app.ui.search.TvFlowCloudEnrollment(context).activate(inviteCode)
                status = if (activated) "TVFlow AI Cloud activé. L'ancienne configuration a été remplacée uniquement après confirmation du serveur."
                    else "Invitation invalide, déjà utilisée, ou serveur indisponible. Configuration précédente conservée."
                if (activated) { inviteCode = ""; endpoint = config.endpoint }
                checking = false
            }
        }) { Text("Activer TVFlow AI Cloud") }
        OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, singleLine = true,
            label = { Text("Adresse HTTPS de la passerelle") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = token, onValueChange = { token = it }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
            label = { Text("Jeton privé de la passerelle (pas la clé Groq)") }, modifier = Modifier.fillMaxWidth())
        SettingsButton(onClick = {
            status = runCatching { config.save(endpoint.trim(), token.trim()); token = "" }
                .fold({ "Configuration enregistrée" }, { "Adresse ou jeton invalide, ou stockage sécurisé indisponible" })
        }) { Text("Enregistrer la passerelle") }
        SettingsButton(enabled = !checking, onClick = {
            checking = true
            status = "Test de la passerelle en cours…"
            scope.launch {
                val result = runCatching { GroqIntentClient(context, com.tvflow.app.ui.search.AiUsageCounter(context)).rewrite("une comédie française") }.getOrNull()
                status = if (result != null) "Groq actif : réponse reçue. La disponibilité du catalogue reste vérifiée localement."
                    else "Passerelle non validée : vérifier adresse, jeton, connexion ou quota. Recherche locale disponible."
                checking = false
            }
        }) { Text(if (checking) "Vérification…" else "Tester la connexion Groq") }
        SettingsButton(onClick = { config.clear(); endpoint = ""; token = ""; status = "Recherche locale activée" }) {
            Text("Utiliser uniquement le moteur local")
        }
        if (status.isNotBlank()) Text(status)
    }
}
