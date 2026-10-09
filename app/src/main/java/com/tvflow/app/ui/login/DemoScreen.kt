package com.tvflow.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private data class DemoFeature(val title: String, val description: String)

private val demoFeatures = listOf(
    DemoFeature("Direct", "Parcourez les chaînes autorisées par catégorie et lancez la lecture."),
    DemoFeature("Guide TV", "Consultez l’EPG, le programme en cours et les émissions à venir."),
    DemoFeature("Films", "Explorez une vidéothèque avec affiches, synopsis, notes et reprise de lecture."),
    DemoFeature("Séries", "Naviguez par série, saison et épisode, avec suivi de progression."),
    DemoFeature("Recherche et favoris", "Retrouvez rapidement un contenu et conservez vos sélections sur l’appareil."),
    DemoFeature("Réglages", "Gérez les profils Xtream, le lecteur, les sous-titres et le contrôle parental.")
)

@Composable
fun DemoScreen(onBack: () -> Unit, onConfigureAccount: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Démonstration TVFlow", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Cette visite fonctionne entièrement hors ligne. TVFlow ne fournit aucun flux ni abonnement IPTV : l’utilisateur ajoute son propre accès Xtream autorisé.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(4.dp))
            demoFeatures.forEach { feature ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(feature.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(feature.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                "La lecture est désactivée dans cette démonstration, car aucun contenu tiers ni identifiant réel n’est intégré à l’application.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Retour") }
                Button(onClick = onConfigureAccount, modifier = Modifier.weight(1f)) { Text("Configurer") }
            }
        }
    }
}
