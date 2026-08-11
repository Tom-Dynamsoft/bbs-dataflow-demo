package com.dynamsoft.bbsdatareceiver.model

data class BarcodeResult(
    val text: String,
    val format: String,
    val source: Source,
    val firstSeenAt: Long = System.currentTimeMillis(),
    var count: Int = 1,
    val confidence: Int? = null,
    val status: String? = null
) {
    enum class Source { DBR, BBS }

    /** Dedup key: format + text */
    val dedupKey: String get() = "$format|$text"
}
