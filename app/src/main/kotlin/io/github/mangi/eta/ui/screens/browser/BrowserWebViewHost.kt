package io.github.mangi.eta.ui.screens.browser

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import io.github.mangi.eta.agent.browser.AgentBrowserSession
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun BrowserWebViewHost(session: AgentBrowserSession, modifier: Modifier = Modifier) {
    val backgroundColor = COUITheme.colorScheme.background.toArgb()
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(backgroundColor)
                session.attachTo(this, context)
            }
        },
        onRelease = { container -> session.detachFrom(container) },
        update = { view -> view.setBackgroundColor(backgroundColor) },
        modifier = modifier.fillMaxWidth(),
    )
}
