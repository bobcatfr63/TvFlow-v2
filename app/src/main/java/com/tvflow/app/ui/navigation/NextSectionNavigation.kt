package com.tvflow.app.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NextSectionNavigation(
    current: NextSection,
    television: Boolean,
    onSelect: (NextSection) -> Unit
) {
    val selectedFocus = remember { FocusRequester() }
    LaunchedEffect(current, television) {
        if (television) {
            withFrameNanos { }
            selectedFocus.requestFocus()
        }
    }
    Row(
        Modifier.fillMaxWidth().background(Color(0xF20A1524))
            .statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("TVFlow Next", color = Color.White, fontWeight = FontWeight.Bold,
            fontSize = 15.sp, modifier = Modifier.width(96.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            NextSection.entries.forEach { section ->
                var focused by remember { mutableStateOf(false) }
                val active = section == current
                Surface(
                    onClick = { onSelect(section) },
                    modifier = Modifier.heightIn(min = 48.dp)
                        .then(if (active) Modifier.focusRequester(selectedFocus) else Modifier)
                        .onFocusChanged { focused = it.isFocused }
                        .semantics { selected = active },
                    shape = RoundedCornerShape(14.dp),
                    color = if (active) Color(0xFF005DCF) else Color(0xFF142235),
                    contentColor = Color.White,
                    border = BorderStroke(if (focused) 2.dp else 1.dp,
                        if (focused) Color(0xFF67D6FF) else Color(0xFF31445C))
                ) {
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center) {
                        Text(section.label, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
