package com.dynamsoft.bbsdatareceiver

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.dynamsoft.bbsdatareceiver.bbs.BbsLauncher
import com.dynamsoft.bbsdatareceiver.bbs.BbsResultParser
import com.dynamsoft.bbsdatareceiver.model.AppState
import com.dynamsoft.bbsdatareceiver.scanner.BarcodeImageAnnotator
import com.dynamsoft.bbsdatareceiver.scanner.DbrScanner
import com.dynamsoft.bbsdatareceiver.scanner.ResultMerger
import com.dynamsoft.bbsdatareceiver.ui.*
import com.dynamsoft.bbsdatareceiver.viewmodel.MainViewModel
import com.dynamsoft.dce.CameraView
import com.dynamsoft.dce.utils.PermissionUtil
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private lateinit var cameraView: CameraView
    private lateinit var dbrScanner: DbrScanner
    private val vm: MainViewModel by viewModels()
    private var annotationJob: Job? = null

    private val bbsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "bbsLauncher result: resultCode=${result.resultCode}, data=${result.data}")
        if (result.data != null) {
            val parsed = BbsResultParser.parse(this, result.data)
            Log.d(TAG, "Parsed BBS result: ${parsed?.barcodes?.size ?: 0} barcodes, annotated=${parsed?.annotatedImageUri}, original=${parsed?.originalImageUri}")
            if (parsed != null && parsed.barcodes.isNotEmpty()) {
                val bbsBitmap = parsed.annotatedImageUri?.let { uri ->
                    try {
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load BBS annotated image", e)
                        null
                    }
                }
                vm.setBbsAnnotatedBitmap(bbsBitmap)
                vm.onBbsResultsReceived(parsed.barcodes, parsed.annotatedImageUri, parsed.originalImageUri)
            } else {
                Log.w(TAG, "BBS returned data but no barcodes parsed")
                vm.onBbsNoResults()
            }
        } else {
            Log.d(TAG, "BBS returned no data (canceled or empty)")
            vm.onBbsNoResults()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DbrScanner.initLicense()
        PermissionUtil.requestCameraPermission(this)

        cameraView = CameraView(this)
        dbrScanner = DbrScanner(cameraView, this).also { scanner ->
            scanner.onBarcodesDecoded = { result ->
                runOnUiThread { vm.onBarcodesDecoded(result) }
            }
        }

        setContent {
            MaterialTheme {
                MainScreen(
                    viewModel = vm,
                    cameraView = cameraView,
                    onStartScanning = { startScanning() },
                    onStopScanning = { stopScanning() },
                    onLaunchBbs = { captureAndLaunchBbs() },
                    onExportCsv = { exportCsv() }
                )
            }
        }
    }

    private var scanRegionSet = false

    private fun startScanning() {
        vm.startScanning()
        dbrScanner.startScanning()
        scanRegionSet = false
        startAnnotationTimer()
    }

    private fun stopScanning() {
        stopAnnotationTimer()
        dbrScanner.stopScanning()
    }

    private fun startAnnotationTimer() {
        annotationJob?.cancel()
        annotationJob = lifecycleScope.launch {
            while (true) {
                delay(3000)
                updateLiveAnnotation()
            }
        }
    }

    private fun stopAnnotationTimer() {
        annotationJob?.cancel()
        annotationJob = null
    }

    private fun updateLiveAnnotation() {
        // Compute scan region dynamically once we have both view dimensions and a frame
        if (!scanRegionSet && dbrScanner.latestFrame != null && cameraView.width > 0) {
            dbrScanner.setScanRegionToVisibleArea()
            scanRegionSet = true
        }

        val items = vm.latestBarcodeItems
        val frameBitmap = dbrScanner.getLatestFrameAsBitmap() ?: return
        // Crop to scan region — SDK barcode coordinates are relative to the scan region
        val cropped = dbrScanner.cropToScanRegion(frameBitmap)
        if (cropped !== frameBitmap) frameBitmap.recycle()

        if (items != null && items.isNotEmpty()) {
            val annotated = BarcodeImageAnnotator.annotate(cropped, items, 1f, 1f)
            vm.setLiveAnnotatedBitmap(annotated)
            if (annotated !== cropped) cropped.recycle()
        } else {
            vm.setLiveAnnotatedBitmap(cropped)
        }
    }

    /**
     * Launch BBS. The live preview is already being updated every 3s with annotations,
     * so onBbsButtonTapped() snapshots it as the DBR comparison image.
     */
    private fun captureAndLaunchBbs() {
        vm.onBbsButtonTapped()

        // Stop camera — BBS needs it
        stopScanning()

        try {
            val intent = BbsLauncher.buildIntent(BbsLauncher.Scenario.PANORAMA_SCAN)
            bbsLauncher.launch(intent)
            vm.onBbsLaunched()
        } catch (e: ActivityNotFoundException) {
            vm.onBbsLaunchFailed()
        }
    }

    private fun exportCsv() {
        val csv = ResultMerger.toCsv(vm.mergedResults.value)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_TEXT, csv)
            putExtra(Intent.EXTRA_SUBJECT, "Barcode Scan Results")
        }
        startActivity(Intent.createChooser(sendIntent, "Export CSV"))
    }

    override fun onResume() {
        super.onResume()
        if (vm.state.value is AppState.Scanning) {
            dbrScanner.startScanning()
            startAnnotationTimer()
        }
    }

    override fun onPause() {
        stopAnnotationTimer()
        dbrScanner.stopScanning()
        super.onPause()
    }

    override fun onDestroy() {
        dbrScanner.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    cameraView: CameraView?,
    onStartScanning: () -> Unit,
    onStopScanning: () -> Unit,
    onLaunchBbs: () -> Unit,
    onExportCsv: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val dbrResults by viewModel.dbrResults.collectAsStateWithLifecycle()
    val mergedResults by viewModel.mergedResults.collectAsStateWithLifecycle()
    val frameCount by viewModel.currentFrameCount.collectAsStateWithLifecycle()
    val debugLog by viewModel.debugLog.collectAsStateWithLifecycle()
    val dbrAnnotatedBitmap by viewModel.dbrAnnotatedBitmap.collectAsStateWithLifecycle()
    val bbsAnnotatedBitmap by viewModel.bbsAnnotatedBitmap.collectAsStateWithLifecycle()
    val liveAnnotatedBitmap by viewModel.liveAnnotatedBitmap.collectAsStateWithLifecycle()
    val bbsButtonEnabled by viewModel.bbsButtonEnabled.collectAsStateWithLifecycle()

    var showDebug by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DBR -> BBS Demo") },
                actions = {
                    TextButton(onClick = { showDebug = !showDebug }) {
                        Text(if (showDebug) "Hide Debug" else "Debug")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Debug panel at top when visible
            DebugPanel(
                visible = showDebug,
                logs = debugLog,
                currentConfig = viewModel.escalationPolicy.config,
                currentThreshold = viewModel.escalationPolicy.currentThreshold,
                isSuppressed = viewModel.escalationPolicy.isSuppressed,
                onConfigChange = { viewModel.updateEscalationConfig(it) },
                onResetDemo = {
                    onStopScanning()
                    viewModel.resetDemoState()
                },
                onSimulateBarcodes = { viewModel.simulateBarcodes() },
                onManualLaunchBbs = { onLaunchBbs() }
            )

            // Main content based on state
            when (state) {
                is AppState.Idle -> {
                    IdleScreen(
                        onStartScanning = onStartScanning,
                        onLaunchBbs = onLaunchBbs
                    )
                }

                is AppState.Scanning -> {
                    ScanScreen(
                        cameraView = cameraView,
                        liveAnnotatedBitmap = liveAnnotatedBitmap,
                        bbsButtonEnabled = bbsButtonEnabled,
                        onLaunchBbs = onLaunchBbs
                    )
                }

                is AppState.HandoffLaunching -> {
                    WaitingScreen(onCancel = {
                        viewModel.resumeScanning()
                        onStartScanning()
                    })
                }

                is AppState.WaitingForResults -> {
                    WaitingScreen(onCancel = {
                        viewModel.resumeScanning()
                        onStartScanning()
                    })
                }

                is AppState.HandoffFailed -> {
                    HandoffFailedScreen(onContinueScanning = {
                        viewModel.continueFromHandoffFailed()
                        onStartScanning()
                    })
                }

                is AppState.Results -> {
                    ResultsScreen(
                        results = mergedResults,
                        dbrAnnotatedBitmap = dbrAnnotatedBitmap,
                        bbsAnnotatedBitmap = bbsAnnotatedBitmap,
                        onResume = {
                            viewModel.resumeScanning()
                            onStartScanning()
                        },
                        onDone = { viewModel.finish() },
                        onExportCsv = onExportCsv
                    )
                }

                is AppState.Finished -> {
                    FinishedScreen(
                        totalResults = mergedResults.size,
                        onReset = {
                            onStopScanning()
                            viewModel.resetDemoState()
                        }
                    )
                }
            }
        }
    }
}
