package com.example.routekeeper.data.repository

import com.example.routekeeper.data.model.AppThemeId
import com.example.routekeeper.data.model.Coordinate
import com.example.routekeeper.data.model.GpsBreadcrumb
import com.example.routekeeper.data.model.MapPreferences
import com.example.routekeeper.data.model.RouteStep
import com.example.routekeeper.data.model.RoutingMode
import com.example.routekeeper.data.model.TrafficLevel
import com.example.routekeeper.data.model.TrafficRouteSegment
import com.example.routekeeper.data.service.RoadNavigationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRepositoryTest {

    @Test
    fun `initial sample draft never auto-completes stops`() {
        val repo = RouteRepository(context = null)
        val draft = repo.activeDraft.value!!
        assertTrue(draft.stops.isNotEmpty())
        assertTrue(draft.stops.none { it.delivered })
    }

    @Test
    fun `segmented navigation recording stops per delivery and combines all segments when last stop completes`() {
        val repo = RouteRepository(context = null)
        val stops = repo.activeDraft.value!!.stops

        // Navigate stop 1
        repo.beginNavigationSegment(stops[0].id, stops[0].name)
        assertTrue(repo.activeDraft.value!!.isRecordingGps)

        repo.appendGpsBreadcrumb(
            GpsBreadcrumb("p1", 1000L, "09:00:05", 51.503, -0.127, 10f, 22.3f, 36f, speedLimitMph = 30f, speedLimitSource = "OSM default")
        )
        repo.updateBreadcrumbRoadLimit("p1", 20f, "Downing Street", "OSM maxspeed")
        assertTrue(repo.activeDraft.value!!.actualTrack.first().isSpeeding)

        // Complete stop 1 -> recording stops until user taps Navigate on next stop
        val u1 = repo.markStopDelivered(stops[0].id)
        assertTrue(u1.stopNowDelivered)
        assertTrue(u1.shouldStopRecording)
        assertFalse(u1.allDeliveriesCompleted)
        assertFalse(repo.activeDraft.value!!.isRecordingGps)

        // Navigate and complete remaining stops
        for (i in 1 until stops.size) {
            repo.beginNavigationSegment(stops[i].id, stops[i].name)
            repo.appendGpsBreadcrumb(
                GpsBreadcrumb("p${i + 1}", (i + 1) * 5000L, "09:0${i}:00", 51.51 + i * 0.001, -0.12, 8f, 18f, 29f)
            )
            val update = repo.markStopDelivered(stops[i].id)
            if (i == stops.lastIndex) {
                assertTrue(update.allDeliveriesCompleted)
                assertTrue(repo.activeDraft.value!!.isTrackCombined)
                assertEquals(stops.size, repo.activeDraft.value!!.trackSegments.size)
                assertEquals(stops.size, repo.activeDraft.value!!.actualTrack.size)
            }
        }
    }

    @Test
    fun `switching route mode preserves completed deliveries and preferences clamp gps interval`() {
        val repo = RouteRepository(context = null)
        val firstId = repo.activeDraft.value!!.stops.first().id
        repo.markStopDelivered(firstId)

        repo.switchRouteMode("reverse")
        assertTrue(repo.activeDraft.value!!.stops.first { it.id == firstId }.delivered)

        repo.updatePreferences(MapPreferences(gpsIntervalSeconds = 12))
        assertEquals(5, repo.preferences.value.gpsIntervalSeconds)

        repo.updatePreferences(MapPreferences(gpsIntervalSeconds = 0))
        assertEquals(1, repo.preferences.value.gpsIntervalSeconds)
    }

    @Test
    fun `applyRoadNavigationResult updates active draft and ignores stale route modes`() {
        val repo = RouteRepository(context = null)
        val draft = repo.activeDraft.value!!
        val initialMode = draft.selectedAlternativeId

        val staleResult = RoadNavigationResult(
            path = listOf(Coordinate(51.5, -0.1), Coordinate(51.51, -0.11)),
            steps = listOf(RouteStep("Turn left", 300.0, 45.0, "Strand", TrafficLevel.SEVERE)),
            trafficSegments = listOf(TrafficRouteSegment(51.5, -0.1, 51.51, -0.11, TrafficLevel.SEVERE, "Strand")),
            distanceMeters = 5500.0,
            durationSeconds = 900.0,
            routingMode = RoutingMode.ROAD_NETWORK,
            trafficSummary = "1 heavy segment"
        )

        // Applying for non-selected mode must be ignored
        repo.applyRoadNavigationResult(draft.id, "reverse", staleResult)
        assertFalse(repo.activeDraft.value!!.trafficSummary == "1 heavy segment")

        // Applying for matching mode updates road path, traffic segments, and saved route persistence
        repo.applyRoadNavigationResult(draft.id, initialMode, staleResult)
        val updated = repo.activeDraft.value!!
        assertEquals(RoutingMode.ROAD_NETWORK, updated.routingMode)
        assertEquals(1, updated.trafficSegments.size)
        assertEquals(TrafficLevel.SEVERE, updated.trafficSegments.first().trafficLevel)
        assertEquals("1 heavy segment", updated.trafficSummary)

        repo.saveActiveDraftToHistory()
        val saved = repo.savedRoutes.value.first()
        assertEquals(1, saved.trafficSegments.size)
        assertEquals("1 heavy segment", saved.trafficSummary)
    }

    @Test
    fun `switching app theme updates preferences without altering route stops or delivery state`() {
        val repo = RouteRepository(context = null)
        assertEquals(AppThemeId.FOREST_PINE, repo.preferences.value.appTheme)

        val initialDraft = repo.activeDraft.value!!
        val initialStopIds = initialDraft.stops.map { it.id }
        val firstStopId = initialStopIds.first()
        repo.markStopDelivered(firstStopId)

        val deliveredBeforeThemeChange = repo.activeDraft.value!!.stops.map { it.delivered }
        val routeModeBefore = repo.activeDraft.value!!.selectedAlternativeId
        val departureBefore = repo.activeDraft.value!!.departureStartTime

        for (theme in AppThemeId.entries) {
            repo.updatePreferences(repo.preferences.value.copy(appTheme = theme))
            assertEquals(theme, repo.preferences.value.appTheme)

            val currentDraft = repo.activeDraft.value!!
            assertEquals(initialStopIds, currentDraft.stops.map { it.id })
            assertEquals(deliveredBeforeThemeChange, currentDraft.stops.map { it.delivered })
            assertEquals(routeModeBefore, currentDraft.selectedAlternativeId)
            assertEquals(departureBefore, currentDraft.departureStartTime)
        }
    }
}
