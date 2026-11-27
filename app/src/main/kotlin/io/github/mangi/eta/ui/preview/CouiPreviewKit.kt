package io.github.mangi.eta.ui.preview

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.suqi8.coui.kmp.theme.COUITheme

/*
 * Preview kit for `android preview render` (see docs/agents/PREVIEWS.md).
 *
 * Every screen or component preview in this app:
 *  - is `private`, named `<Thing>Preview`, and lives at the bottom of the file it previews
 *    (or in ui/preview/ when it shows the design system itself);
 *  - is annotated with one of the multipreviews below, never a bare `@Preview` with ad-hoc sizes;
 *  - wraps its content in [CouiPreviewTheme] so it renders with the real app theme;
 *  - feeds stateless composables with synthetic data (FakeAgentUiStates, preview fixtures),
 *    never stores, repositories, network or Context-backed singletons;
 *  - is playable when opened live on the owner's phone (Preview Hub → Components): local
 *    interactions hold `remember` state, outward callbacks use [previewAction] — never a silent `{}`.
 */

/** Reference phone: 393 × 852 dp (a 6.1–6.7" handset). */
const val PREVIEW_PHONE_WIDTH_DP = 393
const val PREVIEW_PHONE_HEIGHT_DP = 852

/** Light + dark at the reference phone size. The default for screens. */
@Preview(
    name = "Light",
    group = "Phone",
    widthDp = PREVIEW_PHONE_WIDTH_DP,
    heightDp = PREVIEW_PHONE_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
@Preview(
    name = "Dark",
    group = "Phone",
    widthDp = PREVIEW_PHONE_WIDTH_DP,
    heightDp = PREVIEW_PHONE_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
annotation class CouiPhonePreviews

/** Stress cases: 200 % font on the reference phone, and a small 320 × 640 dp phone. */
@Preview(
    name = "Font 200%",
    group = "Stress",
    widthDp = PREVIEW_PHONE_WIDTH_DP,
    heightDp = PREVIEW_PHONE_HEIGHT_DP,
    fontScale = 2f,
    showBackground = true,
)
@Preview(
    name = "Small phone",
    group = "Stress",
    widthDp = 320,
    heightDp = 640,
    showBackground = true,
)
annotation class CouiStressPreviews

/** Light + dark, height wrapping the content. For components and cards, not full screens. */
@Preview(
    name = "Light",
    group = "Component",
    widthDp = PREVIEW_PHONE_WIDTH_DP,
    uiMode = Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
@Preview(
    name = "Dark",
    group = "Component",
    widthDp = PREVIEW_PHONE_WIDTH_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    showBackground = true,
)
annotation class CouiComponentPreviews

/**
 * Callback for a preview action that leaves the component (navigation, submit, network).
 * Headless renders: does nothing. Opened live on the phone through PreviewActivity: shows a
 * toast naming the action, so the owner sees the button works. Interactions that stay inside
 * the component (tabs, selection, switches, text) use real `remember` state instead.
 */
@Composable
fun previewAction(name: String): () -> Unit {
    val toast = previewToast()
    return remember(name, toast) { { toast(actionLabel(name)) } }
}

/** [previewAction] for one-argument callbacks; the toast shows the value. */
@Composable
fun <T> previewActionOf(name: String): (T) -> Unit {
    val toast = previewToast()
    return remember(name, toast) { { value: T -> toast("${actionLabel(name)} ($value)") } }
}

/** "onOpenPermissions" → "Open permissions". */
internal fun actionLabel(name: String): String {
    val words = name.removePrefix("on").split(Regex("(?<=[a-z0-9])(?=[A-Z])")).filter { it.isNotBlank() }
    return words.mapIndexed { i, word -> if (i == 0) word.replaceFirstChar { it.uppercase() } else word.lowercase() }
        .joinToString(" ").ifEmpty { name }
}

@Composable
private fun previewToast(): (String) -> Unit {
    val inspection = LocalInspectionMode.current
    val context = LocalContext.current.applicationContext
    return remember(inspection, context) {
        if (inspection) { _: String -> }
        else { label: String -> Toast.makeText(context, "Sample only · $label", Toast.LENGTH_SHORT).show() }
    }
}

/**
 * The real app theme ([AgentAppTheme] with default appearance: COUI palette, system dark mode)
 * over the COUI window background. Dark mode follows the preview's `uiMode`.
 * [fullScreen] = false wraps the content height (component previews).
 */
@Composable
fun CouiPreviewTheme(
    fullScreen: Boolean = true,
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit,
) {
    AgentAppTheme(appearance = appearance, applyInterfaceScale = false) {
        Box(
            modifier = Modifier
                .then(if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .background(COUITheme.colorScheme.backgroundWithCard),
        ) {
            content()
        }
    }
}
