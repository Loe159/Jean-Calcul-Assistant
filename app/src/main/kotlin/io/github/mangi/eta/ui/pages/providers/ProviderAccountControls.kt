package io.github.mangi.eta.ui.pages.providers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.data.model.ProviderAuthModes
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ProviderAccountControls(
    state: ProviderLoginState,
    authMode: String,
    enabled: Boolean,
    browserError: String?,
    onOpenBrowser: () -> Unit,
    onManageUsage: () -> Unit,
    onLogin: () -> Unit,
    onDisconnect: () -> Unit,
    onCancel: () -> Unit,
) {
    val prompt = state.prompt
    val account = state.account
    val loggedIn = account != null && !account.needsReauthentication
    val needsLogin = !loggedIn || !account.planEnabled
    val brand = if (authMode == ProviderAuthModes.XAI_GROK) "Grok" else "Codex"
    val title = account?.email ?: account?.displayName ?: "$brand account"
    val summary = browserError ?: state.message ?: when (state.phase) {
        ProviderLoginPhase.Loading -> "Loading account…"
        ProviderLoginPhase.Saving -> "Saving…"
        ProviderLoginPhase.Disconnecting -> "Signing out…"
        ProviderLoginPhase.Authorizing -> if (prompt == null) "Preparing sign-in…" else "Finish signing in in your browser"
        ProviderLoginPhase.SyncingModels -> "Signed in; fetching models…"
        ProviderLoginPhase.Idle -> when {
            account == null -> if (authMode == ProviderAuthModes.OPENAI_CHATGPT) {
                "Sign in to Codex with your ChatGPT account"
            } else {
                "Sign in to use your Grok subscription"
            }
            account.needsReauthentication -> "Sign in again"
            !account.planEnabled -> "The account has not granted model access"
            authMode == ProviderAuthModes.OPENAI_CHATGPT -> "Signed in to Codex"
            else -> "Signed in to Grok"
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiPreference(
                title = title,
                summary = summary,
                showChevron = false,
            )
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                prompt?.userCode?.let { userCode ->
                    SelectionContainer {
                        Text(
                            text = userCode,
                            style = MiuixTheme.textStyles.headline1,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.isBusy) {
                        if (prompt != null) {
                            CouiButton(
                                text = "Open browser",
                                onClick = onOpenBrowser,
                                role = CouiButtonRole.Primary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.phase in setOf(ProviderLoginPhase.Saving, ProviderLoginPhase.Authorizing, ProviderLoginPhase.SyncingModels)) {
                            CouiButton(
                                text = "Cancel",
                                onClick = onCancel,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        if (needsLogin) {
                            CouiButton(
                                text = if (loggedIn) "Sign in again" else "Sign in with $brand",
                                enabled = enabled,
                                onClick = onLogin,
                                role = CouiButtonRole.Primary,
                                modifier = Modifier.weight(1f),
                            )
                        } else if (authMode == ProviderAuthModes.OPENAI_CHATGPT) {
                            CouiButton(
                                text = "Manage usage",
                                enabled = enabled,
                                onClick = onManageUsage,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (loggedIn) {
                            CouiButton(
                                text = "Sign out",
                                enabled = enabled,
                                onClick = onDisconnect,
                                role = CouiButtonRole.Destructive,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        AppearanceUiStyle.MIUIX -> {
            BasicComponent(title = title, summary = summary)
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                prompt?.userCode?.let { userCode ->
                    SelectionContainer {
                        Text(
                            text = userCode,
                            style = MiuixTheme.textStyles.headline1,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.isBusy) {
                        if (prompt != null) {
                            TextButton(text = "Open browser", onClick = onOpenBrowser, modifier = Modifier.weight(1f))
                        }
                        if (state.phase in setOf(ProviderLoginPhase.Saving, ProviderLoginPhase.Authorizing, ProviderLoginPhase.SyncingModels)) {
                            TextButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                        }
                    } else {
                        if (needsLogin) {
                            TextButton(
                                text = if (loggedIn) "Sign in again" else "Sign in with $brand",
                                enabled = enabled,
                                onClick = onLogin,
                                modifier = Modifier.weight(1f),
                            )
                        } else if (authMode == ProviderAuthModes.OPENAI_CHATGPT) {
                            TextButton(text = "Manage usage", enabled = enabled, onClick = onManageUsage, modifier = Modifier.weight(1f))
                        }
                        if (loggedIn) {
                            TextButton(text = "Sign out", enabled = enabled, onClick = onDisconnect, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

internal fun openProviderAccountLink(context: Context, url: String): String? = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    null
} catch (_: ActivityNotFoundException) {
    "No browser available"
} catch (_: SecurityException) {
    "Could not open the browser. Check system settings."
}
