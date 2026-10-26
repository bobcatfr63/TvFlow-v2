package com.tvflow.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
    var requestEmail by remember { mutableStateOf("") }
    var sendingRequest by remember { mutableStateOf(false) }
    var requestResult by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
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
        Text("Pas encore de code ? Demande ton accès Groq et TMDB depuis TVFlow.")
        OutlinedTextField(value = requestEmail, onValueChange = { requestEmail = it.take(254) },
            label = { Text("Adresse e-mail pour la demande") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
        SettingsButton(enabled = !sendingRequest && requestEmail.contains('@'), onClick = {
            sendingRequest = true
            scope.launch {
                requestResult = com.tvflow.app.ui.search.TvFlowCloudRequest.send(requestEmail, android.os.Build.MODEL ?: "TVFlow")
                sendingRequest = false
            }
        }) { Text(if (sendingRequest) "Envoi en cours…" else "Demander un accès TVFlow Cloud") }
        if (requestResult.isNotBlank()) Text(requestResult)
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Masquer les paramètres avancés" else "Configuration avancée") }
        if (advanced) {
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
        }
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
