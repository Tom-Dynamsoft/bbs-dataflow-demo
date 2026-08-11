package com.dynamsoft.bbsdatareceiver.model

sealed class AppState {
    data object Idle : AppState()
    data object Scanning : AppState()
    data class Prompting(val frameCount: Int) : AppState()
    data object HandoffLaunching : AppState()
    data object WaitingForResults : AppState()
    data object HandoffFailed : AppState()
    data class Results(val bbsResults: List<BarcodeResult>) : AppState()
    data object Finished : AppState()
}
