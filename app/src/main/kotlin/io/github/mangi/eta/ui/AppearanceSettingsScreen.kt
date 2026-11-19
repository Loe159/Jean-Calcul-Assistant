package io.github.mangi.eta.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.suqi8.coui.kmp.preference.SliderPreference
import io.github.mangi.eta.data.model.AppearanceAccentColor
import io.github.mangi.eta.data.model.AppearancePaletteStyle
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.data.model.AppearanceThemeMode
import io.github.mangi.eta.data.model.AppearanceTopBarBlurStyle
import io.github.mangi.eta.data.model.MAX_INTERFACE_SCALE
import io.github.mangi.eta.data.model.MIN_INTERFACE_SCALE
import io.github.mangi.eta.data.model.normalizeInterfaceScale
import io.github.mangi.eta.data.repository.AppearanceSettingsRepository
import io.github.mangi.eta.ui.app.LocalAppearanceSettings
import io.github.mangi.eta.ui.components.coui.CouiInputDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.blur.isRuntimeShaderSupported
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.preference.PreferenceCategory

@Composable
internal fun AppearanceSettingsScreen(onBack: () -> Unit) {
    val appearance = LocalAppearanceSettings.current
    val coroutineScope = rememberCoroutineScope()
    var scaleDraft by remember(appearance.interfaceScale) {
        mutableFloatStateOf(appearance.interfaceScale * 100f)
    }
    var showScaleDialog by remember { mutableStateOf(false) }
    var scaleInput by remember { mutableStateOf("") }
    val blurSupported = isRuntimeShaderSupported()

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        coroutineScope.launch {
            AppearanceSettingsRepository.update(transform)
        }
    }

    fun commitScale(percent: Float) {
        val scale = normalizeInterfaceScale(percent.roundToInt() / 100f)
        scaleDraft = scale * 100f
        update { current -> current.copy(interfaceScale = scale) }
    }

    val themeModes = AppearanceThemeMode.entries
    val themeModeLabels = listOf(
        stringResource(R.string.appearance_theme_system),
        stringResource(R.string.appearance_theme_light),
        stringResource(R.string.appearance_theme_dark),
    )
    val paletteStyles = AppearancePaletteStyle.entries
    val paletteLabels = listOf(
        stringResource(R.string.appearance_palette_tonal_spot),
        stringResource(R.string.appearance_palette_neutral),
        stringResource(R.string.appearance_palette_vibrant),
        stringResource(R.string.appearance_palette_expressive),
        stringResource(R.string.appearance_palette_rainbow),
        stringResource(R.string.appearance_palette_fruit_salad),
        stringResource(R.string.appearance_palette_monochrome),
        stringResource(R.string.appearance_palette_fidelity),
        stringResource(R.string.appearance_palette_content),
    )
    val accentColors = AppearanceAccentColor.entries
    val accentLabels = listOf(
        stringResource(R.string.appearance_accent_system),
        stringResource(R.string.appearance_accent_blue),
        stringResource(R.string.appearance_accent_purple),
        stringResource(R.string.appearance_accent_pink),
        stringResource(R.string.appearance_accent_red),
        stringResource(R.string.appearance_accent_orange),
        stringResource(R.string.appearance_accent_yellow),
        stringResource(R.string.appearance_accent_green),
        stringResource(R.string.appearance_accent_teal),
    )
    val blurStyles = AppearanceTopBarBlurStyle.entries
    val blurStyleLabels = listOf(
        stringResource(R.string.appearance_blur_style_gaussian),
        stringResource(R.string.appearance_blur_style_progressive),
    )
    val blurStyleSummaries = listOf(
        stringResource(R.string.appearance_blur_style_gaussian_summary),
        stringResource(R.string.appearance_blur_style_progressive_summary),
    )
    val parsedScale = scaleInput.toIntOrNull()

    CouiScaffoldPage(
        title = stringResource(R.string.appearance_title),
        onBack = onBack,
        compactTitle = true,
    ) {
        PreferenceCategory(title = stringResource(R.string.appearance_group_color))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    OverlayDropdownPreference(
                        title = stringResource(R.string.appearance_theme_mode),
                        items = themeModeLabels,
                        selectedIndex = appearance.themeMode.ordinal,
                        onSelectedIndexChange = { index ->
                            themeModes.getOrNull(index)?.let { mode ->
                                update { current -> current.copy(themeMode = mode) }
                            }
                        },
                    )
                }
            }
        }

        PreferenceCategory(title = stringResource(R.string.appearance_group_interface))
        Card(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            CardListGroup {
                item {
                    SwitchPreference(
                        title = stringResource(R.string.appearance_swipe_dismiss),
                        summary = stringResource(R.string.appearance_swipe_dismiss_summary),
                        checked = appearance.swipeDismissEnabled,
                        onCheckedChange = { enabled ->
                            update { current -> current.copy(swipeDismissEnabled = enabled) }
                        },
                    )
                }
                item {
                    SwitchPreference(
                        title = stringResource(R.string.appearance_predictive_back),
                        summary = stringResource(R.string.appearance_predictive_back_summary),
                        checked = appearance.predictiveBackEnabled,
                        onCheckedChange = { enabled ->
                            update { current -> current.copy(predictiveBackEnabled = enabled) }
                        },
                    )
                }
                item {
                    SliderPreference(
                        value = scaleDraft.coerceIn(
                            MIN_INTERFACE_SCALE * 100f,
                            MAX_INTERFACE_SCALE * 100f,
                        ),
                        onValueChange = { scaleDraft = it },
                        title = stringResource(R.string.appearance_interface_scale),
                        summary = stringResource(R.string.appearance_interface_scale_summary),
                        valueText = "${scaleDraft.roundToInt()}%",
                        onClick = {
                            scaleInput = scaleDraft.roundToInt().toString()
                            showScaleDialog = true
                        },
                        valueRange = (MIN_INTERFACE_SCALE * 100f)..(MAX_INTERFACE_SCALE * 100f),
                        steps = 5,
                        onValueChangeFinished = { commitScale(scaleDraft) },
                    )
                }
            }
        }

        CouiPageBottomSpacer()
    }

    CouiInputDialog(
        visible = showScaleDialog,
        title = stringResource(R.string.appearance_interface_scale_dialog_title),
        message = stringResource(R.string.appearance_interface_scale_dialog_summary),
        value = scaleInput,
        onValueChange = { value -> scaleInput = value.filter(Char::isDigit).take(3) },
        placeholder = stringResource(R.string.appearance_interface_scale_input_label),
        confirmText = stringResource(R.string.action_confirm),
        confirmEnabled = parsedScale != null && parsedScale in 80..110,
        onConfirm = {
            parsedScale?.let { commitScale(it.toFloat()) }
            showScaleDialog = false
        },
        onDismissRequest = { showScaleDialog = false },
    )
}
