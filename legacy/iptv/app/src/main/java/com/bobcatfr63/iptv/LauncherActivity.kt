package com.bobcatfr63.iptv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val LauncherBackground = Color(0xFF07111F)
private val LauncherSurface = Color(0xFF102033)
private val LauncherAccent = Color(0xFF38BDF8)
private val LauncherText = Color(0xFFF8FAFC)
private val LauncherSecondary = Color(0xFFD7E2EE)
private val LauncherOutline = Color(0xFF6B829A)

class LauncherActivity : ComponentActivity() {
    private lateinit var credentialStore: CredentialStore
    private lateinit var client: XtreamClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        credentialStore = CredentialStore(this)
        client = XtreamClient(this)
        intent?.data?.takeIf { it.scheme == "bobcatiptv" }?.let { uri ->
            when (uri.host) {
                "pair" -> uri.lastPathSegment?.takeIf { it.isNotBlank() }?.let { BobcatCloud(this).pairTv(it) }
                "import" -> CredentialTransfer.decode(uri)?.let { CredentialStore(this).save(it) }
            }
        }

        if (credentialStore.load() != null) {
            setContent {
                MaterialTheme(colorScheme = darkColorScheme(primary = LauncherAccent, background = LauncherBackground)) {
                    ProfileSelectionScreen()
                }
            }
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = LauncherAccent,
                    onPrimary = Color(0xFF00131D),
                    background = LauncherBackground,
                    onBackground = LauncherText,
                    surface = LauncherSurface,
                    onSurface = LauncherText,
                    surfaceVariant = Color(0xFF172A40),
                    onSurfaceVariant = LauncherSecondary,
                    outline = LauncherOutline,
                    error = Color(0xFFFF7474)
                )
            ) {
                ResponsiveLogin()
            }
        }
    }

    @Composable
    private fun ProfileSelectionScreen() {
        val store = remember { ProfileStore(this) }
        var profiles by remember { mutableStateOf(store.profiles()) }
        var adding by remember { mutableStateOf(false) }
        var newName by remember { mutableStateOf("") }

        fun enter(profile: ViewerProfile) {
            store.select(profile)
            openMain()
        }

        Box(Modifier.fillMaxSize().background(LauncherBackground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Qui regarde ?", color = LauncherText, style = MaterialTheme.typography.displaySmall)
                Text("Choisissez votre espace Bobcat", color = LauncherSecondary)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        val color = when (profile.kind) {
                            ProfileKind.CHILD -> Color(0xFF22C55E)
                            ProfileKind.TEEN -> Color(0xFF8B5CF6)
                            ProfileKind.ADULT -> LauncherAccent
                            ProfileKind.CUSTOM -> Color(0xFFF59E0B)
                        }
                        Surface(
                            onClick = { enter(profile) },
                            modifier = Modifier.width(132.dp).height(154.dp),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                            color = LauncherSurface,
                            border = androidx.compose.foundation.BorderStroke(2.dp, color)
                        ) {
                            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Text(profile.name.take(1).uppercase(), color = color, style = MaterialTheme.typography.displayMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(profile.name, color = LauncherText, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    item { Surface(
                        onClick = { adding = true },
                        modifier = Modifier.width(112.dp).height(132.dp),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                        color = LauncherSurface
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text("+", color = LauncherAccent, style = MaterialTheme.typography.displayMedium)
                            Text("Ajouter", color = LauncherText)
                        }
                    } }
                }
            }
        }

        if (adding) {
            AlertDialog(
                onDismissRequest = { adding = false },
                title = { Text("Ajouter un profil") },
                text = { OutlinedTextField(value = newName, onValueChange = { newName = it.take(16) }, label = { Text("Nom du profil") }, singleLine = true) },
                confirmButton = {
                    Button(enabled = newName.isNotBlank(), onClick = {
                        profiles = store.profiles() + store.add(newName)
                        newName = ""
                        adding = false
                    }) { Text("Ajouter") }
                },
                dismissButton = { TextButton(onClick = { adding = false }) { Text("Annuler") } }
            )
        }
    }

    @Composable
    private fun StartupScreen() {
        Box(
            Modifier.fillMaxSize().background(LauncherBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Surface(
                    modifier = Modifier.size(112.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                    color = LauncherAccent
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("B", color = Color(0xFF00131D), style = MaterialTheme.typography.displayLarge)
                    }
                }
                Text("BOBCAT IPTV", color = LauncherText, style = MaterialTheme.typography.headlineLarge)
                Text("Votre cinéma. Votre télévision.", color = LauncherSecondary, style = MaterialTheme.typography.titleMedium)
                LinearProgressIndicator(
                    modifier = Modifier.width(260.dp),
                    color = LauncherAccent,
                    trackColor = LauncherSurface
                )
            }
        }
    }

    @Composable
    private fun ResponsiveLogin() {
        val presetServers = remember {
            listOf(
                "http://line.ca1-dino.xyz",
                "http://vpn.ca1-dino.xyz",
                "http://line.dndnscloud.ru",
                "http://vpn.dndnscloud.ru"
            )
        }
        var server by remember { mutableStateOf(presetServers.first()) }
        var serverMenuExpanded by remember { mutableStateOf(false) }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scrollState = rememberScrollState()

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(LauncherBackground)
        ) {
            val compact = maxHeight < 520.dp
            val horizontalPadding = if (maxWidth < 700.dp) 20.dp else 32.dp
            val verticalPadding = if (compact) 12.dp else 28.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 620.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
                ) {
                    Text(
                        "Bobcat IPTV",
                        color = LauncherText,
                        style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge
                    )
                    Text("Connexion Xtream", color = LauncherSecondary)

                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = LauncherText,
                        unfocusedTextColor = LauncherText,
                        focusedBorderColor = LauncherAccent,
                        unfocusedBorderColor = LauncherOutline,
                        focusedLabelColor = LauncherAccent,
                        unfocusedLabelColor = LauncherSecondary,
                        cursorColor = LauncherAccent
                    )

                    Text("Serveur", color = LauncherSecondary, style = MaterialTheme.typography.labelLarge)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { serverMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = LauncherText)
                        ) {
                            Text(server, modifier = Modifier.weight(1f))
                            Text("▼", color = LauncherAccent)
                        }
                        DropdownMenu(
                            expanded = serverMenuExpanded,
                            onDismissRequest = { serverMenuExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            presetServers.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset) },
                                    onClick = {
                                        server = preset
                                        serverMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = server,
                        onValueChange = { server = it },
                        label = { Text("Serveur personnalisé") },
                        supportingText = { Text("Tu peux aussi saisir une autre URL Xtream.") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Utilisateur") },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mot de passe") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    Button(
                        onClick = {
                            val credentials = Credentials(server.trim(), username.trim(), password)
                            loading = true
                            error = null
                            lifecycleScope.launch {
                                try {
                                    val auth = client.authenticate(credentials)
                                    if (auth.userInfo?.auth != 1) {
                                        error = auth.userInfo?.message ?: "Connexion Xtream refusée"
                                    } else {
                                        runCatching { client.optimizeServer(credentials) }
                                        credentialStore.save(credentials)
                                        recreate()
                                    }
                                } catch (t: Throwable) {
                                    error = t.message ?: "Impossible de joindre le serveur Xtream"
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading && server.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LauncherAccent,
                            contentColor = Color(0xFF00131D)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                Modifier.size(22.dp),
                                color = Color(0xFF00131D),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Se connecter")
                        }
                    }

                    Text(
                        "Identifiants enregistrés de façon chiffrée sur cet appareil.",
                        style = MaterialTheme.typography.bodySmall,
                        color = LauncherSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }

    private fun openMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
