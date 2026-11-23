package com.example.routekeeper.ui.util

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat

enum class RouteHapticEvent(
    val compatConstant: Int,
    val composeFallbackType: HapticFeedbackType
) {
    DELIVERY_MARKED_DONE(
        compatConstant = HapticFeedbackConstantsCompat.CONFIRM,
        composeFallbackType = HapticFeedbackType.LongPress
    ),
    DELIVERY_REOPENED(
        compatConstant = HapticFeedbackConstantsCompat.TOGGLE_OFF,
        composeFallbackType = HapticFeedbackType.TextHandleMove
    ),
    NAV_APP_SWITCHED(
        compatConstant = HapticFeedbackConstantsCompat.CONTEXT_CLICK,
        composeFallbackType = HapticFeedbackType.TextHandleMove
    )
}

fun interface HapticSignalSink {
    fun perform(event: RouteHapticEvent)
}

class RouteHapticController(
    private val sink: HapticSignalSink
) {
    fun trigger(event: RouteHapticEvent) {
        sink.perform(event)
    }

    fun onMarkDoneClick(wasAlreadyDelivered: Boolean) {
        trigger(
            if (wasAlreadyDelivered) RouteHapticEvent.DELIVERY_REOPENED
            else RouteHapticEvent.DELIVERY_MARKED_DONE
        )
    }

    fun onNavAppSwitched() {
        trigger(RouteHapticEvent.NAV_APP_SWITCHED)
    }

    companion object {
        fun forAndroidView(
            view: View,
            composeHaptic: HapticFeedback
        ): RouteHapticController = RouteHapticController { event ->
            val handled = runCatching {
                ViewCompat.performHapticFeedback(view, event.compatConstant)
            }.getOrDefault(false)
            if (!handled) {
                composeHaptic.performHapticFeedback(event.composeFallbackType)
            }
        }
    }
}

@Composable
fun rememberRouteHapticController(): RouteHapticController {
    val view = LocalView.current
    val composeHaptic = LocalHapticFeedback.current
    return remember(view, composeHaptic) {
        RouteHapticController.forAndroidView(view = view, composeHaptic = composeHaptic)
    }
}
