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

        val rangeRegex = Regex(
            """((?:slot|window|time|delivery|between|deliver\s*between|eta|expected|target|period|booked)\s*[:#-]?\s*)?(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{4}|\d{1,2}\s*(?:am|pm)?)\s*(?:[-–—~]|to|and|until)\s*(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{4}|\d{1,2}\s*(?:am|pm)?)""",
            RegexOption.IGNORE_CASE
        )
        val match = rangeRegex.find(clean)
        if (match != null) {
            val hasKeyword = match.groupValues[1].isNotBlank()
            val startRaw = match.groupValues[2].trim()
            val endRaw = match.groupValues[3].trim()
            val hasTimeMarker = hasKeyword ||
                listOf(startRaw, endRaw).any {
                    it.contains(":") || it.contains(".") ||
                        it.contains("am", ignoreCase = true) ||
                        it.contains("pm", ignoreCase = true)
                }
            if (hasTimeMarker) {
                val startTime = normalizeClockOrNull(startRaw)
                val endTime = normalizeClockOrNull(endRaw)
                if (startTime != null && endTime != null) {
                    return DeliveryTimeWindow(startTime, endTime, 1, "$startTime – $endTime")
                }
            }
        }
        return null
    }

    private val EXPLICIT_DEPARTURE_TIME = Regex(
        """(?:depot\s+departure|departure\s+time|departure|route\s+start(?:\s+time)?|start\s+time|shift\s+start|dispatch\s+time|leave\s+time|leave\s+depot|etd)\s*[:#=-]?\s*(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{4}|\d{1,2}\s*(?:am|pm))""",
        RegexOption.IGNORE_CASE
    )
    private val ROUTE_SUMMARY_WINDOW = Regex(
        """^(?:total\s+packages|route\s+summary|shift|manifest|dispatch).*?(?:delivery\s*window|route\s*window|shift\s*window)\s*[:#-]?\s*(\d{1,2}[:.]\d{2}\s*(?:am|pm)?|\d{4}|\d{1,2}\s*(?:am|pm))""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Auto-extracts a route-level departure/start time (24h "HH:mm") if present in the manifest/PDF.
     * Returns null if no departure time is found so the UI prompts the driver to choose an exact time.
     */
    fun extractDepartureTime(text: String): String? {
        if (text.isBlank()) return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // 1. Prioritize explicit departure / start / dispatch time labels
        for (line in lines) {
            EXPLICIT_DEPARTURE_TIME.find(line)?.let { match ->
                val raw = match.groupValues[1].trim()
                normalizeClockOrNull(raw)?.let { return it }
            }
        }

        // 2. Fallback to route-level summary window line (e.g. "Total Packages: 6 | Delivery Window: 08:00 - 18:00")
        for (line in lines) {
            ROUTE_SUMMARY_WINDOW.find(line)?.let { match ->
                val raw = match.groupValues[1].trim()
                normalizeClockOrNull(raw)?.let { return it }
            }
        }

        return null
    }

    private fun normalizeClockOrNull(raw: String): String? {
        var clean = raw.trim().lowercase()
        val isPm = clean.contains("pm")
        val isAm = clean.contains("am")
        clean = clean.replace("am", "").replace("pm", "").trim()

        var hour: Int
        val min: Int
        if (!clean.contains(":") && !clean.contains(".") && clean.length == 4 && clean.all { it.isDigit() }) {
            hour = clean.substring(0, 2).toIntOrNull() ?: return null
            min = clean.substring(2, 4).toIntOrNull() ?: return null
        } else {
            val parts = clean.split(":", ".")
            hour = parts[0].toIntOrNull() ?: return null
            min = if (parts.size > 1) parts[1].toIntOrNull() ?: return null else 0
        }

        if (min !in 0..59) return null
        if (isPm && hour in 1..11) hour += 12
        if (isAm && hour == 12) hour = 0
        if (hour !in 0..23) return null
        return "%02d:%02d".format(hour, min)
    }

    private val NEW_STOP_LINE = Regex(
        """^\s*(?:(?:customer\s*name|recipient|client|deliver\s*to|name)\s*:|(?:stop|drop|delivery|job)\s*#?\s*\d+(?:\s*[:.\-–]|\s+[a-zA-Z]))""",
        RegexOption.IGNORE_CASE
    )
    private val HEADER_FOOTER_LINE = Regex(
        """^(?:route\s*manifest|total\s*packages|page\s+\d+(?:\s+of\s+\d+)?|printed\s+on|generated\s+on|date\s*:)\b.*""",
        RegexOption.IGNORE_CASE
    )

    fun parseDeliveryList(text: String): List<DeliveryStop> {
        val stops = mutableListOf<DeliveryStop>()
        val rawBlocks = text.split(Regex("""\n\s*[-=]{3,}\s*\n|\n{2,}"""))

        var sequence = 1

        fun commitStop(
            name: String,
            phone: String,
            address: String,
            orderId: String?,
            dropNum: String?,
            timeWindow: DeliveryTimeWindow?
        ) {
            if (address.isNotBlank() || name.isNotBlank()) {
                stops.add(
                    DeliveryStop(
                        id = UUID.randomUUID().toString(),
                        name = name.ifBlank { "Customer #$sequence" },
                        phone = phone,
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

        for (block in rawBlocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue

            var name = ""
            var phone = ""
            var address = ""
            var orderId: String? = null
            var dropNum: String? = null
            var timeWindow: DeliveryTimeWindow? = null

            for (line in lines) {
                if (HEADER_FOOTER_LINE.matches(line)) {
                    continue
                }

                // If PDF extraction collapsed blank lines between stops, split when a new record starts
                if (NEW_STOP_LINE.containsMatchIn(line) && (address.isNotBlank() || (name.isNotBlank() && (phone.isNotBlank() || orderId != null)))) {
                    commitStop(name, phone, address, orderId, dropNum, timeWindow)
                    name = ""
                    phone = ""
                    address = ""
                    orderId = null
                    dropNum = null
                    timeWindow = null
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

                if (
                    line.contains("Customer Name:", ignoreCase = true) ||
                    line.contains("Recipient:", ignoreCase = true) ||
                    line.contains("Client:", ignoreCase = true) ||
                    line.contains("Deliver To:", ignoreCase = true) ||
                    line.startsWith("Name:", ignoreCase = true)
                ) {
                    name = cleanName(line.substringAfter(":"))
                } else if (
                    line.contains("Delivery Address:", ignoreCase = true) ||
                    line.contains("Address:", ignoreCase = true)
                ) {
                    address = cleanAddress(line.substringAfter(":"))
                } else if (name.isBlank() && !line.contains(":") && !UK_POSTCODE.containsMatchIn(line) && !ROAD_KEYWORDS.containsMatchIn(line)) {
                    name = cleanName(line)
                } else if (address.isBlank() && (UK_POSTCODE.containsMatchIn(line) || ROAD_KEYWORDS.containsMatchIn(line))) {
                    address = cleanAddress(line)
                } else if (address.isNotBlank() && UK_POSTCODE.containsMatchIn(line) && !UK_POSTCODE.containsMatchIn(address)) {
                    val extra = cleanAddress(line)
                    if (extra.isNotBlank()) address = "$address, $extra"
                }
            }

            commitStop(name, phone, address, orderId, dropNum, timeWindow)
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
