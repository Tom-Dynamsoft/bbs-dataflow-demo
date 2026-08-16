package com.dynamsoft.bbsdatareceiver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun IdleScreen(
    onStartScanning: () -> Unit,
    onLaunchBbs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Barcode Scanner",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Choose a scanning mode to get started.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onStartScanning,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Real-Time Scan (DBR)", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Best for fewer barcodes. Uses the camera live feed.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onLaunchBbs,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Batch Scan (BBS)", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Best for dense scenes. 100% read rate and accuracy.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
