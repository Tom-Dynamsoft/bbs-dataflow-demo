package com.dynamsoft.bbsdatareceiver.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dynamsoft.bbsdatareceiver.model.BarcodeResult

enum class ResultFilter { ALL, DBR, BBS }

@Composable
fun ResultsScreen(
    results: List<BarcodeResult>,
    dbrAnnotatedBitmap: Bitmap?,
    bbsAnnotatedBitmap: Bitmap?,
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
                Spacer(modifier = Modifier.height(8.dp))

                // DBR vs BBS counts side by side (hide DBR card if no DBR results)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (dbrCount > 0) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$dbrCount",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "DBR",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$bbsCount",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = "BBS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

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

        // Scrollable content: comparison images + results list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Comparison images at top
            if (dbrAnnotatedBitmap != null || bbsAnnotatedBitmap != null) {
                item(key = "comparison_images") {
                    ComparisonImageSection(
                        dbrBitmap = dbrAnnotatedBitmap,
                        bbsBitmap = bbsAnnotatedBitmap
                    )
                }
            }

            // Barcode result items
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
private fun ComparisonImageSection(
    dbrBitmap: Bitmap?,
    bbsBitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        val hasComparison = dbrBitmap != null && bbsBitmap != null
        Text(
            text = if (hasComparison) "Image Comparison" else "Scan Image",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // DBR Image
        if (dbrBitmap != null) {
            AnnotatedImageCard(
                label = "DBR — Dynamsoft Barcode Reader",
                labelColor = MaterialTheme.colorScheme.primary,
                bitmap = dbrBitmap
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // BBS Image
        if (bbsBitmap != null) {
            AnnotatedImageCard(
                label = "BBS — Batch Barcode Scanner",
                labelColor = MaterialTheme.colorScheme.tertiary,
                bitmap = bbsBitmap
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
    }
}

@Composable
private fun AnnotatedImageCard(
    label: String,
    labelColor: Color,
    bitmap: Bitmap,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = labelColor,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = label,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black),
                contentScale = ContentScale.FillWidth
            )
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
