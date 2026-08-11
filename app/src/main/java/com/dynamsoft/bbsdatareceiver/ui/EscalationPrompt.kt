package com.dynamsoft.bbsdatareceiver.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun EscalationPrompt(
    frameCount: Int,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Lots of barcodes in view",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "We detected $frameCount barcodes at once. " +
                        "Dynamsoft Batch Barcode Scanner can capture 100+ codes " +
                        "in a single pass with higher accuracy. Switch to it?"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("Use Batch Barcode Scanner")
            }
        }
    )
}
