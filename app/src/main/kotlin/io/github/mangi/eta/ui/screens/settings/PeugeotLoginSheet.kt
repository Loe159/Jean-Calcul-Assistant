package io.github.mangi.eta.ui.screens.settings

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.mangi.eta.data.car.MyPeugeotCarSource
import io.github.mangi.eta.data.car.PeugeotLogin
import io.github.mangi.eta.data.car.carFetchMessage
import io.github.mangi.eta.data.car.carInstallMessage
import io.github.mangi.eta.data.car.peugeotRedirectUri
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.Close
import io.github.suqi8.coui.kmp.overlay.OverlayBottomSheet
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

@Composable
internal fun PeugeotLoginSheet(source: MyPeugeotCarSource, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var login by remember(source) { mutableStateOf<PeugeotLogin?>(null) }
    var exchanging by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    LaunchedEffect(source) {
        try {
            login = source.beginLogin()
            if (login == null) error = carInstallMessage
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = carFetchMessage(failure)
        }
    }
    OverlayBottomSheet(
        show = true,
        title = "Connecter MyPeugeot",
        onDismissRequest = onDismiss,
        startAction = { CouiTopAppBarIconButton(COUIIcons.Basic.Close, "Fermer", onDismiss) },
        showDragHandle = true,
    ) {
        val attempt = login
        Column(Modifier.fillMaxWidth().imePadding()) {
            if (attempt == null || exchanging || error != null) {
                Text(
                    error ?: if (exchanging) "Connexion en cours…" else "Chargement…",
                    modifier = Modifier.padding(20.dp),
                    color = COUITheme.colorScheme.labelPrimary,
                    style = COUITheme.textStyles.bodyM,
                )
            } else {
                PeugeotLoginView(
                    attempt = attempt,
                    onFailure = { if (!exchanging) error = it },
                    onCode = { state, code ->
                        if (!exchanging && error == null) {
                            exchanging = true
                            scope.launch {
                                try {
                                    source.finishLogin(attempt, state, code)
                                    onDismiss()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    error = carFetchMessage(failure)
                                } finally {
                                    exchanging = false
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

private val peugeotWebHosts = setOf("idpcvs.peugeot.com", "id-dcr.peugeot.com")

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PeugeotLoginView(
    attempt: PeugeotLogin,
    onFailure: (String) -> Unit,
    onCode: (String, String) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(560.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val url = request.url
                        if (url.scheme.equals("mymap", ignoreCase = true)) {
                            if (request.isForMainFrame && url.toString().substringBefore('?') == peugeotRedirectUri) {
                                val state = url.getQueryParameters("state").singleOrNull()
                                val code = url.getQueryParameters("code").singleOrNull()
                                if (!attempt.acceptsState(state) || code.isNullOrBlank()) {
                                    onFailure("La connexion MyPeugeot a été interrompue.")
                                } else {
                                    onCode(requireNotNull(state), code)
                                }
                            }
                            return true
                        }
                        return !url.isPeugeotPage()
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) onFailure("Le site MyPeugeot est indisponible.")
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                        if (request.isForMainFrame) onFailure("Le site MyPeugeot est indisponible.")
                    }
                }
                loadUrl(attempt.url.toString())
            }
        },
        onRelease = {
            it.stopLoading()
            it.clearHistory()
            it.destroy()
        },
    )
}

private fun Uri.isPeugeotPage(): Boolean =
    scheme == "https" && host in peugeotWebHosts && (port == -1 || port == 443) && userInfo == null

internal suspend fun clearPeugeotWebData(context: Context) {
    val cleaner = WebView(context)
    try {
        cleaner.clearCache(true)
    } finally {
        cleaner.destroy()
    }
    suspendCancellableCoroutine { continuation ->
        CookieManager.getInstance().removeAllCookies {
            CookieManager.getInstance().flush()
            continuation.resume(Unit)
        }
    }
}
