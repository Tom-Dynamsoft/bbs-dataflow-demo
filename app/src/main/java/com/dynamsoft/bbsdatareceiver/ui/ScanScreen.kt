package com.dynamsoft.bbsdatareceiver.ui

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.dynamsoft.dce.CameraView
import kotlin.math.roundToInt

@Composable
fun ScanScreen(
    cameraView: CameraView?,
    liveAnnotatedBitmap: Bitmap? = null,
    bbsButtonEnabled: Boolean = false,
    onLaunchBbs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Full-screen camera with all UI as floating overlays
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // Camera view — true full screen
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
                Text("Camera initializing...", color = Color.White)
            }
        }

        // Top overlay: Launch BBS button (always visible) + warning banner (on trigger)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            // Launch BBS button — always available
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = onLaunchBbs,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Launch Batch Scanner")
                    }
                }
            }

            // Warning banner — only when escalation triggers
            AnimatedVisibility(
                visible = bbsButtonEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Too many barcodes — try Batch Scanner for better results",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // Draggable annotation preview overlay — starts at lower-left
        if (liveAnnotatedBitmap != null) {
            val previewMaxWidth = 160.dp
            val aspect = liveAnnotatedBitmap.width.toFloat() / liveAnnotatedBitmap.height.coerceAtLeast(1)
            val previewWidth = previewMaxWidth
            val previewHeight = previewMaxWidth / aspect
            val density = LocalDensity.current
            var offsetX by remember { mutableFloatStateOf(with(density) { 12.dp.toPx() }) }
            var offsetY by remember {
                mutableFloatStateOf(with(density) { 400.dp.toPx() })
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val maxX = with(density) { maxWidth.toPx() - previewWidth.toPx() }
                val maxY = with(density) { maxHeight.toPx() - previewHeight.toPx() }
                LaunchedEffect(maxY) {
                    if (offsetY > maxY) offsetY = (maxY - with(density) { 12.dp.toPx() }).coerceAtLeast(0f)
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .width(previewWidth + 4.dp)
                        .height(previewHeight + 4.dp)
                        .shadow(6.dp, RoundedCornerShape(6.dp))
                        .clip(RoundedCornerShape(6.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount.x).coerceIn(0f, maxX)
                                offsetY = (offsetY + dragAmount.y).coerceIn(0f, maxY)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = liveAnnotatedBitmap.asImageBitmap(),
                        contentDescription = "Annotated barcode preview",
                        modifier = Modifier.width(previewWidth).height(previewHeight),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }
}

