package com.tvflow.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tvflow.app.BuildConfig
import com.tvflow.app.premium.PremiumPlan
import com.tvflow.app.premium.PlayBillingGateway
import androidx.compose.runtime.DisposableEffect
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
    val tmdbDiagnostics by viewModel.tmdbDiagnostics.collectAsState()
    val tmdbSourceStats by viewModel.tmdbSourceStats.collectAsState()
    val tmdbGenreCoverage by viewModel.tmdbGenreCoverage.collectAsState(initial = com.tvflow.app.data.repository.XtreamRepository.TmdbGenreCoverage())
    val refreshProgress by viewModel.refreshProgress.collectAsState()
    val context = LocalContext.current
    val isTv = context.isTelevisionUi()
    var pin by remember { mutableStateOf("") }
    var showPremium by remember { mutableStateOf(false) }
    val billing = remember(context) { PlayBillingGateway(context) }
    val billingState by billing.state.collectAsState()
    DisposableEffect(billing) {
        if (BuildConfig.IS_PLAY_STORE) billing.connect()
        onDispose { billing.close() }
    }
    if (showPremium) {
        AlertDialog(
            onDismissRequest = { showPremium = false },
            title = { Text("TVFlow Premium") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${PremiumPlan.monthlyPriceLabel} ou ${PremiumPlan.introductoryPriceLabel} à vie", style = MaterialTheme.typography.titleMedium)
                    Text("Tarifs indicatifs : les prix définitifs seront affichés par Google Play.")
                    Text("Votre formule actuelle : TVFlow Free")
                    if (BuildConfig.IS_PLAY_STORE) {
                        Text("Google Play : ${billingState.message}")
                        billingState.displayedPrice?.let { Text("Prix affiché par Google Play : $it") }
                        Button(onClick = { billing.restore() }) { Text("Restaurer mes achats") }
                    } else {
                        Text("Les achats Premium sont réservés à la future version Google Play.")
                    }
                    PremiumPlan.benefits.forEach { Text("• $it") }
                    Text(
                        "L’achat et la restauration seront disponibles après la configuration et la vérification sécurisée de Google Play Billing. Aucun paiement n’est effectué ici.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { Button(onClick = { showPremium = false }) { Text("Compris") } }
        )
    }
    var settingsCategory by remember { mutableStateOf<String?>(null) }
    val settingsListState = rememberLazyListState()
    val settingsScrollScope = rememberCoroutineScope()
    var tmdbToken by remember { mutableStateOf("") }
    var showTmdbGuide by remember { mutableStateOf(false) }
    var tmdbGuideStep by remember { mutableStateOf(0) }
    var showChannelOrganization by remember { mutableStateOf(false) }
    var showChannelAdvanced by remember { mutableStateOf(false) }
    var showMediaOrganization by remember { mutableStateOf(false) }
    var showMediaBulk by remember { mutableStateOf(false) }
    var channelVisibilityFilter by remember { mutableStateOf(CategoryVisibilityFilter.VISIBLE) }
    var mediaVisibilityFilter by remember { mutableStateOf(CategoryVisibilityFilter.VISIBLE) }
    var selectedMediaIds by remember { mutableStateOf(setOf<String>()) }
    val hiddenMediaKeys by viewModel.hiddenMediaKeys.collectAsState()

    if (showTmdbGuide) {
        val steps = listOf(
            "1/4 • Créer un compte TMDB" to "Sur le site officiel The Movie Database, crée un compte personnel ou connecte-toi. TVFlow ne demande jamais ton mot de passe TMDB.",
            "2/4 • Demander un accès API" to "Dans les paramètres de ton compte TMDB, ouvre la rubrique API puis suis leurs instructions. L'accès dépend des conditions d'utilisation de TMDB.",
            "3/4 • Copier la clé" to "Copie la clé API v3 ou le jeton d'accès en lecture. Sur Android TV, tu peux effectuer les étapes sur ton téléphone et revenir ici pour saisir la clé avec la télécommande.",
            "4/4 • Enregistrer dans TVFlow" to "Ferme ce guide, colle la clé dans le champ TMDB des paramètres et appuie sur Enregistrer. TVFlow vérifie la clé. La configuration est facultative et les données du catalogue continuent à provenir de Xtream."
        )
        val guide = steps[tmdbGuideStep]
        AlertDialog(
            onDismissRequest = { showTmdbGuide = false },
            title = { Text(guide.first) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(guide.second)
                    if (isTv && tmdbGuideStep <= 1) {
                        val address = if (tmdbGuideStep == 0) "https://www.themoviedb.org/signup" else "https://www.themoviedb.org/settings/api"
                        val qr = remember(address) {
                            val matrix = MultiFormatWriter().encode(address, BarcodeFormat.QR_CODE, 248, 248)
                            val pixels = IntArray(248 * 248) { index ->
                                if (matrix[index % 248, index / 248]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                            }
                            Bitmap.createBitmap(248, 248, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, 248, 0, 0, 248, 248) }.asImageBitmap()
                        }
                        Image(bitmap = qr, contentDescription = "QR code vers $address", modifier = Modifier.width(180.dp).height(180.dp))
                        Text("Scanne ce QR code avec ton téléphone.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (tmdbGuideStep <= 1) {
                        Button(onClick = {
                            val address = if (tmdbGuideStep == 0) "https://www.themoviedb.org/signup" else "https://www.themoviedb.org/settings/api"
                            val browser = Intent(Intent.ACTION_VIEW, Uri.parse(address))
                            if (browser.resolveActivity(context.packageManager) != null) {
                                context.startActivity(browser)
                            } else {
                                android.widget.Toast.makeText(context, "Ouvre $address sur ton téléphone", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }) { Text(if (tmdbGuideStep == 0) "Ouvrir l'inscription TMDB" else "Ouvrir la page API TMDB") }
                    }
                    Text("TMDB enrichit les fiches films et séries, pas les chaînes ni les abonnements IPTV.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (tmdbGuideStep == 3) showTmdbGuide = false else tmdbGuideStep++
                }) { Text(if (tmdbGuideStep == 3) "Terminer et saisir la clé" else "Suivant") }
            },
            dismissButton = {
                Button(onClick = {
                    if (tmdbGuideStep > 0) tmdbGuideStep-- else showTmdbGuide = false
                }) { Text(if (tmdbGuideStep > 0) "Précédent" else "Plus tard") }
            }
        )
    }

    refreshProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text(if (progress.step.startsWith("Enrichissement TMDB")) "Enrichissement TMDB" else "Actualisation TVFlow") },
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
            state = settingsListState,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(if (settingsCategory == null) "Réglages TVFlow" else settingsCategory!!,
                    style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                SettingsButton(onClick = {
                    if (settingsCategory == null) onBack() else settingsCategory = null
                }) { Text("← Retour") }
            }
            if (settingsCategory == null) item {
                listOf("Organisation", "Comptes & sauvegardes", "IA & métadonnées",
                    "Réseau", "Lecture & démarrage", "Contrôle parental", "À propos")
                    .forEach { category ->
                        SettingsButton(onClick = { settingsCategory = category }, modifier = Modifier.fillMaxWidth()) {
                            Text("$category  ›")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
            }

            if (settingsCategory == "Organisation") item {
                SettingsButton(
                    onClick = { showChannelOrganization = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Organisation des chaînes  ›")
                }
                Spacer(Modifier.height(8.dp))
                SettingsButton(
                    onClick = { selectedMediaIds = emptySet(); showMediaOrganization = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Organisation des films et séries  ›")
                }
            }

            if (settingsCategory == "Comptes & sauvegardes") item {
                SectionTitle("TVFlow Premium")
                SettingsButton(onClick = { showPremium = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Premium à vie · 4,99 €  ›")
                }
                Spacer(Modifier.height(8.dp))
                SectionTitle("Sources IPTV")
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

            if (settingsCategory == "Comptes & sauvegardes") item {
                BackupControls()
            }

            if (settingsCategory == "IA & métadonnées") item {
                AiGatewayControls()
            }

            if (settingsCategory == "Réseau") item {
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

            if (settingsCategory == "Lecture & démarrage") item {
                SectionTitle("Lecteur")
                ChoiceRow("Buffer", listOf("AUTO", "FAST", "BALANCED", "SAFE"), state.player.bufferPreset, viewModel::setBuffer)
                ChoiceRow("Image", listOf("FIT", "FILL", "ZOOM"), state.player.videoScale, viewModel::setScale)
                Text("Thème : OLED", style = MaterialTheme.typography.bodyMedium)
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

            if (isTv && settingsCategory == "Lecture & démarrage") {
                item {
                    SectionTitle("Démarrage de la TV")
                    Text("Au lancement de TVFlow", fontWeight = FontWeight.SemiBold)
                    listOf("HOME" to "Accueil normal", "LAST" to "Dernière chaîne regardée", "TNT" to "Première chaîne TNT").forEach { (mode, label) ->
                        SettingsButton(
                            modifier = Modifier.fillMaxWidth(),
                            selected = state.player.appLaunchMode == mode,
                            onClick = { viewModel.setAppLaunchMode(mode) }
                        ) { Text(if (state.player.appLaunchMode == mode) "● $label" else "○ $label") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Au démarrage de la Mi Box", fontWeight = FontWeight.SemiBold)
                    val bootMode = when {
                        !state.player.autoStartOnTvBoot -> "OFF"
                        state.player.resumeLastLiveOnTvBoot -> "LAST"
                        else -> "HOME"
                    }
                    listOf("OFF" to "Ne pas lancer TVFlow", "HOME" to "Lancer sur l'accueil", "LAST" to "Lancer sur la dernière chaîne").forEach { (mode, label) ->
                        SettingsButton(
                            modifier = Modifier.fillMaxWidth(),
                            selected = bootMode == mode,
                            onClick = { viewModel.setTvBootMode(mode) }
                        ) { Text(if (bootMode == mode) "● $label" else "○ $label") }
                    }
                    Text("Les deux choix sont indépendants. Le démarrage automatique dépend aussi des autorisations Android TV.", style = MaterialTheme.typography.bodySmall)
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

            if (settingsCategory == "IA & métadonnées") item {
                SectionTitle("TMDB — sources et statistiques")
                SettingsButton(onClick = viewModel::testTmdbCloud, enabled = !state.busy) {
                    Text("Tester TMDB maintenant")
                }
                state.message?.takeIf { it.startsWith("Test TMDB") || it.startsWith("TMDB :") || it.startsWith("TMDB indisponible") }?.let {
                    Text(it, color = MaterialTheme.colorScheme.primary)
                }
                Text("Source de la dernière recherche : ${tmdbSourceStats.lastSource}")
                Text("Recherches correspondantes : Cloud ${tmdbSourceStats.cloud} • Clé locale ${tmdbSourceStats.local} • Cache serveur ${tmdbSourceStats.cloudCacheHits}")
                Text("Compteurs depuis le démarrage de l'application. Une affiche déjà stockée dans le catalogue n'entraîne aucune nouvelle requête.", style = MaterialTheme.typography.bodySmall)
                SettingsButton(onClick = { tmdbGuideStep = 0; showTmdbGuide = true }) {
                    Text("Guide : configurer TMDB étape par étape")
                }
                Text(
                    if (state.tmdbConfigured) {
                        "TMDb est configuré. TVFlow complète les synopsis, affiches et dates de sortie manquantes."
                    } else {
                        "Optionnel : ajoute un token ou une API Key TMDb pour compléter les métadonnées absentes chez le fournisseur IPTV."
                    }
                )
                Text(
                    if (state.tmdbConfigured) "Enrichissement TMDB disponible (Cloud ou clé locale). Les fiches manquantes seront complétées pendant les actualisations du catalogue."
                    else "TMDB Cloud s'active avec une invitation TVFlow Cloud. Sinon, une clé TMDB personnelle reste possible. Les films et séries IPTV restent accessibles.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Genres TMDB sur le catalogue actif : films ${tmdbGenreCoverage.vodWithGenres}/${tmdbGenreCoverage.vodTotal} (${if (tmdbGenreCoverage.vodTotal > 0) tmdbGenreCoverage.vodWithGenres * 100 / tmdbGenreCoverage.vodTotal else 0} %), séries ${tmdbGenreCoverage.seriesWithGenres}/${tmdbGenreCoverage.seriesTotal} (${if (tmdbGenreCoverage.seriesTotal > 0) tmdbGenreCoverage.seriesWithGenres * 100 / tmdbGenreCoverage.seriesTotal else 0} %).",
                    style = MaterialTheme.typography.bodySmall
                )
                SettingsButton(onClick = viewModel::enrichTmdbNow, enabled = state.tmdbConfigured && !state.busy) {
                    Text("Enrichir les fiches avec TMDB maintenant")
                }
                Text("Traitement limité à 12 fiches par catégorie et 120 catégories par passage, pour préserver la fluidité. La progression indique les catégories parcourues, pas la couverture totale du catalogue.", style = MaterialTheme.typography.bodySmall)
                val stats = state.tmdbStats
                Text(
                    "Recherches TMDB cumulées sur ce profil : ${stats.attempted} tentative(s), ${stats.enriched} réponse(s) avec métadonnées, ${stats.failed} sans données exploitables.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    if (stats.lastAttemptAt > 0) "Dernier essai : " + java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.FRANCE).format(java.util.Date(stats.lastAttemptAt))
                    else "Aucun enrichissement TMDB enregistré sur ce profil.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Diagnostic TMDB depuis le dernier démarrage : ${tmdbDiagnostics.matched} correspondance(s), ${tmdbDiagnostics.noMatch} titre(s) introuvable(s), ${tmdbDiagnostics.unauthorized} refus d'accès, ${tmdbDiagnostics.rateLimited} limite(s) API, ${tmdbDiagnostics.serverErrors} erreur(s) HTTP, ${tmdbDiagnostics.networkErrors} erreur(s) réseau, ${tmdbDiagnostics.invalidResponses} réponse(s) invalide(s).",
                    style = MaterialTheme.typography.bodySmall
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

            if (settingsCategory == "Contrôle parental") item {
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

            if (settingsCategory == "À propos") item {
                SectionTitle("À propos")
                Text("TVFlow v${BuildConfig.VERSION_NAME} • build ${BuildConfig.VERSION_CODE}")
                Text("${BuildConfig.BUILD_CHANNEL} • ${BuildConfig.GIT_SHA}", style = MaterialTheme.typography.bodySmall)
                Text(BuildConfig.AUTHOR_SIGNATURE, fontWeight = FontWeight.Bold)
                Text(
                    "This product uses the TMDB API but is not endorsed or certified by TMDB.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            state.message?.takeUnless { it.startsWith("Test TMDB") || it.startsWith("TMDB :") || it.startsWith("TMDB indisponible") }?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        }
        }

        if (showMediaOrganization) {
            val allMedia = state.vodCategories + state.seriesCategories
            val shownMedia = allMedia.filter { category ->
                mediaVisibilityFilter.accepts("${category.type}:${category.id}" in hiddenMediaKeys)
            }
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
                if (isTv) Text("Appuie sur une catégorie pour l'afficher ou la masquer. Les modifications sont enregistrées automatiquement.")
                CategoryVisibilityFilterButtons(mediaVisibilityFilter) { mediaVisibilityFilter = it }
                SettingsButton(onClick = { showMediaBulk = !showMediaBulk }) {
                    Text(if (showMediaBulk) "Masquer les actions de groupe" else "Sélection multiple", maxLines = 1)
                }
                if (showMediaBulk) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsButton(
                        onClick = {
                            selectedMediaIds = if (shownMedia.isNotEmpty() && shownMedia.all { mediaKey(it) in selectedMediaIds }) {
                                selectedMediaIds - shownMedia.map(::mediaKey).toSet()
                            } else selectedMediaIds + shownMedia.map(::mediaKey).toSet()
                        }
                    ) { Text(if (shownMedia.isNotEmpty() && shownMedia.all { mediaKey(it) in selectedMediaIds } && allMedia.isNotEmpty()) "Désélectionner" else "Tout sélectionner") }
                    SettingsButton(
                        onClick = { viewModel.setMediaCategoriesHidden(allMedia.filter { mediaKey(it) in selectedMediaIds }, true) },
                        enabled = selectedMediaIds.isNotEmpty() && !state.busy
                    ) { Text("Masquer") }
                    SettingsButton(
                        onClick = { viewModel.setMediaCategoriesHidden(allMedia.filter { mediaKey(it) in selectedMediaIds }, false) },
                        enabled = selectedMediaIds.isNotEmpty() && !state.busy
                    ) { Text("Afficher") }
                }
                }
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { SectionTitle("Films") }
                    items(state.vodCategories.filter { mediaVisibilityFilter.accepts(mediaKey(it) in hiddenMediaKeys) }, key = { "vod:" + it.id }) { category ->
                        val key = mediaKey(category)
                        SettingsButton(
                            modifier = Modifier.fillMaxWidth(),
                            selected = key !in hiddenMediaKeys,
                            onClick = { viewModel.setMediaCategoriesHidden(listOf(category), key !in hiddenMediaKeys) }
                        ) { Text("${if (key in hiddenMediaKeys) "○ Masqué" else "● Visible"}  ${category.name}") }
                    }
                    item { SectionTitle("Séries") }
                    items(state.seriesCategories.filter { mediaVisibilityFilter.accepts(mediaKey(it) in hiddenMediaKeys) }, key = { "series:" + it.id }) { category ->
                        val key = mediaKey(category)
                        SettingsButton(
                            modifier = Modifier.fillMaxWidth(),
                            selected = key !in hiddenMediaKeys,
                            onClick = { viewModel.setMediaCategoriesHidden(listOf(category), key !in hiddenMediaKeys) }
                        ) { Text("${if (key in hiddenMediaKeys) "○ Masqué" else "● Visible"}  ${category.name}") }
                    }
                }
            }
        }

        if (showChannelOrganization) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("Catégories de chaînes")
                    SettingsButton(onClick = { showChannelOrganization = false }) { Text("← Réglages") }
                }
                Text("Choisis les catégories à afficher ou masquer. Les chaînes à l'intérieur ne sont pas modifiées.")
                CategoryVisibilityFilterButtons(channelVisibilityFilter) { channelVisibilityFilter = it }
                val displayedCategories = state.categories.filter { channelVisibilityFilter.accepts(it.isHidden) }
                Spacer(Modifier.height(10.dp))
                val allSelected = displayedCategories.isNotEmpty() &&
                    displayedCategories.all { it.id in state.selectedCategoryIds }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsButton(
                        onClick = {
                            if (allSelected) displayedCategories.forEach { if (it.id in state.selectedCategoryIds) viewModel.toggleCategorySelection(it.id) }
                            else displayedCategories.forEach { if (it.id !in state.selectedCategoryIds) viewModel.toggleCategorySelection(it.id) }
                        },
                        selected = allSelected
                    ) { Text(if (allSelected) "Tout désélectionner" else "Tout sélectionner") }
                    SettingsButton(
                        onClick = { viewModel.setSelectedCategoriesHidden(true) },
                        enabled = state.selectedCategoryIds.isNotEmpty()
                    ) { Text("Masquer la sélection") }
                    SettingsButton(
                        onClick = { viewModel.setSelectedCategoriesHidden(false) },
                        enabled = state.selectedCategoryIds.isNotEmpty()
                    ) { Text("Afficher la sélection") }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(displayedCategories, key = { it.id }) { category ->
                        SettingsButton(
                            modifier = Modifier.fillMaxWidth(),
                            selected = category.id in state.selectedCategoryIds,
                            onClick = { viewModel.toggleCategorySelection(category.id) }
                        ) {
                            Text(
                                (if (category.id in state.selectedCategoryIds) "☑ " else "☐ ") +
                                category.name +
                                (if (category.isHidden) "  ·  Masquée" else "  ·  Visible"),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

private enum class CategoryVisibilityFilter(val label: String) {
    VISIBLE("Visibles"), HIDDEN("Masquées"), ALL("Toutes");
    fun accepts(hidden: Boolean) = when (this) {
        VISIBLE -> !hidden
        HIDDEN -> hidden
        ALL -> true
    }
}

@Composable
private fun CategoryVisibilityFilterButtons(
    current: CategoryVisibilityFilter,
    onChange: (CategoryVisibilityFilter) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(CategoryVisibilityFilter.ALL, CategoryVisibilityFilter.VISIBLE, CategoryVisibilityFilter.HIDDEN).forEach { option ->
            SettingsButton(onClick = { onChange(option) }, selected = current == option) {
                Text(option.label)
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
            val sourceLabel = when {
                profile.credentials.username == "__tvflow_m3u__" && profile.credentials.serverUrl.startsWith("m3u-local:") -> "Fichier M3U local"
                profile.credentials.username == "__tvflow_m3u__" -> "M3U URL · " + android.net.Uri.parse(profile.credentials.serverUrl).host.orEmpty()
                else -> profile.credentials.serverUrl
            }
            Text(sourceLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("Suppr.", modifier = Modifier.clickable(onClick = onDelete).padding(8.dp))
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ChoiceRow(label: String, choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { value ->
                val display = when (value) {
                    "BLUE" -> "Bleu"
                    "DARK" -> "Sombre"
                    "OLED" -> "OLED"
                    "PURPLE" -> "Violet"
                    "RAINBOW" -> "Arc-en-ciel animé"
                    "NEBULA" -> "Nebula Flow"
                    "INFERNO" -> "Inferno Pulse"
                    "CRYSTAL" -> "Crystal Vision"
                    "QUANTUM" -> "Quantum Flow"
                    "OCEAN" -> "Ocean Depth"
                    "AURORA" -> "Aurora Cinema"
                    "ECLIPSE" -> "Eclipse Black"
                    "INTERSTELLAR" -> "Interstellar"
                    "HOLOGRAPHIC" -> "Holographic 2077"
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
internal fun SettingsButton(
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
            width = if (focused) 4.dp else 1.dp,
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
