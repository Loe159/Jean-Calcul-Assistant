package io.github.mangi.eta.agent.development

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.WindowManager
import io.github.mangi.eta.BuildConfig
import io.github.mangi.eta.ui.MainActivity
import io.github.mangi.eta.ui.navigation.AppRoute
import java.lang.ref.WeakReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

/** Captures only our own Activity, before the assistant overlay appears. No accessibility/root needed. */
internal object OwnAppScreenContext {
    private var activity = WeakReference<Activity>(null)
    private var screen = "Home"
    private var sensitive = false
    fun started(value: Activity) { activity = WeakReference(value) }
    fun stopped(value: Activity) { if (activity.get() === value) activity.clear() }
    fun screen(route: AppRoute) {
        screen = route.javaClass.simpleName.take(100)
        sensitive = route is AppRoute.AppImprovements || route is AppRoute.ModelProviderDetail ||
            route is AppRoute.ModelProviderNew || route is AppRoute.McpServerDetail ||
            route is AppRoute.SpeechRecognition || route is AppRoute.SpeechSynthesis || route is AppRoute.SpeechOss
    }
    data class Capture(
        val screen: String,
        val text: String,
        val bitmap: Bitmap?,
        val sensitive: Boolean,
        val ready: Deferred<Unit>? = null,
    ) {
        private var releaseScheduled = false
        // PixelCopy owns the destination until its callback, even if the assistant closes first.
        @Synchronized
        fun release() {
            if (releaseScheduled) return
            releaseScheduled = true
            val recycle = { if (bitmap != null && !bitmap.isRecycled) bitmap.recycle() }
            if (ready == null) recycle() else ready.invokeOnCompletion { recycle() }
        }
    }
    fun capture(allowText: Boolean, allowScreenshot: Boolean): Capture? {
        val current = activity.get()?.takeIf { !it.isFinishing && !it.isDestroyed } ?: return null
        val currentScreen = if (current is MainActivity) screen else "Browser"
        if (!allowText && !allowScreenshot) return null
        val private = (current is MainActivity && sensitive) || current.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        val text = "Jean-Calcul ${BuildConfig.VERSION_NAME}; screen: $currentScreen; source revision: ${BuildConfig.SOURCE_COMMIT}." +
            if (private) " Credential page: screen contents withheld." else " The user is viewing Jean-Calcul itself."
        var ready: CompletableDeferred<Unit>? = null
        val bitmap = if (allowScreenshot && !private) runCatching {
            val view = current.window.decorView
            require(view.width > 0 && view.height > 0)
            val scale = minOf(1f, 1400f / maxOf(view.width, view.height))
            val image = Bitmap.createBitmap((view.width * scale).toInt().coerceAtLeast(1), (view.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val copied = CompletableDeferred<Unit>()
            ready = copied
            try {
                PixelCopy.request(current.window, image, { result ->
                    if (result == PixelCopy.SUCCESS) copied.complete(Unit)
                    else copied.completeExceptionally(IllegalStateException("Window capture failed: $result"))
                }, Handler(Looper.getMainLooper()))
                image
            } catch (error: Exception) {
                copied.completeExceptionally(error)
                image.recycle()
                throw error
            }
        }.getOrNull() else null
        return Capture(currentScreen, text, bitmap, private, ready)
    }
}
