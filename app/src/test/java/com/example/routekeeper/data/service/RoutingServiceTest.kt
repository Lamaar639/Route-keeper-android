package com.example.routekeeper.data.service

import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.DeliveryStop
import com.example.routekeeper.data.model.DeliveryTimeWindow
import com.example.routekeeper.data.model.GeocodeStatus
import com.example.routekeeper.data.model.GpsBreadcrumb
import com.example.routekeeper.data.model.GpsTrackSegment
import com.example.routekeeper.data.model.RoutingMode
import com.example.routekeeper.data.model.TrafficLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingServiceTest {

    private fun makeStop(
        id: String,
        lat: Double?,
        lon: Double?,
        slotStart: String? = null,
        slotEnd: String? = null,
        delivered: Boolean = false
    ) = DeliveryStop(
        id = id,
        name = "Customer $id",
        phone = "",
        address = "$id High Street",
        latitude = lat,
        longitude = lon,
        geocodeStatus = if (lat != null) GeocodeStatus.LOCATED else GeocodeStatus.PENDING,
        delivered = delivered,
        deliveredAt = if (delivered) "10:15" else null,
        timeWindow = if (slotStart != null && slotEnd != null) {
            DeliveryTimeWindow(slotStart, slotEnd, 1, "$slotStart – $slotEnd")
        } else null
    )

    @Test
    fun `calculateSchedule waits until booked slot opens and never schedules pre-slot arrival`() {
        val stops = listOf(
            makeStop("s1", 51.5033, -0.1276, "09:00", "10:00"),
            makeStop("s2", 51.5237, -0.1585, "11:00", "12:00"),
            makeStop("s3", 51.5217, -0.1257, null, null)
        )

        val scheduled = RoutingService.calculateSchedule(
            stops = stops,
            departureStartTime = "08:00",
            defaultDwellMinutes = 6.0
        )

        assertEquals("09:00", scheduled[0].estimatedArrival)
        assertEquals("on-time", scheduled[0].windowStatus)
        assertEquals("11:00", scheduled[1].estimatedArrival)
        assertEquals("on-time", scheduled[1].windowStatus)
        assertEquals("flexible", scheduled[2].windowStatus)
    }

    @Test
    fun `optimizeTour orders earlier booked slots before later booked slots`() {
        val stops = listOf(
            makeStop("late_slot", 51.5033, -0.1276, "14:00", "15:00"),
            makeStop("early_slot", 51.5055, -0.0908, "09:00", "10:00"),
            makeStop("mid_slot", 51.5217, -0.1257, "11:00", "12:00")
        )

        val optimized = RoutingService.optimizeTour(stops, departureStartTime = "09:00")
        assertEquals(listOf("early_slot", "mid_slot", "late_slot"), optimized.map { it.id })
    }

    @Test
    fun `reorderStopsForMode preserves completed deliveries across fastest shortest and reverse`() {
        val s1 = makeStop("1", 51.5033, -0.1276, delivered = true)
        val s2 = makeStop("2", 51.5237, -0.1585, delivered = false)
        val s3 = makeStop("3", 51.5055, -0.0908, delivered = true)
        val baseIds = listOf("1", "2", "3")

        val reversed = RoutingService.reorderStopsForMode(listOf(s1, s2, s3), baseIds, "reverse")
        assertEquals(listOf("3", "2", "1"), reversed.map { it.id })
        assertTrue(reversed.first { it.id == "1" }.delivered)
        assertFalse(reversed.first { it.id == "2" }.delivered)
        assertTrue(reversed.first { it.id == "3" }.delivered)

        val shortest = RoutingService.reorderStopsForMode(reversed, baseIds, "shortest")
        assertTrue(shortest.first { it.id == "1" }.delivered)
        assertFalse(shortest.first { it.id == "2" }.delivered)
        assertTrue(shortest.first { it.id == "3" }.delivered)
    }

    @Test
    fun `combineTrackSegments sorts segments and deduplicates overlapping breadcrumbs`() {
        val c1 = GpsBreadcrumb("a", 1000L, "09:00:01", 51.5, -0.1, 5f, 11f, 18f)
        val c2 = GpsBreadcrumb("b", 2000L, "09:00:02", 51.501, -0.101, 6f, 13f, 21f)
        val c2Dup = GpsBreadcrumb("b", 2000L, "09:00:02", 51.501, -0.101, 6f, 13f, 21f)
        val c3 = GpsBreadcrumb("c", 5000L, "09:00:05", 51.502, -0.102, 7f, 15f, 25f)

        val seg2 = GpsTrackSegment("seg2", "s2", "Stop 2", 2, "09:00:04", "09:00:05", listOf(c2Dup, c3))
        val seg1 = GpsTrackSegment("seg1", "s1", "Stop 1", 1, "09:00:01", "09:00:02", listOf(c1, c2))

        val combined = RoutingService.combineTrackSegments(listOf(seg2, seg1))
        assertEquals(listOf("a", "b", "c"), combined.map { it.id })
    }

    @Test
    fun `classifyTrafficLevel maps speed ratios to Google Maps congestion tiers`() {
        assertEquals(TrafficLevel.SEVERE, RoutingService.classifyTrafficLevel(0.25f))
        assertEquals(TrafficLevel.BUSY, RoutingService.classifyTrafficLevel(0.48f))
        assertEquals(TrafficLevel.MODERATE, RoutingService.classifyTrafficLevel(0.70f))
        assertEquals(TrafficLevel.CLEAR, RoutingService.classifyTrafficLevel(0.92f))
    }

    @Test
    fun `parseOsrmRouteResponse builds road-following path, maneuvers, and traffic segments with actual GPS blending`() {
        val osrmJson = """
            {
              "code": "Ok",
              "routes": [
                {
                  "distance": 1450.0,
                  "duration": 260.0,
                  "legs": [
                    {
                      "steps": [
                        {
                          "distance": 450.0,
                          "duration": 60.0,
                          "name": "Whitehall",
                          "maneuver": { "type": "depart", "modifier": "north" },
                          "intersections": [ {}, {}, {} ],
                          "geometry": {
                            "type": "LineString",
                            "coordinates": [
                              [-0.1276, 51.5033],
                              [-0.1272, 51.5048],
                              [-0.1268, 51.5062]
                            ]
                          }
                        },
                        {
                          "distance": 1000.0,
                          "duration": 200.0,
                          "name": "Oxford Street",
                          "maneuver": { "type": "turn", "modifier": "left" },
                          "intersections": [ {}, {}, {}, {}, {} ],
                          "geometry": {
                            "type": "LineString",
                            "coordinates": [
                              [-0.1268, 51.5062],
                              [-0.1320, 51.5140],
                              [-0.1410, 51.5152]
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        // Actual slow GPS sample along the midpoint of Oxford Street step (lat ~51.5101, lon ~-0.1294)
        val slowActualGps = listOf(
            GpsBreadcrumb(
                id = "gps1",
                timestampMs = 10000L,
                timeLabel = "08:35:00",
                latitude = 51.5101,
                longitude = -0.1294,
                speedMps = 2.0f,
                speedMph = 4.5f,
                speedKmh = 7.2f,
                speedLimitMph = 30f
            )
        )

        val result = RoutingService.parseOsrmRouteResponse(
            jsonBody = osrmJson,
            baseClockMinutes = 8 * 60 + 30,
            historicalBreadcrumbs = slowActualGps
        )

        assertNotNull(result)
        result!!
        assertEquals(RoutingMode.ROAD_NETWORK, result.routingMode)
        assertEquals(1450.0, result.distanceMeters, 0.1)
        assertEquals(260.0, result.durationSeconds, 0.1)
        // Road-following geometry should preserve intermediate street vertices (5 unique points vs 2 stop endpoints)
        assertEquals(5, result.path.size)
        assertEquals(2, result.steps.size)
        assertTrue(result.steps[0].instruction.contains("Whitehall"))
        assertTrue(result.steps[1].instruction.contains("Turn left onto Oxford Street"))
        assertEquals(4, result.trafficSegments.size)

        // Segment matching historical slow GPS sample must reflect Actual GPS + Severe congestion
        val oxfordSlowSeg = result.trafficSegments[2]
        assertEquals("Actual GPS + Historic", oxfordSlowSeg.source)
        assertEquals(TrafficLevel.SEVERE, oxfordSlowSeg.trafficLevel)
        assertEquals(Coordinate(51.5033, -0.1276), result.path.first())
    }
}
