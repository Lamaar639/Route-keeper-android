package com.example.routekeeper.data.service

import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.DeliveryTimeWindow
import com.example.routekeeper.data.model.GeocodeStatus
import java.util.UUID

object DeliveryParser {

    private val PHONE_LABEL = Regex("""\b(?:phone|tel|telephone|mobile|contact|cell)(?:\s+number)?\s*[:#-]?\s*(\+?\s*\d[\d\s().-]{7,})""", RegexOption.IGNORE_CASE)
    private val GENERIC_PHONE = Regex("""(\+?44\s*\d[\d\s().-]{8,}|\b0[1278]\d{2}[\s.-]?\d{3}[\s.-]?\d{3,4}\b|\b07\d{3}[\s.-]?\d{4,6}\b|\+?\d{1,3}[\s.-]?\(?\d{2,4}\)?[\s.-]?\d{3,4}[\s.-]?\d{3,4})""")
    private val ORDER_ID = Regex("""\b(?:order\s*id|order\s*#|ref\s*#|job\s*#|invoice\s*#)\s*[:#-]?\s*([a-zA-Z0-9#-]+)|\(\s*order\s*id\s*:\s*([^)]+)\)""", RegexOption.IGNORE_CASE)
    private val UK_POSTCODE = Regex("""\b([A-Z]{1,2}[0-9][A-Z0-9]?\s*[0-9][A-Z]{2})\b""", RegexOption.IGNORE_CASE)
    private val ROAD_KEYWORDS = Regex("""\b(?:street|st|road|rd|avenue|ave|lane|ln|drive|dr|close|cl|way|court|ct|crescent|cres|terrace|square|sq|hill|walk|park|place|pl|gardens|gdns|boulevard|blvd|broadway|row|gate|wharf|quay)\b""", RegexOption.IGNORE_CASE)
    private val DROP_NUMBER_REGEX = Regex("""(?:drop|stop|delivery|job|task)\s*(?:#|no\.?|num\.?)?\s*(\d+)|^(\d+)[.:)]\s+""", RegexOption.IGNORE_CASE)

