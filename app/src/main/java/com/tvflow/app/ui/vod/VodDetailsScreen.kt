package com.tvflow.app.ui.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.tvflow.app.domain.VodItem

@Composable
fun VodDetailsScreen(
    title: String,
    posterUrl: String?,
    synopsis: String?,
    rating: String?,
    releaseYear: Int?,
    releaseMonth: Int?,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    onSuggestion: (VodItem) -> Unit = {},
    viewModel: VodDetailsViewModel = hiltViewModel()
) {
    val suggestions by viewModel.suggestions.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
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

            if (suggestions.isNotEmpty()) {
                Text(
                    "Suggestions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(suggestions, key = { it.streamId }) { item ->
                        VodSuggestionCard(item = item, onClick = { onSuggestion(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun VodSuggestionCard(
    item: VodItem,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.06f else 1f
                scaleY = if (focused) 1.06f else 1f
            },
        border = BorderStroke(
            if (focused) 3.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(190.dp)
            )
            Text(
                item.name,
                modifier = Modifier.padding(8.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
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
