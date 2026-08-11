package com.dynamsoft.bbsdatareceiver.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dynamsoft.bbsdatareceiver.model.EscalationConfig

@Composable
fun DebugPanel(
    visible: Boolean,
    logs: List<String>,
    currentConfig: EscalationConfig,
    currentThreshold: Int,
    isSuppressed: Boolean,
    onConfigChange: (EscalationConfig) -> Unit,
    onResetDemo: () -> Unit,
    onSimulateBarcodes: (() -> Unit)? = null,
    onManualLaunchBbs: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = visible, modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            tonalElevation = 8.dp,
            color = Color(0xFF1A1A2E)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                // Config summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Threshold: $currentThreshold | Suppressed: $isSuppressed",
                        color = Color(0xFF00FF88),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(onClick = onResetDemo) {
                        Text("Reset Demo", fontSize = 11.sp, color = Color(0xFFFF6B6B))
                    }
                }

                // Debug action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onSimulateBarcodes != null) {
                        TextButton(onClick = onSimulateBarcodes) {
                            Text("+ Fake Barcodes", fontSize = 11.sp, color = Color(0xFF88CCFF))
                        }
                    }
                    if (onManualLaunchBbs != null) {
                        TextButton(onClick = onManualLaunchBbs) {
                            Text("Launch BBS", fontSize = 11.sp, color = Color(0xFFFFAA44))
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF333355))

                // Log entries
                val listState = rememberLazyListState()
                LaunchedEffect(logs.size) {
                    if (logs.isNotEmpty()) {
                        listState.animateScrollToItem(logs.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(logs) { entry ->
                        Text(
                            text = entry,
                            color = Color(0xFFCCCCCC),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
