package com.tvflow.app.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun VodDetailsScreen(
    title: String,
    posterUrl: String?,
    synopsis: String?,
    rating: String?,
    releaseYear: Int?,
    releaseMonth: Int?,
    onPlay: () -> Unit,
    onBack: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(280.dp).height(420.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )

                val dateLabel = when {
                    releaseYear != null && releaseMonth != null ->
                        monthName(releaseMonth) + " " + releaseYear
                    releaseYear != null -> releaseYear.toString()
                    else -> null
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    dateLabel?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    rating?.takeIf { it.isNotBlank() }?.let {
                        Text("★ " + it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    synopsis?.takeIf { it.isNotBlank() } ?: "Synopsis indisponible.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 12,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onPlay) { Text("▶ Lire") }
                    Button(onClick = onBack) { Text("Retour") }
                }
            }
        }
    }
}

private fun monthName(month: Int): String = when (month) {
    1 -> "Janvier"
    2 -> "Février"
    3 -> "Mars"
    4 -> "Avril"
    5 -> "Mai"
    6 -> "Juin"
    7 -> "Juillet"
    8 -> "Août"
    9 -> "Septembre"
    10 -> "Octobre"
    11 -> "Novembre"
    12 -> "Décembre"
    else -> month.toString()
}
