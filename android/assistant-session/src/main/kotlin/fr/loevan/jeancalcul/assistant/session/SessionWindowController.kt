package fr.loevan.jeancalcul.assistant.session

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.ViewGroup
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/** Configures the session window without coupling it to the voice-session lifecycle. */
internal class SessionWindowController(
    private val onBackInvoked: () -> Unit,
) {
    private var predictiveBackDispatcher: OnBackInvokedDispatcher? = null
    private var predictiveBackCallback: OnBackInvokedCallback? = null

    fun prepare(dialog: Dialog?) {
        configure(dialog)
        registerPredictiveBackCallback(dialog)
    }

    fun reconfigure(dialog: Dialog?) = configure(dialog)

    fun release() {
        predictiveBackCallback?.let { callback ->
            predictiveBackDispatcher?.unregisterOnBackInvokedCallback(callback)
        }
        predictiveBackDispatcher = null
        predictiveBackCallback = null
    }

    private fun configure(dialog: Dialog?) {
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0f)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            clearFlags(
                WindowManager.LayoutParams.FLAG_DIM_BEHIND or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setBackgroundBlurRadius(BACKGROUND_BLUR_RADIUS_PX)
            }
        }
    }

    private fun registerPredictiveBackCallback(dialog: Dialog?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val dispatcher = dialog?.window?.onBackInvokedDispatcher ?: return
        val callback = OnBackInvokedCallback(onBackInvoked)
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
        predictiveBackDispatcher = dispatcher
        predictiveBackCallback = callback
    }

    private companion object {
        const val BACKGROUND_BLUR_RADIUS_PX = 32
    }
}
