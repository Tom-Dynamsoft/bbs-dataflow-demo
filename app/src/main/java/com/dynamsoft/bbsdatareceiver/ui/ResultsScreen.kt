package com.dynamsoft.bbsdatareceiver.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dynamsoft.bbsdatareceiver.model.BarcodeResult

enum class ResultFilter { ALL, DBR, BBS }

@Composable
fun ResultsScreen(
    results: List<BarcodeResult>,
    annotatedImageUri: Uri?,
    onResume: () -> Unit,
    onDone: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(ResultFilter.ALL) }

    val filtered = when (filter) {
        ResultFilter.ALL -> results
        ResultFilter.DBR -> results.filter { it.source == BarcodeResult.Source.DBR }
        ResultFilter.BBS -> results.filter { it.source == BarcodeResult.Source.BBS }
    }

    val dbrCount = results.count { it.source == BarcodeResult.Source.DBR }
    val bbsCount = results.count { it.source == BarcodeResult.Source.BBS }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Surface(tonalElevation = 2.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Scan Results",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${results.size} total · $dbrCount from DBR · $bbsCount from BBS",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Filter chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filter == ResultFilter.ALL,
                        onClick = { filter = ResultFilter.ALL },
                        label = { Text("All (${results.size})") }
                    )
                    FilterChip(
                        selected = filter == ResultFilter.DBR,
                        onClick = { filter = ResultFilter.DBR },
                        label = { Text("DBR ($dbrCount)") }
                    )
                    FilterChip(
                        selected = filter == ResultFilter.BBS,
                        onClick = { filter = ResultFilter.BBS },
                        label = { Text("BBS ($bbsCount)") }
                    )
                }
            }
        }

        HorizontalDivider()

        // Results list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(filtered, key = { "${it.source}|${it.dedupKey}" }) { result ->
                MergedResultItem(result)
                HorizontalDivider()
            }
        }

        // Action buttons
        Surface(tonalElevation = 4.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportCsv,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export CSV")
                }
                OutlinedButton(
                    onClick = onResume,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Resume Scan")
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun MergedResultItem(result: BarcodeResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Source badge
        Badge(
            containerColor = when (result.source) {
                BarcodeResult.Source.DBR -> MaterialTheme.colorScheme.primaryContainer
                BarcodeResult.Source.BBS -> MaterialTheme.colorScheme.tertiaryContainer
            },
            contentColor = when (result.source) {
                BarcodeResult.Source.DBR -> MaterialTheme.colorScheme.onPrimaryContainer
                BarcodeResult.Source.BBS -> MaterialTheme.colorScheme.onTertiaryContainer
            }
        ) {
            Text(result.source.name, modifier = Modifier.padding(horizontal = 4.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )
            Text(
                text = result.format,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (result.count > 1) {
            Text(
                text = "×${result.count}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
