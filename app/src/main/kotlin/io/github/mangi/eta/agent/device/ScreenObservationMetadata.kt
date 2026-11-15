package io.github.mangi.eta.agent.device

import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService.NodeSnapshotStatus
import io.github.mangi.eta.agent.accessibility.AgentAccessibilityService.ObservedWindow
import java.util.Locale
import org.json.JSONObject

internal object ScreenObservationMetadata {
    fun mayUseUiAutomator(
        requested: Boolean,
        accessibilityConnected: Boolean,
        rootAvailable: Boolean,
        virtualDisplay: Boolean,
    ): Boolean = requested && !accessibilityConnected && rootAvailable && !virtualDisplay

    fun uiTreeStatus(
        requested: Boolean,
        accessibilityStatus: NodeSnapshotStatus?,
        dumpOutcome: ShellActionOutcomePolicy.Outcome?,
        nodeCount: Int,
    ): String = when {
        !requested -> "not_requested"
        accessibilityStatus != null -> accessibilityStatus.name.lowercase(Locale.ROOT)
        dumpOutcome == ShellActionOutcomePolicy.Outcome.TIMED_OUT -> "timeout"
        dumpOutcome == ShellActionOutcomePolicy.Outcome.FAILED -> "query_failed"
        dumpOutcome == ShellActionOutcomePolicy.Outcome.SUCCEEDED -> if (nodeCount > 0) "available" else "empty"
        else -> "service_unavailable"
    }

    fun focus(window: ObservedWindow?, uiAutomatorPackageName: String?): JSONObject {
        val packageName = if (window != null) window.packageName else uiAutomatorPackageName
        val observedPackageName = packageName?.takeIf(String::isNotBlank)
        return JSONObject()
            .put("package", observedPackageName.orEmpty())
            .put("component", "")
            .put(
                "source",
                when {
                    window != null -> "accessibility"
                    observedPackageName != null -> "uiautomator"
                    else -> "unavailable"
                },
            )
            .put("window_id", window?.windowId ?: JSONObject.NULL)
            .put("display_id", window?.displayId ?: JSONObject.NULL)
            .put("focused", window?.focused ?: JSONObject.NULL)
            .put("active", window?.active ?: JSONObject.NULL)
    }

    fun uiTreeNote(status: String, screenshotAttached: Boolean): String {
        val description = when (status) {
            "not_requested" -> "UI tree was not requested"
            "available" -> "UI tree available"
            "empty" -> "Windows read but no nodes available. This does not prove the app cannot use this display."
            "no_window" -> "Target display returned no observable window"
            "root_unavailable" -> "Target window found, but its root node is unreadable"
            "timeout" -> "Node query timed out"
            "busy" -> "Previous node query has not finished"
            "query_failed" -> "Node query failed"
            else -> "Accessibility unavailable; no node reading channel"
        }
        if (status == "not_requested" || status == "available") return description
        return description + if (screenshotAttached) {
            "; use the attached screenshot to inspect the UI"
        } else {
            "; request a screenshot to verify the UI"
        }
    }
}
