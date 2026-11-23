package com.example.routekeeper.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryParserTest {

    @Test
    fun `sample delivery note parses all 6 stops unmarked as delivered`() {
        val stops = DeliveryParser.parseDeliveryList(DeliveryParser.SAMPLE_DELIVERY_NOTE_TEXT)
        assertEquals(6, stops.size)
        assertTrue(stops.none { it.delivered })
        assertEquals("Arthur Pendelton", stops[0].name)
        assertEquals("09:00", stops[0].timeWindow?.startTime)
        assertEquals("10:00", stops[0].timeWindow?.endTime)
    }

    @Test
    fun `house and flat number ranges are not misparsed as time windows`() {
        val text = """
            Customer Name: Alice Brown
            Address: Flat 1-2, 12 - 14 Oak Street, London, NW1 8AB
        """.trimIndent()

        val stops = DeliveryParser.parseDeliveryList(text)
        assertEquals(1, stops.size)
        assertNull("Street/flat range should not become a booked slot", stops[0].timeWindow)
        assertEquals("", stops[0].phone)
    }

    @Test
    fun `extracts explicit and military departure times and returns null when missing`() {
        assertEquals("07:45", DeliveryParser.extractDepartureTime("Depot Departure: 07:45\nStop 1: Bob"))
        assertEquals("08:30", DeliveryParser.extractDepartureTime("Route Start Time: 0830\nCustomer Name: Bob"))
        assertEquals("00:15", DeliveryParser.extractDepartureTime("Dispatch Time: 12:15 am"))
        assertNull(DeliveryParser.extractDepartureTime("Customer Name: Jane Doe\nAddress: 10 Downing Street\nTime Slot: 10:00 - 11:00"))
    }

    @Test
    fun `splits uncoloned stop headers and appends multiline UK postcode`() {
        val text = """
            Stop #1 - John Smith
            221B Baker Street
            Marylebone, London NW1 6XE
            Time Slot: 0900 - 1030
            Stop #2 - Jane Doe
            Russell Square
            London WC1B 5EH
            Between 1:00 pm and 2:30 pm
        """.trimIndent()

        val stops = DeliveryParser.parseDeliveryList(text)
        assertEquals(2, stops.size)
        assertEquals("John Smith", stops[0].name)
        assertTrue(stops[0].address.contains("NW1 6XE"))
        assertEquals("09:00", stops[0].timeWindow?.startTime)
        assertEquals("10:30", stops[0].timeWindow?.endTime)

        assertEquals("Jane Doe", stops[1].name)
        assertTrue(stops[1].address.contains("WC1B 5EH"))
        assertEquals("13:00", stops[1].timeWindow?.startTime)
        assertEquals("14:30", stops[1].timeWindow?.endTime)
    }
}
