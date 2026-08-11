package com.dynamsoft.bbsdatareceiver

import com.dynamsoft.bbsdatareceiver.model.BarcodeResult
import com.dynamsoft.bbsdatareceiver.scanner.ResultMerger
import org.junit.Assert.*
import org.junit.Test

class ResultMergerTest {

    @Test
    fun `merge keeps both sources separate`() {
        val dbr = listOf(
            BarcodeResult("ABC", "QR_CODE", BarcodeResult.Source.DBR),
            BarcodeResult("DEF", "CODE_128", BarcodeResult.Source.DBR)
        )
        val bbs = listOf(
            BarcodeResult("ABC", "QR_CODE", BarcodeResult.Source.BBS),
            BarcodeResult("GHI", "CODE_39", BarcodeResult.Source.BBS)
        )

        val merged = ResultMerger.merge(dbr, bbs)

        assertEquals(4, merged.size)
        assertEquals(2, merged.count { it.source == BarcodeResult.Source.DBR })
        assertEquals(2, merged.count { it.source == BarcodeResult.Source.BBS })
    }

    @Test
    fun `dedup merges counts for same key`() {
        val results = listOf(
            BarcodeResult("ABC", "QR_CODE", BarcodeResult.Source.BBS, count = 1),
            BarcodeResult("ABC", "QR_CODE", BarcodeResult.Source.BBS, count = 1),
            BarcodeResult("DEF", "CODE_128", BarcodeResult.Source.BBS, count = 1)
        )

        val deduped = ResultMerger.dedup(results)

        assertEquals(2, deduped.size)
        val abc = deduped.first { it.text == "ABC" }
        assertEquals(2, abc.count)
    }

    @Test
    fun `dedup preserves order of first occurrence`() {
        val results = listOf(
            BarcodeResult("BBB", "QR_CODE", BarcodeResult.Source.DBR),
            BarcodeResult("AAA", "QR_CODE", BarcodeResult.Source.DBR),
            BarcodeResult("BBB", "QR_CODE", BarcodeResult.Source.DBR)
        )

        val deduped = ResultMerger.dedup(results)

        assertEquals("BBB", deduped[0].text)
        assertEquals("AAA", deduped[1].text)
    }

    @Test
    fun `toCsv produces valid output`() {
        val results = listOf(
            BarcodeResult("Hello", "QR_CODE", BarcodeResult.Source.DBR, count = 3),
            BarcodeResult("World", "CODE_128", BarcodeResult.Source.BBS, count = 1, status = "Recognized")
        )

        val csv = ResultMerger.toCsv(results)
        val lines = csv.trim().lines()

        assertEquals(3, lines.size) // header + 2 rows
        assertTrue(lines[0].contains("INDEX"))
        assertTrue(lines[1].contains("Hello"))
        assertTrue(lines[1].contains("DBR"))
        assertTrue(lines[2].contains("BBS"))
        assertTrue(lines[2].contains("Recognized"))
    }

    @Test
    fun `toCsv escapes quotes in text`() {
        val results = listOf(
            BarcodeResult("He said \"hi\"", "QR_CODE", BarcodeResult.Source.DBR)
        )

        val csv = ResultMerger.toCsv(results)
        assertTrue(csv.contains("He said \"\"hi\"\""))
    }

    @Test
    fun `merge with empty lists`() {
        val merged = ResultMerger.merge(emptyList(), emptyList())
        assertTrue(merged.isEmpty())
    }

    @Test
    fun `merge with only DBR results`() {
        val dbr = listOf(
            BarcodeResult("ABC", "QR_CODE", BarcodeResult.Source.DBR)
        )
        val merged = ResultMerger.merge(dbr, emptyList())
        assertEquals(1, merged.size)
        assertEquals(BarcodeResult.Source.DBR, merged[0].source)
    }
}
