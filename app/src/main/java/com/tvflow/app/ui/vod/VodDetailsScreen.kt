package com.tvflow.app.ui.vod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.tvflow.app.domain.VodItem
import com.tvflow.app.util.isTelevisionUi

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
    val enrichedItem by viewModel.item.collectAsState()
    val isTv = LocalContext.current.isTelevisionUi()
    val posterWidth = if (isTv) 230.dp else 104.dp
    val posterHeight = if (isTv) 340.dp else 158.dp
    val effectiveTitle = enrichedItem?.name?.takeIf { it.isNotBlank() } ?: title
    val effectivePoster = enrichedItem?.posterUrl ?: posterUrl
    val effectiveSynopsis = enrichedItem?.synopsis?.takeIf { it.isNotBlank() } ?: synopsis
    val effectiveRating = enrichedItem?.rating?.takeIf { it.isNotBlank() } ?: rating
    val effectiveReleaseYear = enrichedItem?.releaseYear ?: releaseYear
    val effectiveReleaseMonth = enrichedItem?.releaseMonth ?: releaseMonth

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = effectivePoster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = 0.83f), Color(0xFF07121E).copy(alpha = 0.95f), Color.Black)
            )))
        Column(
            modifier = Modifier.fillMaxSize().padding(if (isTv) 32.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 28.dp else 18.dp)
            ) {
                AsyncImage(
                    model = effectivePoster,
                    contentDescription = effectiveTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(posterWidth).height(posterHeight)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("TVFlow CINEMA  •  FILM", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(
                        effectiveTitle,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )

                    val dateLabel = when {
                        effectiveReleaseYear != null && effectiveReleaseMonth != null ->
                            monthName(effectiveReleaseMonth) + " " + effectiveReleaseYear
                        effectiveReleaseYear != null -> effectiveReleaseYear.toString()
                        else -> null
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        dateLabel?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                        effectiveRating?.takeIf { it.isNotBlank() }?.let {
                            Text("★ " + it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text(
                        effectiveSynopsis?.takeIf { it.isNotBlank() } ?: "Chargement du synopsis…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (isTv) 10 else 5,
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
                    "À découvrir",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(suggestions, key = { it.streamId }) { item ->
                        VodSuggestionCard(item = item, onClick = { onSuggestion(item) })
                    }
                }
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
            .width(112.dp)
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
                modifier = Modifier.fillMaxWidth().height(130.dp)
            )
            Text(
                item.name,
                modifier = Modifier.padding(7.dp),
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
