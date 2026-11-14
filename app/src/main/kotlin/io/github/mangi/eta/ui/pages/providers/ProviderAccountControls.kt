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
    val title = account?.email ?: account?.displayName ?: "$brand compte"
    val summary = browserError ?: state.message ?: when (state.phase) {
        ProviderLoginPhase.Loading -> "Lecture du compte…"
        ProviderLoginPhase.Saving -> "Enregistrement…"
        ProviderLoginPhase.Disconnecting -> "Déconnexion…"
        ProviderLoginPhase.Authorizing -> if (prompt == null) "Préparation de la connexion…" else "Terminez la connexion dans le navigateur"
        ProviderLoginPhase.SyncingModels -> "Connecté ; récupération des modèles…"
        ProviderLoginPhase.Idle -> when {
            account == null -> if (authMode == ProviderAuthModes.OPENAI_CHATGPT) {
                "使用 ChatGPT compte登录 Codex"
            } else {
                "登录后使用 Grok 订阅额度"
            }
            account.needsReauthentication -> "Veuillez vous reconnecter"
            !account.planEnabled -> "compte尚未授予模型调用权限"
            authMode == ProviderAuthModes.OPENAI_CHATGPT -> "Connecté à Codex"
            else -> "已登录 Grok"
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
                                text = "Ouvrir le navigateur",
                                onClick = onOpenBrowser,
                                role = CouiButtonRole.Primary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (state.phase in setOf(ProviderLoginPhase.Saving, ProviderLoginPhase.Authorizing, ProviderLoginPhase.SyncingModels)) {
                            CouiButton(
                                text = "Annuler",
                                onClick = onCancel,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        if (needsLogin) {
                            CouiButton(
                                text = if (loggedIn) "Reconnecter" else "Se connecter avec $brand",
                                enabled = enabled,
                                onClick = onLogin,
                                role = CouiButtonRole.Primary,
                                modifier = Modifier.weight(1f),
                            )
                        } else if (authMode == ProviderAuthModes.OPENAI_CHATGPT) {
                            CouiButton(
                                text = "Gérer l’utilisation",
                                enabled = enabled,
                                onClick = onManageUsage,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (loggedIn) {
                            CouiButton(
                                text = "Se déconnecter",
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
                            TextButton(text = "Ouvrir le navigateur", onClick = onOpenBrowser, modifier = Modifier.weight(1f))
                        }
                        if (state.phase in setOf(ProviderLoginPhase.Saving, ProviderLoginPhase.Authorizing, ProviderLoginPhase.SyncingModels)) {
                            TextButton(text = "Annuler", onClick = onCancel, modifier = Modifier.weight(1f))
                        }
                    } else {
                        if (needsLogin) {
                            TextButton(
                                text = if (loggedIn) "Reconnecter" else "Se connecter avec $brand",
                                enabled = enabled,
                                onClick = onLogin,
                                modifier = Modifier.weight(1f),
                            )
                        } else if (authMode == ProviderAuthModes.OPENAI_CHATGPT) {
                            TextButton(text = "Gérer l’utilisation", enabled = enabled, onClick = onManageUsage, modifier = Modifier.weight(1f))
                        }
                        if (loggedIn) {
                            TextButton(text = "Se déconnecter", enabled = enabled, onClick = onDisconnect, modifier = Modifier.weight(1f))
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
    "Aucun navigateur disponible"
} catch (_: SecurityException) {
    "无法Ouvrir le navigateur，请检查系统设置"
}
