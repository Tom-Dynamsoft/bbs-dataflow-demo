package com.dynamsoft.bbsdatareceiver.scanner

import com.dynamsoft.bbsdatareceiver.model.BarcodeResult

/**
 * Pure functions for merging and deduplicating barcode results from multiple sources.
 */
object ResultMerger {

    /**
     * Merge DBR and BBS results. Dedup by format+text within same source.
     * Results from different sources with the same barcode are kept separate (tagged differently).
     */
    fun merge(
        dbrResults: List<BarcodeResult>,
        bbsResults: List<BarcodeResult>
    ): List<BarcodeResult> {
        val merged = mutableListOf<BarcodeResult>()

        // Add all DBR results (already deduped by the scanning session)
        merged.addAll(dbrResults)

        // Add BBS results, deduping within BBS set
        val bbsDeduped = dedup(bbsResults)
        merged.addAll(bbsDeduped)

        return merged
    }

    /** Deduplicate results by format+text, merging counts. */
    fun dedup(results: List<BarcodeResult>): List<BarcodeResult> {
        val map = linkedMapOf<String, BarcodeResult>()
        for (result in results) {
            val existing = map[result.dedupKey]
            if (existing != null) {
                map[result.dedupKey] = existing.copy(
                    count = existing.count + result.count
                )
            } else {
                map[result.dedupKey] = result
            }
        }
        return map.values.toList()
    }

    /** Export results to CSV string. */
    fun toCsv(results: List<BarcodeResult>): String {
        val sb = StringBuilder()
        sb.appendLine("INDEX,BARCODE_TEXT,BARCODE_FORMAT,SOURCE,COUNT,STATUS")
        results.forEachIndexed { index, r ->
            sb.appendLine("${index + 1},\"${escapeCsv(r.text)}\",\"${r.format}\",\"${r.source}\",${r.count},\"${r.status ?: ""}\"")
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String =
        value.replace("\"", "\"\"")
}
