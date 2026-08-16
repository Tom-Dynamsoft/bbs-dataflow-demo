package com.dynamsoft.bbsdatareceiver.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import com.dynamsoft.bbsdatareceiver.model.AppState
import com.dynamsoft.bbsdatareceiver.model.BarcodeResult
import com.dynamsoft.bbsdatareceiver.model.EscalationConfig
import com.dynamsoft.bbsdatareceiver.scanner.EscalationPolicy
import com.dynamsoft.bbsdatareceiver.scanner.ResultMerger
import com.dynamsoft.dbr.BarcodeResultItem
import com.dynamsoft.dbr.DecodedBarcodesResult
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

    // Comparison images
    private val _dbrAnnotatedBitmap = MutableStateFlow<Bitmap?>(null)
    val dbrAnnotatedBitmap: StateFlow<Bitmap?> = _dbrAnnotatedBitmap.asStateFlow()

    private val _bbsAnnotatedBitmap = MutableStateFlow<Bitmap?>(null)
    val bbsAnnotatedBitmap: StateFlow<Bitmap?> = _bbsAnnotatedBitmap.asStateFlow()

    // Live annotation preview (updated every 3s during scanning)
    private val _liveAnnotatedBitmap = MutableStateFlow<Bitmap?>(null)
    val liveAnnotatedBitmap: StateFlow<Bitmap?> = _liveAnnotatedBitmap.asStateFlow()

    // BBS button enabled when escalation threshold is met
    private val _bbsButtonEnabled = MutableStateFlow(false)
    val bbsButtonEnabled: StateFlow<Boolean> = _bbsButtonEnabled.asStateFlow()

    private val _annotatedImageUri = MutableStateFlow<Uri?>(null)
    val annotatedImageUri: StateFlow<Uri?> = _annotatedImageUri.asStateFlow()

    /** Latest barcode items from the most recent frame — used for photo annotation at escalation. */
    var latestBarcodeItems: Array<BarcodeResultItem>? = null
        private set


    val escalationPolicy = EscalationPolicy()

    // --- State transitions ---

    fun startScanning() {
        transition(AppState.Scanning)
    }

    fun onBarcodesDecoded(result: DecodedBarcodesResult) {
        val items = result.items ?: return
        if (_state.value !is AppState.Scanning) return

        // Store latest items for photo annotation at escalation time
        latestBarcodeItems = items

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

        // Keep only the latest frame's results (no accumulation)
        _dbrResults.value = uniqueInFrame

        // Check escalation: density (10+) AND instability (results changing between frames)
        val frameKeys = uniqueInFrame.map { it.dedupKey }.toSet()
        val shouldPrompt = escalationPolicy.onFrame(frameResults.size, frameKeys)
        Log.d("MainViewModel", "Frame: ${frameResults.size} raw, ${uniqueInFrame.size} unique, state=${_state.value::class.simpleName}, shouldPrompt=$shouldPrompt, threshold=${escalationPolicy.currentThreshold}")
        if (shouldPrompt && !_bbsButtonEnabled.value) {
            _bbsButtonEnabled.value = true
            log("Escalation triggered (dense + unstable) — BBS button enabled")
        }
    }

    fun onBbsButtonTapped() {
        escalationPolicy.onAccept()
        // Snapshot the current live preview as the DBR comparison image
        val livePreview = _liveAnnotatedBitmap.value
        if (livePreview != null) {
            _dbrAnnotatedBitmap.value = livePreview.copy(livePreview.config ?: Bitmap.Config.ARGB_8888, false)
        }
        log("BBS button tapped. Launching BBS...")
        transition(AppState.HandoffLaunching)
    }

    fun setLiveAnnotatedBitmap(bitmap: Bitmap?) {
        val old = _liveAnnotatedBitmap.value
        _liveAnnotatedBitmap.value = bitmap
        if (old != null && old !== bitmap) old.recycle()
    }

    fun onBbsLaunched() {
        transition(AppState.WaitingForResults)
    }

    fun onBbsLaunchFailed() {
        transition(AppState.HandoffFailed)
    }

    fun setDbrAnnotatedBitmap(bitmap: Bitmap) {
        _dbrAnnotatedBitmap.value = bitmap
    }

    fun setBbsAnnotatedBitmap(bitmap: Bitmap?) {
        _bbsAnnotatedBitmap.value = bitmap
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
        _dbrAnnotatedBitmap.value?.recycle()
        _dbrAnnotatedBitmap.value = null
        _bbsAnnotatedBitmap.value?.recycle()
        _bbsAnnotatedBitmap.value = null
        _liveAnnotatedBitmap.value?.recycle()
        _liveAnnotatedBitmap.value = null
        _bbsButtonEnabled.value = false
        latestBarcodeItems = null
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

        // Check escalation — simulate with changing keys to trigger instability
        val fakeKeys = fakeBarcodes.map { it.dedupKey }.toSet()
        if (escalationPolicy.onFrame(fakeBarcodes.size, fakeKeys) && !_bbsButtonEnabled.value) {
            _bbsButtonEnabled.value = true
            log("Escalation triggered (simulated) — BBS button enabled")
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
