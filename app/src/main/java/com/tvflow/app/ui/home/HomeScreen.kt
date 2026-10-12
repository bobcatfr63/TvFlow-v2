package com.tvflow.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.tvflow.app.domain.LiveChannel
import com.tvflow.app.ui.common.ParentalPinDialog

@Composable
fun HomeScreen(
    onPlay: (LiveChannel) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedSource by viewModel.selectedSource.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val selectedSubFilterId by viewModel.selectedSubFilterId.collectAsState()
    val pinRequiredCategory by viewModel.pinRequiredCategory.collectAsState()
    val pinError by viewModel.pinError.collectAsState()

    pinRequiredCategory?.let { category ->
        ParentalPinDialog(
            categoryName = category.name,
            error = pinError,
            onConfirm = viewModel::confirmParentalPin,
            onDismiss = viewModel::dismissParentalPin
        )
    }

    LaunchedEffect(categories, selectedSource) {
        if (selectedSource == null && categories.isNotEmpty()) {
            viewModel.selectCategory(categories.first().id)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 24.dp)
    ) {
        LazyColumn(
            modifier = Modifier.width(310.dp).fillMaxHeight().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "TVFlow",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(12.dp)
                )
            }
            item {
                MenuCard("★ Favoris") { viewModel.selectFavorites() }
            }
            item {
                MenuCard("↻ Récents") { viewModel.selectRecent() }
            }
            items(categories, key = { it.id }) { category ->
                MenuCard(category.name) { viewModel.selectCategory(category.id) }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (channels.isEmpty()) {
                val message = when (selectedSource) {
                    ChannelSource.Favorites -> "Aucune chaîne favorite"
                    ChannelSource.Recent -> "Aucune chaîne regardée récemment"
                    null -> "Chargement du catalogue…"
                    is ChannelSource.Category -> if (isLoading) "Chargement des chaînes…" else "Aucune chaîne dans cette catégorie"
                }
                Text(
                    message,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)
                ) {
                    val selectedCategory = (selectedSource as? ChannelSource.Category)
                        ?.let { source -> categories.firstOrNull { it.id == source.id } }
                    selectedCategory?.takeIf { it.subFilters.isNotEmpty() }?.let { category ->
                        SubFilterBar(
                            category = category,
                            selectedId = selectedSubFilterId,
                            onSelect = viewModel::selectSubFilter
                        )
                    }
                    SortToolbar(
                        sort = selectedSort,
                        channelCount = channels.size,
                        onSort = viewModel::cycleSort
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = channels,
                            key = { it.streamId },
                            contentType = { "live-channel" }
                        ) { channel ->
                            ChannelRow(
                                channel = channel,
                                onClick = { onPlay(channel) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuCard(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (focused) 1.04f else 1f
                scaleY = if (focused) 1.04f else 1f
            },
        colors = CardDefaults.cardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            if (focused) 4.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            label,
            color = if (focused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun SubFilterBar(
    category: com.tvflow.app.domain.LiveCategory,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip("Tous", selectedId == null) { onSelect(null) }
        }
        items(category.subFilters, key = { it.id }) { filter ->
            FilterChip(filter.name, selectedId == filter.id) { onSelect(filter.id) }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier.graphicsLayer {
            scaleX = if (focused) 1.06f else 1f
            scaleY = if (focused) 1.06f else 1f
        },
        colors = CardDefaults.cardColors(
            containerColor = when {
                focused -> MaterialTheme.colorScheme.primaryContainer
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            if (focused) 4.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Text(
            label,
            color = when {
                focused -> MaterialTheme.colorScheme.onPrimaryContainer
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun SortToolbar(
    sort: HomeSort,
    channelCount: Int,
    onSort: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$channelCount chaînes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Card(
            onClick = onSort,
            interactionSource = interaction,
            modifier = Modifier.graphicsLayer {
                scaleX = if (focused) 1.06f else 1f
                scaleY = if (focused) 1.06f else 1f
            },
            colors = CardDefaults.cardColors(
                containerColor = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
            ),
            border = BorderStroke(
                if (focused) 4.dp else 1.dp,
                MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "TRI : " + when (sort) {
                    HomeSort.TNT -> "TNT"
                    HomeSort.NAME -> "A-Z"
                    HomeSort.FAVORITES -> "FAVORIS"
                    HomeSort.EPG -> "EPG"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun ChannelRow(channel: LiveChannel, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (focused) 1.015f else 1f
                scaleY = if (focused) 1.015f else 1f
            },
        colors = CardDefaults.cardColors(
            containerColor = if (focused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            if (focused) 4.dp else 1.dp,
            if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                modifier = Modifier.size(width = 72.dp, height = 48.dp).padding(end = 12.dp),
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    channel.logicalNumber?.let { "$it  ${channel.name}" } ?: channel.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (!channel.epgChannelId.isNullOrBlank()) {
                    Text(channel.epgChannelId, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (channel.isFavorite) {
                Text("★", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
