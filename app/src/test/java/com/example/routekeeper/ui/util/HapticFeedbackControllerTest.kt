package com.example.routekeeper.ui.util

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.core.view.HapticFeedbackConstantsCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class HapticFeedbackControllerTest {

    @Test
    fun `clicking Mark Done emits confirmation haptic when incomplete and toggle-off haptic when reopening`() {
        val events = mutableListOf<RouteHapticEvent>()
        val controller = RouteHapticController { event -> events.add(event) }

        controller.onMarkDoneClick(wasAlreadyDelivered = false)
        controller.onMarkDoneClick(wasAlreadyDelivered = true)

        assertEquals(
            listOf(
                RouteHapticEvent.DELIVERY_MARKED_DONE,
                RouteHapticEvent.DELIVERY_REOPENED
            ),
            events
        )
        assertEquals(HapticFeedbackConstantsCompat.CONFIRM, RouteHapticEvent.DELIVERY_MARKED_DONE.compatConstant)
        assertEquals(HapticFeedbackType.LongPress, RouteHapticEvent.DELIVERY_MARKED_DONE.composeFallbackType)
        assertEquals(HapticFeedbackConstantsCompat.TOGGLE_OFF, RouteHapticEvent.DELIVERY_REOPENED.compatConstant)
        assertEquals(HapticFeedbackType.TextHandleMove, RouteHapticEvent.DELIVERY_REOPENED.composeFallbackType)
    }

    @Test
    fun `switching navigation app emits context-click haptic`() {
        val events = mutableListOf<RouteHapticEvent>()
        val controller = RouteHapticController { event -> events.add(event) }

        controller.onNavAppSwitched()

        assertEquals(listOf(RouteHapticEvent.NAV_APP_SWITCHED), events)
        assertEquals(HapticFeedbackConstantsCompat.CONTEXT_CLICK, RouteHapticEvent.NAV_APP_SWITCHED.compatConstant)
        assertEquals(HapticFeedbackType.TextHandleMove, RouteHapticEvent.NAV_APP_SWITCHED.composeFallbackType)
    }
}
