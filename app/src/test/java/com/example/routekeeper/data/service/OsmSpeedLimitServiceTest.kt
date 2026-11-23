package com.example.routekeeper.data.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OsmSpeedLimitServiceTest {

    @Test
    fun `parseMaxSpeedMph handles mph kmh and UK national tags`() {
        assertEquals(30f, OsmSpeedLimitService.parseMaxSpeedMph("30 mph"))
        assertEquals(31f, OsmSpeedLimitService.parseMaxSpeedMph("50"))
        assertEquals(70f, OsmSpeedLimitService.parseMaxSpeedMph("UK:motorway"))
        assertEquals(60f, OsmSpeedLimitService.parseMaxSpeedMph("nsl_single"))
        assertEquals(70f, OsmSpeedLimitService.parseMaxSpeedMph("nsl_dual"))
        assertEquals(10f, OsmSpeedLimitService.parseMaxSpeedMph("walk"))
        assertNull(OsmSpeedLimitService.parseMaxSpeedMph(""))
    }

    @Test
    fun `parseOverpassResponse skips non-vehicle highways and prefers explicit maxspeed`() {
        val json = """
            {
              "elements": [
                { "type": "way", "id": 1, "tags": { "highway": "footway", "name": "Alley" } },
                { "type": "way", "id": 2, "tags": { "highway": "residential", "name": "Oak Lane" } },
                { "type": "way", "id": 3, "tags": { "highway": "primary", "maxspeed": "40 mph", "name": "Regent Road" } }
              ]
            }
        """.trimIndent()

        val result = OsmSpeedLimitService.parseOverpassResponse(json)
        assertNotNull(result)
        assertEquals(40f, result!!.speedLimitMph)
        assertEquals("Regent Road", result.roadName)
        assertEquals("OSM maxspeed", result.source)
    }

    @Test
    fun `parseOverpassResponse returns null on malformed json`() {
        assertNull(OsmSpeedLimitService.parseOverpassResponse("not json"))
    }
}
