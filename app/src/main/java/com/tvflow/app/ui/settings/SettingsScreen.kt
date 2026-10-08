package com.tvflow.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tvflow.app.BuildConfig
import com.tvflow.app.domain.LiveCategory
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.domain.MediaCategory
import com.tvflow.app.domain.MediaSection
import com.tvflow.app.domain.ServerProfile
import com.tvflow.app.launcher.TvLauncherMode
import com.tvflow.app.util.isTelevisionUi

@Composable
fun SettingsScreen(
    onAddProfile: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val refreshProgress by viewModel.refreshProgress.collectAsState()
    val context = LocalContext.current
    val isTv = context.isTelevisionUi()
    var pin by remember { mutableStateOf("") }
    var tmdbToken by remember { mutableStateOf("") }
    var showChannelOrganization by remember { mutableStateOf(false) }
    var showMediaOrganization by remember { mutableStateOf(false) }
    var selectedMediaIds by remember { mutableStateOf(setOf<String>()) }

    refreshProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Actualisation TVFlow") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(progress.step, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(
                        progress = { progress.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${progress.percent} %",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Les catalogues sont mis à jour. Tu peux laisser cette fenêtre travailler.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
        if (!showChannelOrganization && !showMediaOrganization) {
        LazyColumn(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Réglages TVFlow", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SettingsButton(onClick = onBack) { Text("← Retour") }
            }

            item {
                SettingsButton(
                    onClick = { showChannelOrganization = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Organisation des chaînes  ›")
                }
            }

            item {
                SectionTitle("Serveurs Xtream")
                state.profiles.forEach { profile ->
                    ProfileRow(
                        profile = profile,
                        active = profile.id == state.activeProfileId,
                        onActivate = { viewModel.switchProfile(profile.id) },
                        onDelete = { if (state.profiles.size > 1) viewModel.deleteProfile(profile.id) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsButton(onClick = onAddProfile) { Text("+ Ajouter") }
                    SettingsButton(onClick = viewModel::refresh, enabled = !state.busy) { Text("Actualiser") }
                }
            }

            item {
                SectionTitle("Diagnostic réseau")
                Text("Teste séparément l’API Xtream, le Direct et la VOD sans exposer les identifiants.")
                Spacer(Modifier.height(8.dp))
                SettingsButton(
                    onClick = viewModel::runConnectionDiagnostics,
                    enabled = !state.busy
                ) {
                    Text(if (state.busy) "Test en cours…" else "Tester la connexion")
                }
                state.diagnostics?.let { diagnostics ->
                    Spacer(Modifier.height(10.dp))
                    DiagnosticLine(diagnostics.api.label, diagnostics.api.ok, diagnostics.api.statusCode, diagnostics.api.message)
                    DiagnosticLine(diagnostics.live.label, diagnostics.live.ok, diagnostics.live.statusCode, diagnostics.live.message)
                    DiagnosticLine(diagnostics.vod.label, diagnostics.vod.ok, diagnostics.vod.statusCode, diagnostics.vod.message)
                    if (diagnostics.ipLikelyBlocked) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Le serveur renvoie 403 depuis cette connexion. Si le même compte fonctionne via VPN, l’IP ou le réseau actuel est probablement filtré côté fournisseur.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                SectionTitle("Lecteur")
                ChoiceRow("Buffer", listOf("FAST", "BALANCED", "SAFE"), state.player.bufferPreset, viewModel::setBuffer)
                ChoiceRow("Image", listOf("FIT", "FILL", "ZOOM"), state.player.videoScale, viewModel::setScale)
                ChoiceRow(
                    "Thème",
                    listOf("BLUE", "DARK", "OLED", "PURPLE", "GREEN", "HIGH_CONTRAST"),
                    state.player.accent,
                    viewModel::setAccent
                )
                ToggleRow("Sous-titres par défaut", state.player.subtitlesEnabled) { viewModel.setSubtitles(it) }
                ChoiceRow(
                    "Aperçus animés accueil mobile",
                    listOf("OFF", "WIFI", "ALWAYS"),
                    state.player.mobileHomePreviewMode,
                    viewModel::setMobileHomePreviewMode
                )
                Text(
                    when (state.player.mobileHomePreviewMode) {
                        "OFF" -> "Téléphone : fond statique uniquement."
                        "ALWAYS" -> "Téléphone : aperçu vidéo autorisé sur tous les réseaux."
                        else -> "Téléphone : aperçu vidéo uniquement sur Wi‑Fi/réseau non facturé."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToggleRow("Épisode suivant automatique", state.player.autoNextEpisode) { viewModel.setAutoNextEpisode(it) }
                if (state.player.autoNextEpisode) {
                    Text("Compte à rebours épisode suivant", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 20, 30).forEach { seconds ->
                            SettingsButton(
                                onClick = { viewModel.setNextEpisodeCountdownSeconds(seconds) },
                                selected = state.player.nextEpisodeCountdownSeconds == seconds
                            ) {
                                Text(if (state.player.nextEpisodeCountdownSeconds == seconds) "● " + seconds + "s" else seconds.toString() + "s")
                            }
                        }
                    }
                }
                ToggleRow("Masquer les flux confirmés hors ligne", state.player.hideOfflineStreams) { viewModel.setHideOfflineStreams(it) }
                Text("La santé des flux est apprise uniquement pendant une vraie lecture. Aucun scan massif n’est lancé.", style = MaterialTheme.typography.bodySmall)
                Text("Actualisation automatique", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Manuel", 1 to "1 jour", 2 to "2 jours", 3 to "3 jours", 7 to "7 jours").forEach { (days, label) ->
                        SettingsButton(onClick = { viewModel.setRefreshIntervalDays(days) }) {
                            Text(if (state.player.refreshIntervalDays == days) "● $label" else label)
                        }
                    }
                }
            }

            if (isTv) {
                item {
                    SectionTitle("Démarrage de la TV")
                    ToggleRow(
                        "Démarrer TVFlow avec la box Android TV",
                        state.player.autoStartOnTvBoot
                    ) { viewModel.setAutoStartOnTvBoot(it) }
                    ToggleRow(
                        "Reprendre automatiquement la dernière chaîne",
                        state.player.resumeLastLiveOnTvBoot
                    ) { viewModel.setResumeLastLiveOnTvBoot(it) }
                    Text(
                        "La reprise de chaîne active automatiquement le démarrage de TVFlow. La dernière chaîne n’est mémorisée qu’après une lecture réellement démarrée.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Écran d’accueil de la box", fontWeight = FontWeight.SemiBold)
                    ToggleRow(
                        "Proposer TVFlow comme écran d’accueil",
                        state.player.tvLauncherEnabled
                    ) { viewModel.setTvLauncherEnabled(it) }
                    if (state.player.tvLauncherEnabled) {
                        val launcherStatus = TvLauncherMode.getStatus(context)
                        Text(
                            when {
                                launcherStatus.isTvFlowCurrentHome ->
                                    "État : TVFlow est l’écran d’accueil actuel."
                                launcherStatus.tvFlowAvailable ->
                                    "État : TVFlow est disponible comme écran d’accueil."
                                else ->
                                    "État : le système ne propose pas TVFlow comme launcher."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        SettingsButton(onClick = viewModel::openHomeChooser) {
                            Text(
                                if (launcherStatus.isTvFlowCurrentHome) {
                                    "Changer d’écran d’accueil"
                                } else {
                                    "Choisir l’écran d’accueil"
                                }
                            )
                        }
                    }
                    SettingsButton(onClick = viewModel::openAndroidSettings) {
                        Text("Ouvrir les réglages Android")
                    }
                    Text(
                        "Compatible Android TV, Google TV et box Android qui autorisent un launcher alternatif. Le téléphone garde son interface normale et certains constructeurs peuvent bloquer ce choix.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                SectionTitle("Métadonnées films & séries")
                Text(
                    if (state.tmdbConfigured) {
                        "TMDb est configuré. TVFlow complète les synopsis, affiches et dates de sortie manquantes."
                    } else {
                        "Optionnel : ajoute un token ou une API Key TMDb pour compléter les métadonnées absentes chez le fournisseur IPTV."
                    }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tmdbToken,
                    onValueChange = { tmdbToken = it },
                    label = { Text(if (state.tmdbConfigured) "Nouveau token / API Key TMDb" else "Token / API Key TMDb") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsButton(
                        onClick = { viewModel.setTmdbToken(tmdbToken); tmdbToken = "" },
                        enabled = tmdbToken.isNotBlank()
                    ) { Text("Enregistrer") }
                    if (state.tmdbConfigured) {
                        SettingsButton(onClick = { viewModel.setTmdbToken(""); tmdbToken = "" }) {
                            Text("Désactiver")
                        }
                    }
                }
                Text(
                    "Le token reste stocké localement sur l’appareil et n’est pas intégré dans l’APK ni envoyé sur GitHub.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            item {
                SettingsButton(
                    onClick = { selectedMediaIds = emptySet(); showMediaOrganization = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Organisation des films et séries  ›") }
            }

            item {
                SectionTitle("Contrôle parental")
                Text(if (state.pinConfigured) "PIN configuré" else "Aucun PIN configuré")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN 4 à 8 chiffres") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsButton(onClick = { viewModel.setPin(pin); pin = "" }, enabled = pin.length >= 4) { Text("Enregistrer") }
                    if (state.pinConfigured) SettingsButton(onClick = viewModel::clearPin) { Text("Supprimer") }
                }
            }

            item {
                SectionTitle("À propos")
                Text("TVFlow v${BuildConfig.VERSION_NAME} • build ${BuildConfig.VERSION_CODE}")
                Text("${BuildConfig.BUILD_CHANNEL} • ${BuildConfig.GIT_SHA}", style = MaterialTheme.typography.bodySmall)
                Text(BuildConfig.AUTHOR_SIGNATURE, fontWeight = FontWeight.Bold)
                Text(
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        }
        }

        if (showMediaOrganization) {
            val allMedia = state.vodCategories + state.seriesCategories
            fun mediaKey(category: MediaCategory) = "${category.type}:${category.id}"
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Organisation des films et séries")
                    SettingsButton(onClick = { showMediaOrganization = false }) {
                        Text("← Réglages")
                    }
                }
                Text("Sélectionne les catégories à classer en France, Enfants ou Autres.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsButton(
                        onClick = {
                            selectedMediaIds = if (selectedMediaIds.size == allMedia.size) {
                                emptySet()
                            } else allMedia.map(::mediaKey).toSet()
                        }
                    ) { Text(if (selectedMediaIds.size == allMedia.size && allMedia.isNotEmpty()) "Désélectionner" else "Tout sélectionner") }
                    SettingsButton(
                        onClick = {
                            viewModel.classifyMediaCategories(
                                allMedia.filter { mediaKey(it) in selectedMediaIds }, MediaSection.FRANCE
                            )
                        },
                        enabled = selectedMediaIds.isNotEmpty() && !state.busy
                    ) { Text("France") }
                    SettingsButton(
                        onClick = {
                            viewModel.classifyMediaCategories(
                                allMedia.filter { mediaKey(it) in selectedMediaIds }, MediaSection.KIDS
                            )
                        },
                        enabled = selectedMediaIds.isNotEmpty() && !state.busy
                    ) { Text("Enfants") }
                    SettingsButton(
                        onClick = {
                            viewModel.classifyMediaCategories(
                                allMedia.filter { mediaKey(it) in selectedMediaIds }, MediaSection.OTHER
                            )
                        },
                        enabled = selectedMediaIds.isNotEmpty() && !state.busy
                    ) { Text("Autres") }
                    SettingsButton(
                        onClick = {
                            viewModel.classifyMediaCategories(
                                allMedia.filter { mediaKey(it) in selectedMediaIds }, null
                            )
                        },
                        enabled = selectedMediaIds.isNotEmpty() && !state.busy
                    ) { Text("Auto") }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SectionTitle("Films") }
                    items(state.vodCategories, key = { "vod:" + it.id }) { category ->
                        val key = mediaKey(category)
                        SettingsButton(
                            modifier = Modifier.fillMaxWidth(),
                            selected = key in selectedMediaIds,
                            onClick = {
                                selectedMediaIds = if (key in selectedMediaIds) selectedMediaIds - key
                                else selectedMediaIds + key
                            }
                        ) { Text("${if (key in selectedMediaIds) "☑" else "☐"}  ${category.name}") }
                    }
                    item { SectionTitle("Séries") }
                    items(state.seriesCategories, key = { "series:" + it.id }) { category ->
                        val key = mediaKey(category)
                        SettingsButton(
                            modifier = Modifier.fillMaxWidth(),
                            selected = key in selectedMediaIds,
                            onClick = {
                                selectedMediaIds = if (key in selectedMediaIds) selectedMediaIds - key
                                else selectedMediaIds + key
                            }
                        ) { Text("${if (key in selectedMediaIds) "☑" else "☐"}  ${category.name}") }
                    }
                }
            }
        }

        if (showChannelOrganization) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Organisation des chaînes")
                SettingsButton(onClick = { showChannelOrganization = false }) { Text("← Réglages") }
            }
            Text("Sélectionne une catégorie à gauche, puis utilise les actions à droite.")
            Text(
                "Le classement FR / Enfants / Autres est mémorisé pour chaque serveur.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allSelected = state.categories.isNotEmpty() &&
                    state.selectedCategoryIds.size == state.categories.size
                SettingsButton(
                    onClick = {
                        if (allSelected) viewModel.clearCategorySelection()
                        else viewModel.selectAllCategories()
                    },
                    modifier = Modifier.weight(1f),
                    selected = allSelected
                ) {
                    Text(if (allSelected) "✓ Désélectionner" else "☐ Tout sélectionner", maxLines = 1)
                }
                SettingsButton(
                    onClick = { viewModel.setSelectedCategoriesHidden(true) },
                    modifier = Modifier.weight(1f),
                    enabled = state.selectedCategoryIds.isNotEmpty()
                ) { Text("Masquer sélection", maxLines = 1) }
                SettingsButton(
                    onClick = { viewModel.setSelectedCategoriesHidden(false) },
                    modifier = Modifier.weight(1f),
                    enabled = state.selectedCategoryIds.isNotEmpty()
                ) { Text("Afficher sélection", maxLines = 1) }
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LazyColumn(
                    Modifier.weight(0.44f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.categories, key = { it.id }) { category ->
                        CategoryManageRow(
                            category = category,
                            selected = state.selectedCategoryId == category.id,
                            selectedForBulk = category.id in state.selectedCategoryIds,
                            onSelect = { viewModel.selectCategory(category.id) },
                            onToggleBulk = { viewModel.toggleCategorySelection(category.id) }
                        )
                    }
                }

                Column(Modifier.weight(0.56f).fillMaxHeight()) {
                    val selectedCategory = state.categories.firstOrNull { it.id == state.selectedCategoryId }
                    if (selectedCategory != null) {
                        Text(
                            selectedCategory.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingsButton(
                                onClick = { viewModel.classifyCategory(selectedCategory, MediaSection.FRANCE) },
                                modifier = Modifier.weight(1f)
                            ) { Text("FR", maxLines = 1) }
                            SettingsButton(
                                onClick = { viewModel.classifyCategory(selectedCategory, MediaSection.KIDS) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Enfants", maxLines = 1) }
                            SettingsButton(
                                onClick = { viewModel.classifyCategory(selectedCategory, MediaSection.OTHER) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Autres", maxLines = 1) }
                            SettingsButton(
                                onClick = { viewModel.resetCategoryClassification(selectedCategory) },
                                modifier = Modifier.weight(1f)
                            ) { Text("Auto", maxLines = 1) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SettingsButton(
                                onClick = { viewModel.toggleCategoryHidden(selectedCategory) },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (selectedCategory.isHidden) "Afficher" else "Masquer", maxLines = 1) }
                            SettingsButton(
                                onClick = { viewModel.toggleCategoryLocked(selectedCategory) },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (selectedCategory.isLocked) "Déverrouiller" else "Verrouiller", maxLines = 1) }
                            SettingsButton(
                                onClick = { viewModel.moveCategory(selectedCategory, -1) },
                                modifier = Modifier.weight(0.55f)
                            ) { Text("↑") }
                            SettingsButton(
                                onClick = { viewModel.moveCategory(selectedCategory, +1) },
                                modifier = Modifier.weight(0.55f)
                            ) { Text("↓") }
                        }
                        Spacer(Modifier.height(10.dp))
                    } else {
                        Text("Choisis une catégorie pour afficher ses actions.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp))
                    }

                    val allChannelsSelected = state.channels.isNotEmpty() &&
                        state.selectedChannelIds.size == state.channels.size
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SettingsButton(
                            onClick = {
                                if (allChannelsSelected) viewModel.clearChannelSelection()
                                else viewModel.selectAllChannels()
                            },
                            modifier = Modifier.weight(1.25f),
                            selected = allChannelsSelected,
                            enabled = state.channels.isNotEmpty()
                        ) {
                            Text(if (allChannelsSelected) "✓ Désélectionner" else "☐ Toutes les chaînes", maxLines = 1)
                        }
                        SettingsButton(
                            onClick = { viewModel.setSelectedChannelsHidden(true) },
                            modifier = Modifier.weight(1f),
                            enabled = state.selectedChannelIds.isNotEmpty()
                        ) { Text("Masquer", maxLines = 1) }
                        SettingsButton(
                            onClick = { viewModel.setSelectedChannelsHidden(false) },
                            modifier = Modifier.weight(1f),
                            enabled = state.selectedChannelIds.isNotEmpty()
                        ) { Text("Afficher", maxLines = 1) }
                    }

                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(state.channels, key = { it.streamId }) { channel ->
                            ChannelManageRow(
                                channel = channel,
                                selectedForBulk = channel.streamId in state.selectedChannelIds,
                                onToggleBulk = { viewModel.toggleChannelSelection(channel.streamId) },
                                onHide = { viewModel.toggleChannelHidden(channel) },
                                onUp = { viewModel.moveChannel(channel, -1) },
                                onDown = { viewModel.moveChannel(channel, +1) }
                            )
                        }
                    }
                }
            }
        }
        }
    }
    }
}

@Composable
private fun DiagnosticLine(
    label: String,
    ok: Boolean,
    statusCode: Int?,
    message: String
) {
    val skipped = message.startsWith("Non testé")
    val status = when {
        ok -> "OK"
        skipped -> "NON TESTÉ"
        statusCode != null -> "HTTP $statusCode"
        else -> "ÉCHEC"
    }
    Text(
        text = "$label : $status • $message",
        color = when {
            ok -> MaterialTheme.colorScheme.primary
            skipped -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.error
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun ProfileRow(profile: ServerProfile, active: Boolean, onActivate: () -> Unit, onDelete: () -> Unit) {
    FocusRow(onClick = onActivate, selected = active) {
        Column(Modifier.weight(1f)) {
            Text((if (active) "● " else "○ ") + profile.name, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            Text(profile.credentials.serverUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Suppr.", modifier = Modifier.clickable(onClick = onDelete).padding(8.dp))
    }
}

@Composable
private fun ChoiceRow(label: String, choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { value ->
                val display = when (value) {
                    "BLUE" -> "Bleu"
                    "DARK" -> "Sombre"
                    "OLED" -> "OLED"
                    "PURPLE" -> "Violet"
                    "GREEN" -> "Vert"
                    "HIGH_CONTRAST" -> "Contraste"
                    else -> value
                }
                SettingsButton(
                    onClick = { onSelect(value) },
                    selected = selected == value
                ) {
                    Text(if (selected == value) "● $display" else display)
                }
            }
        }
    }
}

@Composable
private fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme

    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.scale(if (focused) 1.06f else 1f),
        enabled = enabled,
        interactionSource = interaction,
        border = BorderStroke(
            width = if (focused) 3.dp else 1.dp,
            color = when {
                focused -> colors.primary
                selected -> colors.secondary
                else -> colors.outlineVariant
            }
        ),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = when {
                focused -> colors.primaryContainer
                selected -> colors.secondaryContainer
                else -> colors.surfaceVariant
            },
            contentColor = when {
                focused -> colors.onPrimaryContainer
                selected -> colors.onSecondaryContainer
                else -> colors.onSurfaceVariant
            }
        ),
        content = content
    )
}

@Composable
private fun ToggleRow(label: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    SettingsButton(
        onClick = { onChange(!enabled) },
        selected = enabled
    ) {
        Text("$label : ${if (enabled) "Oui" else "Non"}")
    }
}

@Composable
private fun MediaCategoryClassifyRow(
    prefix: String,
    category: MediaCategory,
    onFrance: () -> Unit,
    onKids: () -> Unit,
    onOther: () -> Unit,
    onAuto: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$prefix • ${category.name}",
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall
        )
        SmallAction(if (category.section == MediaSection.FRANCE) "●FR" else "FR", onFrance)
        SmallAction(if (category.section == MediaSection.KIDS) "●E" else "E", onKids)
        SmallAction(if (category.section == MediaSection.OTHER) "●A" else "A", onOther)
        SmallAction("↺", onAuto)
    }
}

@Composable
private fun CategoryManageRow(
    category: LiveCategory,
    selected: Boolean,
    selectedForBulk: Boolean,
    onSelect: () -> Unit,
    onToggleBulk: () -> Unit
) {
    FocusRow(onClick = onSelect, selected = selected) {
        SmallAction(if (selectedForBulk) "☑" else "☐", onToggleBulk)
        Column(Modifier.weight(1f)) {
            Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (category.isHidden || category.isLocked) {
                Text(
                    listOfNotNull(
                        "Masquée".takeIf { category.isHidden },
                        "Verrouillée".takeIf { category.isLocked }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun ChannelManageRow(
    channel: LiveChannel,
    selectedForBulk: Boolean,
    onToggleBulk: () -> Unit,
    onHide: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit
) {
    FocusRow(onClick = onToggleBulk, selected = selectedForBulk) {
        SmallAction(if (selectedForBulk) "☑" else "☐", onToggleBulk)
        Text(channel.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        SmallAction(if (channel.isHidden) "Voir" else "Masq.", onHide)
        SmallAction("↑", onUp)
        SmallAction("↓", onDown)
    }
}

@Composable
private fun SmallAction(text: String, onClick: () -> Unit) {
    Text(text, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 7.dp, vertical = 6.dp))
}

@Composable
private fun FocusRow(onClick: () -> Unit, selected: Boolean, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .scale(if (focused) 1.02f else 1f)
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.primaryContainer
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                RoundedCornerShape(10.dp)
            )
            .then(
                if (focused) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        RoundedCornerShape(10.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(if (focused) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
