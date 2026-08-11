package com.dynamsoft.bbsdatareceiver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dynamsoft.bbsdatareceiver.model.BarcodeResult
import com.dynamsoft.dce.CameraView

@Composable
fun ScanScreen(
    cameraView: CameraView?,
    results: List<BarcodeResult>,
    currentFrameCount: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Camera preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (cameraView != null) {
                AndroidView(
                    factory = { cameraView },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera initializing...")
                }
            }
        }

        // Header: counts
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${results.size} unique codes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$currentFrameCount in current frame",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider()

        // Results list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f)
        ) {
            items(results, key = { it.dedupKey }) { result ->
                BarcodeResultItem(result)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BarcodeResultItem(result: BarcodeResult) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            Badge(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Text("×${result.count}")
            }
        }
    }
}
