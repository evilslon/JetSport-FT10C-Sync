package com.jetsport.ft10csync.protocol

import org.junit.Assert.*
import org.junit.Test

class Ft10cStepHistoryParserTest {
    @Test fun parsesCapturedRecordAndBigEndianCounters() {
        val packet = hex("B2 07 EA 0B 09 0B 05 4D 33 38 05 00 14 27 3A 13 05 39")
        val event = Ft10cStepHistoryParser.parse(packet)
        assertTrue(event is Ft10cHistoryEvent.Record)
        val record = (event as Ft10cHistoryEvent.Record).value
        assertEquals(2026, record.year)
        assertEquals(11, record.month)
        assertEquals(9, record.day)
        assertEquals(11, record.hour)
        assertEquals(1357, record.totalSteps)
        assertEquals(20, record.runSteps)
        assertEquals(1337, record.walkSteps)
        assertEquals(0, record.uncategorizedSteps)
    }

    @Test fun parsesEndMarkerAndPreservesTrailingByte() {
        val event = Ft10cStepHistoryParser.parse(hex("B2 FD 17"))
        assertEquals(Ft10cHistoryEvent.End(0x17), event)
    }

    @Test fun rejectsInvalidCalendarDateAndWrongLength() {
        val invalidDate = hex("B2 07 EA 02 1E 0B 00 01 00 00 00 00 00 00 00 00 00 00")
        assertTrue(Ft10cStepHistoryParser.parse(invalidDate) is Ft10cHistoryEvent.Invalid)
        assertTrue(Ft10cStepHistoryParser.parse(hex("B2 00")) is Ft10cHistoryEvent.Invalid)
        assertTrue(Ft10cStepHistoryParser.parse(hex("B2 00 00")) is Ft10cHistoryEvent.Invalid)
    }

    @Test fun validatesLeapDay() {
        val leapDay = hex("B2 07 E8 02 1D 00 00 01 00 00 00 00 00 00 00 00 00 00")
        assertTrue(Ft10cStepHistoryParser.parse(leapDay) is Ft10cHistoryEvent.Record)
    }

    @Test fun sessionDeduplicatesByDateAndHourAndCompletesOnlyOnEndMarker() {
        val first = hex("B2 07 EA 0B 09 0B 00 0A 00 00 00 00 00 00 00 00 00 0A")
        val replacement = hex("B2 07 EA 0B 09 0B 00 14 00 00 00 00 00 00 00 00 00 14")
        val session = Ft10cHistorySession()
        session.accept(first)
        session.accept(replacement)
        assertFalse(session.isComplete)
        assertEquals(1, session.snapshot().size)
        assertEquals(20, session.snapshot().single().totalSteps)
        session.accept(hex("B2 FD 17"))
        assertTrue(session.isComplete)
        assertTrue(session.accept(first) is Ft10cHistoryEvent.Invalid)
    }

    private fun hex(value: String) = value.split(" ").map { it.toInt(16).toByte() }.toByteArray()


    @Test
    fun rejectsActivityBreakdownGreaterThanTotal() {
        val packet = byteArrayOf(
            0xB2.toByte(), 0x07, 0xEA.toByte(), 0x0B, 0x0C, 0x0C,
            0x00, 0x64, // total = 100
            0x00, 0x00, 0x00, 0x00, 0x50, // run = 80
            0x00, 0x00, 0x00, 0x00, 0x32, // walk = 50; sum 130
        )

        val result = Ft10cStepHistoryParser.parse(packet)
        assertTrue(result is Ft10cHistoryEvent.Invalid)
        assertTrue((result as Ft10cHistoryEvent.Invalid).reason.contains("exceed total"))
    }
}
