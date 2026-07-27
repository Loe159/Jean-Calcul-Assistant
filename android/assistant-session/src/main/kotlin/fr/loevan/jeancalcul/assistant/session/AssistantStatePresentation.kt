package fr.loevan.jeancalcul.assistant.session

import fr.loevan.jeancalcul.domain.AssistantState
import fr.loevan.jeancalcul.ui.ActionCardState
import fr.loevan.jeancalcul.ui.GradientOrbState
import fr.loevan.jeancalcul.ui.PrivacyIndicatorState
import fr.loevan.jeancalcul.ui.StatusBadgeState
import fr.loevan.jeancalcul.ui.VoiceWaveState

internal data class AssistantStatePresentation(
    val title: String,
    val orbState: GradientOrbState,
    val waveState: VoiceWaveState,
    val badgeState: StatusBadgeState,
    val microphoneState: PrivacyIndicatorState,
    val processingState: PrivacyIndicatorState,
    val activeGlow: Boolean,
    val actionState: ActionCardState? = null,
    val actionSummary: String? = null,
)

internal fun VoiceSessionState.presentation(): AssistantStatePresentation {
    StaticPresentations[assistantState]?.let { return it }
    return when (val current = assistantState) {
        is AssistantState.ProposingAction -> actionPresentation(current.summary, ActionCardState.Proposed)
        is AssistantState.WaitingApproval ->
            actionPresentation(current.summary, ActionCardState.ConfirmationRequired)

        is AssistantState.Executing -> actionPresentation(current.summary, ActionCardState.Executing)
        is AssistantState.Speaking -> SpeakingPresentation
        is AssistantState.Cancelled -> CancelledPresentation
        is AssistantState.Error -> errorPresentation()
        else -> error("Missing assistant presentation for $current")
    }
}

private fun VoiceSessionState.errorPresentation() =
    AssistantStatePresentation(
        title = if (microphonePermissionRequired) "Microphone requis" else "Assistant indisponible",
        orbState = if (microphonePermissionRequired) GradientOrbState.Offline else GradientOrbState.Error,
        waveState = VoiceWaveState.MicrophoneUnavailable,
        badgeState = if (microphonePermissionRequired) StatusBadgeState.Permission else StatusBadgeState.Error,
        microphoneState = PrivacyIndicatorState.MicrophoneInactive,
        processingState = PrivacyIndicatorState.DestinationVisible,
        activeGlow = false,
    )

private fun actionPresentation(
    summary: String,
    state: ActionCardState,
): AssistantStatePresentation {
    val waiting = state == ActionCardState.ConfirmationRequired
    val title =
        when {
            waiting -> "Confirmation requise"
            state == ActionCardState.Executing -> "Action en cours"
            else -> "Action proposee"
        }
    return AssistantStatePresentation(
        title = title,
        orbState =
            when (state) {
                ActionCardState.ConfirmationRequired -> GradientOrbState.WaitingApproval
                ActionCardState.Executing -> GradientOrbState.Executing
                else -> GradientOrbState.ProposingAction
            },
        waveState = if (state == ActionCardState.Executing) VoiceWaveState.Static else VoiceWaveState.Silence,
        badgeState = if (state == ActionCardState.Executing) StatusBadgeState.Active else StatusBadgeState.Warning,
        microphoneState = PrivacyIndicatorState.MicrophoneInactive,
        processingState = PrivacyIndicatorState.LocalProcessing,
        activeGlow = true,
        actionState = state,
        actionSummary = summary,
    )
}

private val StaticPresentations =
    mapOf(
        AssistantState.Idle to
            AssistantStatePresentation(
                "Assistant pret",
                GradientOrbState.Idle,
                VoiceWaveState.Waiting,
                StatusBadgeState.Inactive,
                PrivacyIndicatorState.MicrophoneInactive,
                PrivacyIndicatorState.DestinationVisible,
                activeGlow = false,
            ),
        AssistantState.Invoked to
            AssistantStatePresentation(
                "Assistant invoque",
                GradientOrbState.Invoked,
                VoiceWaveState.Waiting,
                StatusBadgeState.Available,
                PrivacyIndicatorState.MicrophoneInactive,
                PrivacyIndicatorState.DestinationVisible,
                activeGlow = false,
            ),
        AssistantState.Listening to
            AssistantStatePresentation(
                "Je vous ecoute...",
                GradientOrbState.Listening,
                VoiceWaveState.Listening,
                StatusBadgeState.Active,
                PrivacyIndicatorState.MicrophoneActive,
                PrivacyIndicatorState.LocalProcessing,
                activeGlow = true,
            ),
        AssistantState.Transcribing to
            AssistantStatePresentation(
                "Transcription en cours",
                GradientOrbState.Transcribing,
                VoiceWaveState.Static,
                StatusBadgeState.Active,
                PrivacyIndicatorState.MicrophoneInactive,
                PrivacyIndicatorState.LocalProcessing,
                activeGlow = true,
            ),
        AssistantState.Thinking to
            AssistantStatePresentation(
                "Preparation de la reponse",
                GradientOrbState.Thinking,
                VoiceWaveState.Static,
                StatusBadgeState.Active,
                PrivacyIndicatorState.MicrophoneInactive,
                PrivacyIndicatorState.DestinationVisible,
                activeGlow = true,
            ),
        AssistantState.Completed to
            AssistantStatePresentation(
                "Interaction terminee",
                GradientOrbState.Completed,
                VoiceWaveState.Waiting,
                StatusBadgeState.Success,
                PrivacyIndicatorState.MicrophoneInactive,
                PrivacyIndicatorState.DestinationVisible,
                activeGlow = false,
            ),
    )

private val SpeakingPresentation =
    AssistantStatePresentation(
        "Reponse vocale",
        GradientOrbState.Speaking,
        VoiceWaveState.Speaking,
        StatusBadgeState.Active,
        PrivacyIndicatorState.MicrophoneInactive,
        PrivacyIndicatorState.LocalProcessing,
        activeGlow = true,
    )

private val CancelledPresentation =
    AssistantStatePresentation(
        "Interaction annulee",
        GradientOrbState.Cancelled,
        VoiceWaveState.Waiting,
        StatusBadgeState.Inactive,
        PrivacyIndicatorState.MicrophoneInactive,
        PrivacyIndicatorState.DestinationVisible,
        activeGlow = false,
    )

internal fun AssistantState.isInterruptible(): Boolean =
    this == AssistantState.Listening ||
        this == AssistantState.Transcribing ||
        this == AssistantState.Thinking ||
        this is AssistantState.ProposingAction ||
        this is AssistantState.WaitingApproval ||
        this is AssistantState.Executing ||
        this is AssistantState.Speaking
