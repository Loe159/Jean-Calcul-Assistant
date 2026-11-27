package io.github.mangi.eta.agent.confirm

import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceSettings
import io.github.mangi.eta.ui.app.AgentAppTheme
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.preview.CouiPhonePreviews
import io.github.mangi.eta.ui.preview.CouiPreviewTheme
import io.github.mangi.eta.ui.preview.CouiStressPreviews
import io.github.mangi.eta.ui.preview.previewAction

/**
 * Translucent popup asking the user to confirm one sensitive agent action. Accepter is enough for a
 * [ActionRisk.LOW] action; a [ActionRisk.HIGH] one also needs the fingerprint (or the device PIN when none is enrolled).
 */
class ActionConfirmationActivity : ComponentActivity() {
    private var requestId: String? = null
    private var request by mutableStateOf<ConfirmationRequest?>(null)
    private var verifying by mutableStateOf(false)
    private var answered = false
    private var biometricCancel: CancellationSignal? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        requestId = ActionConfirmation.requestId(intent)
        request = requestId?.let { ActionConfirmation.current()?.requestFor(it) }
        if (request == null) {
            finish()
            return
        }
        setContent {
            AgentAppTheme(appearance = AppearanceSettings(), applyInterfaceScale = false) {
                val current = request
                if (current != null) {
                    ActionConfirmationDialog(
                        request = current,
                        verifying = verifying,
                        onAccept = ::accept,
                        onDecline = { answer(ConfirmationDecision.DECLINED) },
                    )
                }
            }
        }
    }

    private fun accept() {
        val current = request ?: return
        if (current.risk == ActionRisk.LOW) answer(ConfirmationDecision.APPROVED) else verifyUser()
    }

    private fun verifyUser() {
        if (verifying) return
        val strong = BiometricManager.Authenticators.BIOMETRIC_STRONG
        val credential = BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canStrong = getSystemService(BiometricManager::class.java)
            ?.canAuthenticate(strong) == BiometricManager.BIOMETRIC_SUCCESS
        val canCredential = getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        if (!canStrong && !canCredential) {
            answer(ConfirmationDecision.DECLINED)
            return
        }
        verifying = true
        val builder = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.action_confirmation_title))
            .setDescription(request?.summary.orEmpty())
        if (canStrong) {
            builder.setAllowedAuthenticators(if (canCredential) strong or credential else strong)
            if (!canCredential) builder.setNegativeButton(getString(R.string.action_confirmation_cancel), mainExecutor) { _, _ ->
                answer(ConfirmationDecision.DECLINED)
            }
        } else {
            builder.setAllowedAuthenticators(credential)
        }
        val signal = CancellationSignal().also { biometricCancel = it }
        builder.build().authenticate(signal, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                answer(ConfirmationDecision.APPROVED)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                answer(ConfirmationDecision.DECLINED)
            }
        })
    }

    private fun answer(decision: ConfirmationDecision) {
        if (answered) return
        answered = true
        biometricCancel?.cancel()
        requestId?.let { ActionConfirmation.current()?.resolve(it, decision) }
        finish()
    }

    override fun onDestroy() {
        if (!answered && isFinishing) {
            answered = true
            requestId?.let { ActionConfirmation.current()?.resolve(it, ConfirmationDecision.DECLINED) }
        }
        super.onDestroy()
    }

}

@androidx.compose.runtime.Composable
internal fun ActionConfirmationDialog(
    request: ConfirmationRequest,
    verifying: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val high = request.risk == ActionRisk.HIGH
    val message = if (high) {
        request.summary + "\n\n" + stringResource(R.string.action_confirmation_fingerprint)
    } else {
        request.summary
    }
    CouiScaffoldPage(
        title = stringResource(R.string.action_confirmation_title),
        onBack = onDecline,
        compactTitle = true,
        overlay = {
            CouiConfirmDialog(
                visible = true,
                title = stringResource(R.string.action_confirmation_title),
                message = message,
                confirmText = stringResource(R.string.action_confirmation_accept),
                onConfirm = onAccept,
                onDismissRequest = onDecline,
                cancelText = stringResource(R.string.action_confirmation_decline),
                confirmEnabled = !verifying,
                destructive = high,
            )
        },
    ) {}
}

@CouiPhonePreviews
@CouiStressPreviews
@androidx.compose.runtime.Composable
private fun ActionConfirmationLowPreview() = CouiPreviewTheme {
    ActionConfirmationDialog(
        request = ConfirmationRequest("Créer l'événement « Dentiste » mardi 2 déc. 14:00", ActionRisk.LOW),
        verifying = false,
        onAccept = previewAction("onAccept"),
        onDecline = previewAction("onDecline"),
    )
}

@CouiPhonePreviews
@CouiStressPreviews
@androidx.compose.runtime.Composable
private fun ActionConfirmationHighPreview() = CouiPreviewTheme {
    ActionConfirmationDialog(
        request = ConfirmationRequest("Supprimer l'alarme de 07:00", ActionRisk.HIGH),
        verifying = false,
        onAccept = previewAction("onAccept"),
        onDecline = previewAction("onDecline"),
    )
}
