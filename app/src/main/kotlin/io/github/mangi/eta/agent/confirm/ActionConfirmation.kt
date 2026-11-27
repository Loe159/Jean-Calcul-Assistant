package io.github.mangi.eta.agent.confirm

import android.content.Context
import android.content.Intent

/** The app-wide confirmation broker, wired to [ActionConfirmationActivity]. */
internal object ActionConfirmation {
    private const val EXTRA_REQUEST_ID = "io.github.mangi.eta.extra.CONFIRMATION_REQUEST_ID"

    @Volatile private var instance: ActionConfirmationBroker? = null

    fun broker(context: Context): ActionConfirmationBroker {
        val app = context.applicationContext
        return instance ?: synchronized(this) {
            instance ?: ActionConfirmationBroker { id -> open(app, id) }.also { instance = it }
        }
    }

    /** The broker the popup answers to; null before the first confirmation was requested. */
    fun current(): ActionConfirmationBroker? = instance

    fun requestId(intent: Intent): String? = intent.getStringExtra(EXTRA_REQUEST_ID)

    private fun open(context: Context, requestId: String): Boolean = runCatching {
        context.startActivity(
            Intent(context, ActionConfirmationActivity::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }.isSuccess
}
