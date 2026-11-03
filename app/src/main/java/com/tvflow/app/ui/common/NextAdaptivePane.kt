package com.tvflow.app.ui.common

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tvflow.app.BuildConfig
import com.tvflow.app.util.isTelevisionUi

@Composable
internal fun isNextPortraitLayout(): Boolean =
    BuildConfig.APPLICATION_ID.endsWith(".next") &&
        !LocalContext.current.isTelevisionUi() &&
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT

/** Preserve the lazy sidebar on wide screens; let portrait content use the full width. */
@Composable
internal fun NextAdaptivePane(
    compact: Boolean,
    sidebarWidth: Dp,
    categoryLabel: String,
    modifier: Modifier = Modifier,
    sidebar: @Composable (Modifier, () -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    var categoriesOpen by rememberSaveable(compact) { mutableStateOf(false) }
    if (compact) {
        Column(modifier) {
            OutlinedButton(onClick = { categoriesOpen = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Text("Catégories · $categoryLabel", maxLines = 2)
            }
            Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        }
        if (categoriesOpen) {
            val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .65f }
                .coerceIn(180.dp, 480.dp)
            Dialog(onDismissRequest = { categoriesOpen = false }) {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Choisir une catégorie", style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.fillMaxWidth().height(height).padding(top = 8.dp)) {
                            sidebar(Modifier.fillMaxSize()) { categoriesOpen = false }
                        }
                        TextButton(onClick = { categoriesOpen = false }) { Text("Fermer") }
                    }
                }
            }
        }
    } else {
        Row(modifier) {
            sidebar(Modifier.width(sidebarWidth).fillMaxHeight()) { }
            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NextWrappingRow(
    wrap: Boolean,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    if (wrap) FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)) { content() }
    else Row(horizontalArrangement = Arrangement.spacedBy(spacing)) { content() }
}
