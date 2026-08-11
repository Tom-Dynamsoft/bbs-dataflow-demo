package com.dynamsoft.bbsdatareceiver

import com.dynamsoft.bbsdatareceiver.bbs.BbsResultParser
import org.junit.Assert.*
import org.junit.Test

class CsvParsingTest {

    @Test
    fun `parseCsvLine handles simple fields`() {
        val result = BbsResultParser.parseCsvLine("a,b,c")
        assertArrayEquals(arrayOf("a", "b", "c"), result)
    }

    @Test
    fun `parseCsvLine handles quoted fields with commas`() {
        val result = BbsResultParser.parseCsvLine("\"hello, world\",b,c")
        assertArrayEquals(arrayOf("hello, world", "b", "c"), result)
    }

    @Test
    fun `parseCsvLine handles empty fields`() {
        val result = BbsResultParser.parseCsvLine("a,,c")
        assertArrayEquals(arrayOf("a", "", "c"), result)
    }

    @Test
    fun `parseCsvLine handles quoted empty fields`() {
        val result = BbsResultParser.parseCsvLine("\"a\",\"\",\"c\"")
        assertArrayEquals(arrayOf("a", "", "c"), result)
    }

    @Test
    fun `parseCsvLine handles BBS format with location brackets`() {
        val result = BbsResultParser.parseCsvLine("\"1\",\"DM-QRBatch187\",\"QR_CODE\",\"Recognized\",\"[(86,526),(78,483)]\"")
        assertEquals(5, result.size)
        assertEquals("1", result[0])
        assertEquals("DM-QRBatch187", result[1])
        assertEquals("QR_CODE", result[2])
        assertEquals("Recognized", result[3])
        assertEquals("[(86,526),(78,483)]", result[4])
    }

    @Test
    fun `parseCsvLine handles single field`() {
        val result = BbsResultParser.parseCsvLine("only")
        assertArrayEquals(arrayOf("only"), result)
    }
}
