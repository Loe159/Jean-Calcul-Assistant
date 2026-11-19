package io.github.mangi.eta.ui.pages.providers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.data.model.typeLabel
import io.github.mangi.eta.data.repository.ProviderRepository
import io.github.mangi.eta.data.repository.RuntimeConfigRepository
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiSearchBarBox
import io.github.mangi.eta.ui.navigation.AppRoute
import io.github.mangi.eta.ui.navigation.NewProviderType
import kotlinx.coroutines.launch
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.basic.RadioButton

@Composable
internal fun ModelProviderListScreen(
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val providers by ProviderRepository.providersFlow().collectAsState(initial = emptyList())
    val selectedProviderId by RuntimeConfigRepository.selectedProviderIdFlow().collectAsState(initial = null)
    var searchQuery by remember { mutableStateOf("") }
    var providerToDelete by remember { mutableStateOf<ProviderSetting?>(null) }

    LaunchedEffect(Unit) {
        RuntimeConfigRepository.ensureDefaults(EtaApp.serviceInstance)
    }

    val filteredProviders = remember(providers, searchQuery) {
        val query = searchQuery.trim()
        providers.filter { provider ->
            query.isBlank() ||
                provider.name.contains(query, ignoreCase = true) ||
                provider.baseUrl.contains(query, ignoreCase = true) ||
                provider.typeLabel.contains(query, ignoreCase = true)
        }
    }

    CouiScaffoldPage(title = stringResource(R.string.ui_model_provider_e8c7f5), onBack = onBack) {
        CouiSearchBarBox(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(R.string.ui_search_provider_74e049),
        )

        ProviderSection(title = stringResource(R.string.ui_add_new_provider_74df54)) {

            ArrowPreference(
                title = "Add a vLLM server",
                summary = "OpenAI-compatible endpoint for your local server",
                startAction = {
                    Icon(
                        imageVector = Icons.Rounded.TravelExplore,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = COUITheme.colorScheme.labelSecondary,
                    )
                },
                onClick = { onNavigate(AppRoute.ModelProviderNew(NewProviderType.OpenAiCompatible)) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))


        }

        ProviderSection(
            title = pluralStringResource(
                R.plurals.provider_configured_count,
                filteredProviders.size,
                filteredProviders.size,
            ),
        ) {
            if (filteredProviders.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            stringResource(R.string.provider_empty)
                        } else {
                            stringResource(R.string.provider_no_matches)
                        },
                        style = COUITheme.textStyles.bodyS,
                        color = COUITheme.colorScheme.labelSecondary,
                    )
                }
            } else {
                filteredProviders.forEachIndexed { index, provider ->
                    ProviderListItem(
                        provider = provider,
                        isSelected = provider.id == selectedProviderId,
                        onOpen = { onNavigate(AppRoute.ModelProviderDetail(provider.id)) },
                        onDelete = if (!provider.isBuiltIn) {
                            { providerToDelete = provider }
                        } else {
                            null
                        },
                        onSelect = {
                            scope.launch {
                                RuntimeConfigRepository.setSelectedProviderId(provider.id)
                                RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                            }
                        },
                    )
                    if (index < filteredProviders.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp, end = 16.dp))
                    }
                }
            }
        }

        CouiPageBottomSpacer()
    }

    if (providerToDelete != null) {
        val target = providerToDelete
        val onConfirmDelete: () -> Unit = {
            scope.launch {
                target?.let { provider ->
                    ProviderRepository.deleteProvider(provider.id)
                    RuntimeConfigRepository.syncToRemotePreferences(EtaApp.serviceInstance)
                }
                providerToDelete = null
            }
        }
        CouiConfirmDialog(
            visible = true,
            title = stringResource(R.string.ui_remove_provider_9f848f),
            message = stringResource(R.string.provider_delete_summary, target?.name.orEmpty()),
            confirmText = stringResource(R.string.ui_delete_3755f5),
            destructive = true,
            onConfirm = onConfirmDelete,
            onDismissRequest = { providerToDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProviderListItem(
    provider: ProviderSetting,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?,
    onSelect: () -> Unit,
) {
    val opacity = if (provider.isEnabled) 1f else 0.6f
    val subtitle = listOfNotNull(
        provider.typeLabel,
        pluralStringResource(R.plurals.provider_models_count, provider.models.size, provider.models.size),
        stringResource(R.string.ui_built_in_09ceea).takeIf { provider.isBuiltIn },
        stringResource(R.string.ui_disabled_0fe5a9).takeIf { !provider.isEnabled },
    ).joinToString(" · ")

    BasicComponent(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen, onLongClick = onDelete)
            .graphicsLayer { alpha = opacity },
        startAction = { ProviderIcon(provider) },
        endActions = {
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                modifier = Modifier.padding(start = 12.dp),
                enabled = provider.isEnabled,
            )
        },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = provider.name,
                style = COUITheme.textStyles.headlineXS,
                color = if (isSelected) COUITheme.colorScheme.primary else COUITheme.colorScheme.labelPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (isSelected) {
                TagChip(text = stringResource(R.string.ui_current_25e74d), tone = TagChipTone.Emphasized)
            }
        }
        if (provider.baseUrl.isNotBlank()) {
            Text(
                text = provider.baseUrl,
                style = COUITheme.textStyles.bodyS,
                color = COUITheme.colorScheme.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = subtitle,
            style = COUITheme.textStyles.bodyS,
            color = COUITheme.colorScheme.labelSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
