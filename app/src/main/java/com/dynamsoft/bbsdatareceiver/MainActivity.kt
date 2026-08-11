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
import com.dynamsoft.bbsdatareceiver.bbs.BbsLauncher
import com.dynamsoft.bbsdatareceiver.bbs.BbsResultParser
import com.dynamsoft.bbsdatareceiver.model.AppState
import com.dynamsoft.bbsdatareceiver.scanner.DbrScanner
import com.dynamsoft.bbsdatareceiver.scanner.ResultMerger
import com.dynamsoft.bbsdatareceiver.ui.*
import com.dynamsoft.bbsdatareceiver.viewmodel.MainViewModel
import com.dynamsoft.dce.CameraView
import com.dynamsoft.dce.utils.PermissionUtil

class MainActivity : ComponentActivity() {

    private lateinit var cameraView: CameraView
    private lateinit var dbrScanner: DbrScanner
    private val vm: MainViewModel by viewModels()

    private val bbsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.data != null) {
            val parsed = BbsResultParser.parse(this, result.data)
            if (parsed != null && parsed.barcodes.isNotEmpty()) {
                vm.onBbsResultsReceived(parsed.barcodes, parsed.annotatedImageUri, parsed.originalImageUri)
            } else {
                vm.onBbsNoResults()
            }
        } else {
            vm.onBbsNoResults()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize DBR license once
        DbrScanner.initLicense()

        // Request camera permission early
        PermissionUtil.requestCameraPermission(this)

        // Create CameraView and DbrScanner eagerly in onCreate (matching official sample)
        cameraView = CameraView(this)
        dbrScanner = DbrScanner(cameraView, this).also { scanner ->
            scanner.onBarcodesDecoded = { items ->
                runOnUiThread { vm.onBarcodesDecoded(items) }
            }
        }

        setContent {
            MaterialTheme {
                MainScreen(
                    viewModel = vm,
                    cameraView = cameraView,
                    onStartScanning = { startScanning() },
                    onStopScanning = { stopScanning() },
                    onLaunchBbs = { launchBbs() },
                    onExportCsv = { exportCsv() }
                )
            }
        }
    }

    private fun startScanning() {
        vm.startScanning()
        dbrScanner.startScanning()
    }

    private fun stopScanning() {
        dbrScanner.stopScanning()
    }

    private fun launchBbs() {
        // Stop camera first — BBS needs it
        stopScanning()

        try {
            val intent = BbsLauncher.buildIntent(BbsLauncher.Scenario.FOV_SCAN)
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
        }
    }

    override fun onPause() {
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
    val annotatedImageUri by viewModel.annotatedImageUri.collectAsStateWithLifecycle()

    var showDebug by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DBR → BBS Demo") },
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
            when (val s = state) {
                is AppState.Idle -> {
                    IdleScreen(onStartScanning = onStartScanning)
                }

                is AppState.Scanning -> {
                    ScanScreen(
                        cameraView = cameraView,
                        results = dbrResults,
                        currentFrameCount = frameCount
                    )
                }

                is AppState.Prompting -> {
                    ScanScreen(
                        cameraView = cameraView,
                        results = dbrResults,
                        currentFrameCount = frameCount
                    )
                    EscalationPrompt(
                        frameCount = s.frameCount,
                        onDismiss = { viewModel.onPromptDecline() },
                        onAccept = {
                            viewModel.onPromptAccept()
                            onLaunchBbs()
                        }
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
                        annotatedImageUri = annotatedImageUri,
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
