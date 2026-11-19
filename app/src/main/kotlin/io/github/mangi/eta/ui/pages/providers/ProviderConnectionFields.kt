@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")

package io.github.mangi.eta.ui.pages.providers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AnthropicProviderSetting
import io.github.mangi.eta.data.model.GeminiProviderSetting
import io.github.mangi.eta.data.model.OpenAiEndpointMode
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.data.model.ProviderSetting
import io.github.mangi.eta.ui.components.coui.CouiTextField
import io.github.suqi8.coui.kmp.basic.Icon
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.basic.HorizontalDivider
import io.github.suqi8.coui.kmp.preference.OverlayDropdownPreference
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun ProviderConnectionFields(
    provider: ProviderSetting,
    draft: ProviderConfigDraft,
    onDraftChange: (ProviderConfigDraft) -> Unit,
    loginState: ProviderLoginState,
    enabled: Boolean,
    browserError: String?,
    onOpenBrowser: () -> Unit,
    onManageUsage: () -> Unit,
    onLogin: () -> Unit,
    onDisconnect: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    var apiKeyVisible by remember { mutableStateOf(false) }
    val oauth = ProviderAuthModes.isOAuth(draft.authMode)
    val accountMode = providerAccountAuthMode(provider)
    val showWebSearchSwitch =
        provider is GeminiProviderSetting ||
            (draft.endpointMode == OpenAiEndpointMode.RESPONSES && provider !is AnthropicProviderSetting)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        CouiTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it)) },
            label = stringResource(R.string.ui_name_1be7ae),
            singleLine = true,
            enabled = enabled,
        )
    }
    if (accountMode != null) {
    }
    if (oauth) {
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
        ProviderAccountControls(
            state = loginState,
            authMode = draft.authMode,
            enabled = enabled,
            browserError = browserError,
            onOpenBrowser = onOpenBrowser,
            onManageUsage = onManageUsage,
            onLogin = onLogin,
            onDisconnect = onDisconnect,
            onCancel = onCancel,
        )
    } else {
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            CouiTextField(
                value = draft.baseUrl,
                onValueChange = { onDraftChange(draft.copy(baseUrl = it)) },
                label = "Base URL",
                singleLine = true,
                enabled = enabled,
            )
            Spacer(modifier = Modifier.height(12.dp))
            CouiTextField(
                value = draft.apiKey,
                onValueChange = { onDraftChange(draft.copy(apiKey = it)) },
                label = "API key (optional for vLLM)",
                singleLine = true,
                enabled = enabled,
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(28.dp)
                            .clickable(role = Role.Button) { apiKeyVisible = !apiKeyVisible },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = context.getString(
                                if (apiKeyVisible) R.string.page_hide_bb0e7e else R.string.page_show_71b677,
                            ),
                            modifier = Modifier.size(20.dp),
                            tint = COUITheme.colorScheme.labelSecondary,
                        )
                    }
                },
            )
            if (provider is AnthropicProviderSetting) {
                Spacer(modifier = Modifier.height(12.dp))
                CouiTextField(
                    value = draft.anthropicVersion,
                    onValueChange = { onDraftChange(draft.copy(anthropicVersion = it)) },
                    label = "anthropic-version",
                    singleLine = true,
                    enabled = enabled,
                )
            }
        }
        if (provider !is AnthropicProviderSetting && provider !is GeminiProviderSetting) {
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
            OverlayDropdownPreference(
                title = stringResource(R.string.ui_endpoint_mode_3c8546),
                items = listOf("Chat Completions API", "Responses API"),
                selectedIndex = if (draft.endpointMode == OpenAiEndpointMode.RESPONSES) 1 else 0,
                enabled = enabled,
                summary = context.getString(
                    if (draft.endpointMode == OpenAiEndpointMode.RESPONSES) {
                        R.string.page_using_typed_items_with_semantic_streaming_events_f9c906
                    } else {
                        R.string.page_use_standard_chat_completions_ee4b1a
                    },
                ),
                onSelectedIndexChange = { index ->
                    onDraftChange(
                        draft.copy(
                            endpointMode = if (index == 1) OpenAiEndpointMode.RESPONSES else OpenAiEndpointMode.CHAT_COMPLETIONS,
                        ),
                    )
                },
            )
        }
    }
    if (showWebSearchSwitch) {
        HorizontalDivider(modifier = Modifier.padding(start = 16.dp, end = 16.dp))
        SwitchPreference(
            title = if (provider is GeminiProviderSetting) "Google Search" else stringResource(R.string.ui_server_side_web_search_ddb8e0),
            summary = if (provider is GeminiProviderSetting) {
                null
            } else {
                stringResource(R.string.ui_allows_the_model_to_call_web_searches_provided_by_th_2f752f)
            },
            checked = draft.hostedWebSearchEnabled,
            enabled = enabled,
            onCheckedChange = { onDraftChange(draft.copy(hostedWebSearchEnabled = it)) },
        )
    }
}
