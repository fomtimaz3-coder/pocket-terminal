package com.pocketterminal.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalKeyboard(
    ctrlActive: Boolean,
    onCtrlToggle: () -> Unit,
    onSpecial: (String) -> Unit
) {
    val keys = listOf(
        "ESC", "TAB", "CTRL", "CTRL+C", "CTRL+L", "ALT",
        "↑", "↓", "←", "→", "/", "|", "~", "-", "_"
    )
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { label ->
            val selected = label == "CTRL" && ctrlActive
            FilterChip(
                selected = selected,
                onClick = {
                    if (label == "CTRL") onCtrlToggle() else onSpecial(label)
                },
                label = { Text(label, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0xFF1A2026),
                    labelColor = Color(0xFFD7E0E7),
                    selectedContainerColor = Color(0xFF9BE15D),
                    selectedLabelColor = Color(0xFF17210E)
                )
            )
        }
    }
}