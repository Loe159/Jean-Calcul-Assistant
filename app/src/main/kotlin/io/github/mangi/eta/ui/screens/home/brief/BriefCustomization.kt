package io.github.mangi.eta.ui.screens.home.brief

import android.content.Context
import androidx.core.content.edit
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.icon.extended.Filter
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.PreferenceCategory
import io.github.suqi8.coui.kmp.preference.SwitchPreference

internal class BriefVisibilityPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("now_brief_visibility", Context.MODE_PRIVATE)

    fun hiddenCards(): Set<BriefCardId> =
        BriefCardId.entries.filterTo(mutableSetOf()) { preferences.getBoolean(it.key, false) }

    fun setVisible(card: BriefCardId, visible: Boolean) {
        preferences.edit { putBoolean(card.key, !visible) }
    }

    fun foodCardsVisible() = preferences.getBoolean("food_cards_visible", true)

    fun setFoodCardsVisible(visible: Boolean) {
        preferences.edit { putBoolean("food_cards_visible", visible) }
    }

    fun previewsExpanded(): Boolean = preferences.getBoolean(PREVIEWS_EXPANDED, false)

    fun setPreviewsExpanded(expanded: Boolean) {
        preferences.edit { putBoolean(PREVIEWS_EXPANDED, expanded) }
    }

    private companion object {
        const val PREVIEWS_EXPANDED = "previews_expanded"
    }
}

@Composable
internal fun BriefCustomization(
    show: Boolean,
    hiddenCards: Set<BriefCardId>,
    hasCarSession: Boolean,
    onVisibilityChange: (BriefCardId, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onManageFiltering: () -> Unit,
    foodCardsVisible: Boolean? = null,
    onFoodCardsVisibilityChange: (Boolean) -> Unit = {},
) {
    OverlayBottomSheet(
        show = show,
        title = BriefSettingsLabel,
        onDismissRequest = onDismiss,
        startAction = {
            CouiTopAppBarIconButton(COUIIcons.Basic.Close, "Fermer", onDismiss)
        },
        showDragHandle = true,
        insideMargin = DpSize(16.dp, 0.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            val (previewCards, realCards) = BriefCardId.entries.partition { it.isPreview(hasCarSession) }
            item {
                CardListGroup {
                    if (foodCardsVisible != null) {
                        item {
                            SwitchPreference(
                                title = stringResource(R.string.food_brief_customization),
                                checked = foodCardsVisible,
                                onCheckedChange = onFoodCardsVisibilityChange,
                            )
                        }
                    }
                    realCards.forEach { card ->
                        item {
                            SwitchPreference(
                                title = card.title,
                                checked = card !in hiddenCards,
                                onCheckedChange = { onVisibilityChange(card, it) },
                                startAction = { Icon(card.icon, null, tint = briefTintedLabel(card.tint())) },
                            )
                        }
                    }
                }
            }
            item { PreferenceCategory(title = "Aperçus") }
            item {
                CardListGroup {
                    previewCards.forEach { card ->
                        item {
                            SwitchPreference(
                                title = card.title,
                                checked = card !in hiddenCards,
                                onCheckedChange = { onVisibilityChange(card, it) },
                                startAction = { Icon(card.icon, null, tint = briefTintedLabel(card.tint())) },
                            )
                        }
                    }
                }
            }
            item {
                CardListGroup {
                    item {
                        ArrowPreference(
                            title = "Notifications intelligentes",
                            startAction = { Icon(COUIIcons.Filter, null) },
                            onClick = onManageFiltering,
                        )
                    }
                }
            }
        }
    }
}
