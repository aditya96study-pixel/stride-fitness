package com.aditya.stride.export

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun plainRowsSplitOnCommas() {
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("1", "2", "3")),
            Csv.parse("a,b,c\n1,2,3"),
        )
    }

    @Test
    fun aQuotedFieldMayContainCommasQuotesAndNewlines() {
        val text = "type,note\nweight,\"first, second\"\nweight,\"say \"\"hi\"\"\"\n" +
            "weight,\"line one\nline two\""
        val rows = Csv.parse(text)
        assertEquals(4, rows.size)
        assertEquals("first, second", rows[1][1])
        assertEquals("say \"hi\"", rows[2][1])
        assertEquals("line one\nline two", rows[3][1])
    }

    @Test
    fun crlfAndLoneCrBothEndARow() {
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c")), Csv.parse("a\r\nb\rc"))
    }

    /** Excel adds a byte-order mark on re-save; it must not land inside the first cell. */
    @Test
    fun aByteOrderMarkIsStripped() {
        assertEquals("type", Csv.parse("﻿type,date\nweight,2026-01-01")[0][0])
    }

    @Test
    fun emptyFieldsAreKeptSoColumnsStayAligned() {
        assertEquals(listOf(listOf("a", "", "", "d")), Csv.parse("a,,,d"))
    }

    @Test
    fun aTrailingNewlineDoesNotProduceAnExtraRow() {
        assertEquals(2, Csv.parse("a,b\nc,d\n").size)
        assertEquals(2, Csv.parse("a,b\r\nc,d\r\n").size)
    }

    @Test
    fun emptyInputIsNoRowsRatherThanOneEmptyRow() {
        assertEquals(emptyList<List<String>>(), Csv.parse(""))
        assertEquals(emptyList<List<String>>(), Csv.parse("\n"))
    }

    @Test
    fun quotingAndParsingRoundTripEveryAwkwardValue() {
        val values = listOf("plain", "with, comma", "with \"quote\"", "with\nnewline", "")
        val text = Csv.row(values)
        assertEquals(values, Csv.parse(text).single())
    }

    @Test
    fun onlyFieldsThatNeedItAreQuoted() {
        assertEquals("plain", Csv.quote("plain"))
        assertEquals("\"a,b\"", Csv.quote("a,b"))
        assertEquals("\"a\"\"b\"", Csv.quote("a\"b"))
    }
}