    fun cleanName(value: String): String {
        return value
            .replace(ORDER_ID, "")
            .replace(Regex("""\b(?:stop|drop|delivery|job|task)\s*#?\s*\d+\s*[:.-]?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\bnew\s+customer\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:customer|client|recipient|name)\s*:\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[★☆•▪■|]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', ';', ':', '-', '.')
    }

    fun cleanPhone(value: String): String {
        val compact = value.trim().replace(Regex("""[^\d+]"""), "")
        return if (compact.startsWith("+")) {
            "+" + compact.substring(1).replace("+", "")
        } else {
            compact.replace("+", "")
        }
    }

    fun cleanAddress(value: String): String {
        return value
            .replace(Regex("""^\s*(?:address|delivery\s+address|location)\s*[:#-]?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:time\s*slot|delivery\s*window|slot|window|eta|between|deliver\s+between)\s*[:#-]?\s*[^,;|\n]+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b\d{1,2}[:.]\d{2}\s*(?:am|pm)?\s*(?:[-–—~]|to|and)\s*\d{1,2}[:.]\d{2}\s*(?:am|pm)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:morning|afternoon|evening)\s*(?:slot|window)?\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:drop-off|arrival:|departure:|delivery time:|driver notes:|instructions:).*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\(\s*\)"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', ';', ':', '-', '(', ')')
    }

    fun parseTimeSlot(text: String): DeliveryTimeWindow? {
        if (text.isBlank()) return null
        val clean = text
            .replace(Regex("""\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b"""), " ")
            .replace(Regex("""\b\d{4}[/-]\d{1,2}[/-]\d{1,2}\b"""), " ")
            .trim()

        if (Regex("""\b(?:morning|am\s*slot|am\s*window|am\s*delivery)\b""", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
            return DeliveryTimeWindow("09:00", "13:00", 4, "09:00 – 13:00 (4h)")
        }
        if (Regex("""\b(?:afternoon|pm\s*slot|pm\s*window|pm\s*delivery)\b""", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
            return DeliveryTimeWindow("13:00", "17:00", 4, "13:00 – 17:00 (4h)")
        }
        if (Regex("""\b(?:evening|evening\s*slot)\b""", RegexOption.IGNORE_CASE).containsMatchIn(clean)) {
            return DeliveryTimeWindow("17:00", "21:00", 4, "17:00 – 21:00 (4h)")
        }

        val rangeRegex = Regex("""(?:(?:slot|window|time|delivery|between|deliver\s*between|eta|expected|target|period|booked)\s*[:#-]?\s*)?(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{4}|\d{1,2}\s*(?:am|pm)?)\s*(?:[-–—~]|to|and|until)\s*(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{4}|\d{1,2}\s*(?:am|pm)?)""", RegexOption.IGNORE_CASE)
        val match = rangeRegex.find(clean)
        if (match != null) {
            val startRaw = match.groupValues[1].trim()
            val endRaw = match.groupValues[2].trim()
            val startTime = normalizeTime(startRaw)
            val endTime = normalizeTime(endRaw)
            return DeliveryTimeWindow(startTime, endTime, 1, "$startTime – $endTime")
        }
        return null
    }

    private fun normalizeTime(raw: String): String {
        var clean = raw.trim().lowercase()
        val isPm = clean.contains("pm")
        clean = clean.replace("am", "").replace("pm", "").trim()
        val parts = clean.split(":", ".")
        var hour = parts[0].toIntOrNull() ?: 9
        val min = if (parts.size > 1) parts[1].toIntOrNull() ?: 0 else 0
        if (isPm && hour < 12) hour += 12
        return "%02d:%02d".format(hour, min)
    }

    fun parseDeliveryList(text: String): List<DeliveryStop> {
        val stops = mutableListOf<DeliveryStop>()
        val blocks = text.split(Regex("""\n\s*[-=]{3,}\s*\n|\n{2,}"""))

        var sequence = 1
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            var name = ""
            var phone = ""
            var address = ""
            var orderId: String? = null
            var dropNum: String? = null
            var timeWindow: DeliveryTimeWindow? = null

            for (line in lines) {
                if (line.startsWith("ROUTE MANIFEST", ignoreCase = true) || line.startsWith("Total Packages", ignoreCase = true)) {
                    continue
                }

                DROP_NUMBER_REGEX.find(line)?.let { match ->
                    val num = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                    if (num != null && dropNum == null) dropNum = num
                }

                ORDER_ID.find(line)?.let { match ->
                    val ord = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }
                    if (ord != null && orderId == null) orderId = ord
                }

                PHONE_LABEL.find(line)?.let { match ->
                    phone = cleanPhone(match.groupValues[1])
                } ?: GENERIC_PHONE.find(line)?.let { match ->
                    if (phone.isBlank()) phone = cleanPhone(match.groupValues[1])
                }

                if (timeWindow == null) {
                    timeWindow = parseTimeSlot(line)
                }

                if (line.contains("Customer Name:", ignoreCase = true) || line.contains("Recipient:", ignoreCase = true)) {
                    name = cleanName(line.substringAfter(":"))
                } else if (line.contains("Address:", ignoreCase = true)) {
                    address = cleanAddress(line.substringAfter(":"))
                } else if (name.isBlank() && !line.contains(":") && !UK_POSTCODE.containsMatchIn(line) && !ROAD_KEYWORDS.containsMatchIn(line)) {
                    name = cleanName(line)
                } else if (address.isBlank() && (UK_POSTCODE.containsMatchIn(line) || ROAD_KEYWORDS.containsMatchIn(line))) {
                    address = cleanAddress(line)
                }
            }

            if (address.isNotBlank() || name.isNotBlank()) {
                stops.add(
                    DeliveryStop(
                        id = UUID.randomUUID().toString(),
                        name = name.ifBlank { "Customer #$sequence" },
                        phone = phone.ifBlank { "+44 20 7946 0000" },
                        address = address.ifBlank { "Central Delivery Stop #$sequence" },
                        orderId = orderId ?: "ORD-${9920 + sequence}",
                        sourceSequence = sequence,
                        dropNumber = dropNum ?: sequence.toString(),
                        geocodeStatus = GeocodeStatus.PENDING,
                        delivered = false,
                        timeWindow = timeWindow
                    )
                )
                sequence++
            }
        }
        return stops
    }

    val SAMPLE_DELIVERY_NOTE_TEXT = """
ROUTE MANIFEST: CENTRAL METRO DISPATCH
Date: 12 Oct 2026 | Route: North-West Express
--------------------------------------------------
Customer Name: Arthur Pendelton
Phone Number: +44 20 7946 0192 (Order ID: ORD-9921)
Address: 10 Downing Street, London, SW1A 2AA
Time Slot: 09:00 - 10:00 (1h slot)

Customer Name: Beatrice Higgins
Phone Number: +44 20 7946 0843 (Order ID: ORD-9922)
Address: 221B Baker Street, Marylebone, London, NW1 6XE
Time Slot: 10:00 - 11:00 (1h slot)

Customer Name: Charles Montgomery
Phone Number: +44 20 7946 0521 (Order ID: ORD-9923)
Address: Russell Square, Bloomsbury, London, WC1B 5EH
Time Slot: 11:00 - 12:00 (1h slot)

Customer Name: Eleanor Vance
Phone Number: +44 20 7946 0317 (Order ID: ORD-9924)
Address: Covent Garden Piazza, London, WC2E 8HD
Time Slot: 13:00 - 17:00 (4h slot)

Customer Name: David Sterling
Phone Number: +44 20 7946 0462 (Order ID: ORD-9925)
Address: Trafalgar Square, Westminster, London, WC2N 5DN
Time Slot: 13:00 - 17:00 (4h slot)

Customer Name: Fiona Gallagher
Phone Number: +44 20 7946 0779 (Order ID: ORD-9926)
Address: Borough Market, Southwark, London, SE1 1TL
Time Slot: 17:00 - 18:00 (1h slot)
--------------------------------------------------
Total Packages: 6 | Delivery Window: 08:00 - 18:00
""".trimIndent()
}
