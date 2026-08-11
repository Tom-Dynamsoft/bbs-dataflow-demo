package com.dynamsoft.bbsdatareceiver.viewmodel

import android.net.Uri
import com.dynamsoft.bbsdatareceiver.model.AppState
import com.dynamsoft.bbsdatareceiver.model.BarcodeResult
import com.dynamsoft.bbsdatareceiver.model.EscalationConfig
import com.dynamsoft.bbsdatareceiver.scanner.EscalationPolicy
import com.dynamsoft.bbsdatareceiver.scanner.ResultMerger
import com.dynamsoft.dbr.BarcodeResultItem
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow<AppState>(AppState.Idle)
    val state: StateFlow<AppState> = _state.asStateFlow()

    private val _dbrResults = MutableStateFlow<List<BarcodeResult>>(emptyList())
    val dbrResults: StateFlow<List<BarcodeResult>> = _dbrResults.asStateFlow()

    private val _bbsResults = MutableStateFlow<List<BarcodeResult>>(emptyList())
    val bbsResults: StateFlow<List<BarcodeResult>> = _bbsResults.asStateFlow()

    private val _mergedResults = MutableStateFlow<List<BarcodeResult>>(emptyList())
    val mergedResults: StateFlow<List<BarcodeResult>> = _mergedResults.asStateFlow()

    private val _currentFrameCount = MutableStateFlow(0)
    val currentFrameCount: StateFlow<Int> = _currentFrameCount.asStateFlow()

    private val _debugLog = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debugLog.asStateFlow()

    // BBS result images
    private val _annotatedImageUri = MutableStateFlow<Uri?>(null)
    val annotatedImageUri: StateFlow<Uri?> = _annotatedImageUri.asStateFlow()

    val escalationPolicy = EscalationPolicy()

    // --- State transitions ---

    fun startScanning() {
        transition(AppState.Scanning)
    }

    fun onBarcodesDecoded(items: Array<BarcodeResultItem>) {
        if (_state.value !is AppState.Scanning) return

        // Deduplicate within this frame
        val frameResults = items.map { item ->
            BarcodeResult(
                text = item.text ?: "",
                format = item.formatString ?: "UNKNOWN",
                source = BarcodeResult.Source.DBR
            )
        }.filter { it.text.isNotEmpty() }

        val uniqueInFrame = frameResults.distinctBy { it.dedupKey }
        _currentFrameCount.value = frameResults.size  // raw count for density detection

        // Update session-level deduped results
        val currentList = _dbrResults.value.toMutableList()
        for (result in uniqueInFrame) {
            val existingIndex = currentList.indexOfFirst { it.dedupKey == result.dedupKey }
            if (existingIndex >= 0) {
                val existing = currentList[existingIndex]
                currentList[existingIndex] = existing.copy(count = existing.count + 1)
            } else {
                currentList.add(result)
            }
        }
        _dbrResults.value = currentList

        // Check escalation using raw item count (density), not unique count
        val shouldPrompt = escalationPolicy.onFrame(frameResults.size)
        Log.d("MainViewModel", "Frame: ${frameResults.size} raw, ${uniqueInFrame.size} unique, state=${_state.value::class.simpleName}, shouldPrompt=$shouldPrompt, threshold=${escalationPolicy.currentThreshold}")
        if (shouldPrompt) {
            transition(AppState.Prompting(frameResults.size))
        }
    }

    fun onPromptDecline() {
        escalationPolicy.onDecline()
        log("Prompt declined. Threshold now: ${escalationPolicy.currentThreshold}, suppressed: ${escalationPolicy.isSuppressed}")
        transition(AppState.Scanning)
    }

    fun onPromptAccept() {
        escalationPolicy.onAccept()
        log("Prompt accepted. Launching BBS...")
        transition(AppState.HandoffLaunching)
    }

    fun onBbsLaunched() {
        transition(AppState.WaitingForResults)
    }

    fun onBbsLaunchFailed() {
        transition(AppState.HandoffFailed)
    }

    fun onBbsResultsReceived(results: List<BarcodeResult>, annotatedUri: Uri?, originalUri: Uri?) {
        _bbsResults.value = results
        _annotatedImageUri.value = annotatedUri
        _mergedResults.value = ResultMerger.merge(_dbrResults.value, results)
        log("BBS returned ${results.size} results. Merged total: ${_mergedResults.value.size}")
        transition(AppState.Results(results))
    }

    fun onBbsNoResults() {
        log("BBS returned no results, resuming scanning")
        transition(AppState.Scanning)
    }

    fun resumeScanning() {
        transition(AppState.Scanning)
    }

    fun finish() {
        transition(AppState.Finished)
    }

    fun continueFromHandoffFailed() {
        transition(AppState.Scanning)
    }

    fun resetDemoState() {
        escalationPolicy.reset()
        _dbrResults.value = emptyList()
        _bbsResults.value = emptyList()
        _mergedResults.value = emptyList()
        _currentFrameCount.value = 0
        _annotatedImageUri.value = null
        _debugLog.value = emptyList()
        transition(AppState.Idle)
        log("Demo state reset")
    }

    fun simulateBarcodes() {
        if (_state.value !is AppState.Scanning) {
            startScanning()
        }
        val fakeBarcodes = (1..12).map { i ->
            BarcodeResult(
                text = "FAKE-BARCODE-${String.format("%03d", i)}",
                format = if (i % 3 == 0) "QR_CODE" else "CODE_128",
                source = BarcodeResult.Source.DBR
            )
        }
        val currentList = _dbrResults.value.toMutableList()
        for (result in fakeBarcodes) {
            val existingIndex = currentList.indexOfFirst { it.dedupKey == result.dedupKey }
            if (existingIndex >= 0) {
                val existing = currentList[existingIndex]
                currentList[existingIndex] = existing.copy(count = existing.count + 1)
            } else {
                currentList.add(result)
            }
        }
        _dbrResults.value = currentList
        _currentFrameCount.value = fakeBarcodes.size
        log("Simulated ${fakeBarcodes.size} barcodes (total unique: ${currentList.size})")

        // Check escalation
        if (escalationPolicy.onFrame(fakeBarcodes.size)) {
            transition(AppState.Prompting(fakeBarcodes.size))
        }
    }

    fun updateEscalationConfig(config: EscalationConfig) {
        escalationPolicy.config = config
        log("Escalation config updated: $config")
    }

    private fun transition(newState: AppState) {
        val oldState = _state.value
        _state.value = newState
        log("${oldState::class.simpleName} → ${newState::class.simpleName}")
    }

    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(System.currentTimeMillis())
        _debugLog.value = _debugLog.value + "[$timestamp] $message"
    }
}
