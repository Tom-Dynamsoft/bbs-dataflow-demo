package com.dynamsoft.bbsdatareceiver.bbs

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.dynamsoft.bbsdatareceiver.model.BarcodeResult
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Parses BBS result intents into BarcodeResult lists.
 * Ported from the original Java ShareReceiver.
 *
 * BBS returns files via ClipData containing FileProvider URIs:
 * - *_AnnotatedImage.jpg
 * - *_OriginalImage.jpg
 * - *.csv (barcode data)
 */
object BbsResultParser {

    data class ParsedResult(
        val barcodes: List<BarcodeResult>,
        val annotatedImageUri: Uri?,
        val originalImageUri: Uri?,
        val csvUri: Uri?
    )

    private val IMAGE_EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif", ".heic", ".heif")

    fun parse(context: Context, intent: Intent?): ParsedResult? {
        if (intent == null) return null
        val uris = extractUris(intent)
        if (uris.isEmpty()) return null
        return classifyAndParse(context, uris)
    }

    private fun extractUris(intent: Intent): List<Uri> {
        // 1) EXTRA_STREAM as ArrayList (ACTION_SEND_MULTIPLE)
        @Suppress("DEPRECATION")
        intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        // 2) EXTRA_STREAM as single Uri
        @Suppress("DEPRECATION")
        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            ?.let { return listOf(it) }

        // 3) ClipData
        intent.clipData?.let { clip ->
            val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it)?.uri }
            if (uris.isNotEmpty()) return uris
        }

        // 4) Intent data
        intent.data?.let { return listOf(it) }

        return emptyList()
    }

    private fun classifyAndParse(context: Context, uris: List<Uri>): ParsedResult {
        var annotatedUri: Uri? = null
        var originalUri: Uri? = null
        var csvUri: Uri? = null

        for (uri in uris) {
            val name = resolveFileName(context, uri) ?: uri.toString()
            when {
                isAnnotatedImage(name) -> annotatedUri = uri
                isOriginalImage(name) -> originalUri = uri
                isCsvFile(name) -> csvUri = uri
            }
        }

        val barcodes = if (csvUri != null) parseCsvToBarcodes(context, csvUri) else emptyList()
        return ParsedResult(barcodes, annotatedUri, originalUri, csvUri)
    }

    private fun resolveFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (col >= 0) return cursor.getString(col)
                    }
                }
            } catch (_: Exception) {}
        }
        return uri.lastPathSegment
    }

    private fun hasImageExtension(name: String): Boolean =
        IMAGE_EXTENSIONS.any { name.lowercase().endsWith(it) }

    private fun isAnnotatedImage(name: String): Boolean =
        hasImageExtension(name) && name.contains("AnnotatedImage")

    private fun isOriginalImage(name: String): Boolean =
        hasImageExtension(name) && name.contains("OriginalImage")

    private fun isCsvFile(name: String): Boolean =
        name.lowercase().endsWith(".csv")

    /**
     * Parse BBS CSV into BarcodeResult list.
     * CSV columns: INDEX, BARCODE_TEXT, BARCODE_FORMAT, STATUS, LOCATION
     */
    private fun parseCsvToBarcodes(context: Context, csvUri: Uri): List<BarcodeResult> {
        val rows = parseCsv(context, csvUri)
        if (rows.size < 2) return emptyList() // need header + at least one data row

        val header = rows[0].map { it.trim().uppercase() }
        val textCol = header.indexOfFirst { it.contains("BARCODE_TEXT") || it == "TEXT" }
        val formatCol = header.indexOfFirst { it.contains("BARCODE_FORMAT") || it == "FORMAT" }
        val statusCol = header.indexOfFirst { it.contains("STATUS") }

        if (textCol < 0 && formatCol < 0) return emptyList()

        return rows.drop(1).mapNotNull { row ->
            val text = row.getOrNull(textCol)?.trim() ?: ""
            val format = row.getOrNull(formatCol)?.trim() ?: "UNKNOWN"
            val status = row.getOrNull(statusCol)?.trim()
            if (text.isEmpty()) return@mapNotNull null
            BarcodeResult(
                text = text,
                format = format,
                source = BarcodeResult.Source.BBS,
                status = status
            )
        }
    }

    private fun parseCsv(context: Context, csvUri: Uri): List<Array<String>> {
        val rows = mutableListOf<Array<String>>()
        try {
            context.contentResolver.openInputStream(csvUri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.isNotBlank()) {
                            rows.add(parseCsvLine(line))
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {}
        return rows
    }

    /** Parse a CSV line handling double-quoted fields with embedded commas. */
    internal fun parseCsvLine(line: String): Array<String> {
        val fields = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
        }
        fields.add(sb.toString())
        return fields.toTypedArray()
    }
}
