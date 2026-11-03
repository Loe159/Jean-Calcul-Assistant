package fr.loevan.jeancalcul.domain

/**
 * The single effective destination of an [AssistantSettings] configuration (CONV-01).
 *
 * The legacy schema keeps two serialized preferences ([AssistantSettings.activeModelProfileId]
 * and [AssistantSettings.activeAgentProfileId]); this type is the only effective state exposed
 * to callers: [None], [Model] or [Agent].
 */
sealed interface ActiveProfile {
    data object None : ActiveProfile

    data class Model(val configured: ConfiguredModelProfile) : ActiveProfile

    data class Agent(val configured: ConfiguredAgentProfile) : ActiveProfile

    /**
     * Identifier of the effective profile. [None] has no identifier: reading [profileId] on
     * [None] throws [IllegalStateException]. Callers must branch on the profile type first.
     */
    val profileId: String
        get() =
            when (this) {
                None -> error("No active profile.")
                is Model -> configured.profile.id
                is Agent -> configured.profile.id
            }
}

/**
 * Deterministic, platform-neutral resolution of the active profile from a full configuration.
 *
 * The logic is pure: it never opens a connection, never probes a backend and never invents a
 * profile. It reuses the activation validation of [AssistantSettingsValidator] so a profile is
 * only effective when it exists, is enabled, its connection exists, is enabled and matches the
 * expected [ProviderKind] (CONV-02, DATA-05).
 */
object ActiveProfileResolver {
    /**
     * Resolves the single effective profile of [settings].
     *
     * The result is the selection that survives [normalize]: when both legacy preferences
     * point to valid profiles the agent wins (inherited priority) and the model preference is
     * not effective; when the agent preference is invalid the valid model preference is kept;
     * when no selected preference is valid the result is [None]. A selected profile that is
     * missing, disabled, or bound to a missing, disabled or wrong-kind connection is dropped,
     * and an unselected profile never becomes effective (CONV-01, CONV-04, AUTH-06).
     */
    fun resolve(settings: AssistantSettings): ActiveProfile {
        val normalized = settings.normalizeActiveSelections()
        return when {
            normalized.activeAgentProfileId != null ->
                ActiveProfile.Agent(requireNotNull(ActiveProfileResolver.activeAgentProfile(normalized)))

            normalized.activeModelProfileId != null ->
                ActiveProfile.Model(requireNotNull(ActiveProfileResolver.activeModelProfile(normalized)))

            else -> ActiveProfile.None
        }
    }

    /** Returns [settings] with mutually exclusive active selections (CONV-01). */
    fun normalize(settings: AssistantSettings): AssistantSettings = settings.normalizeActiveSelections()

    internal fun activeModelProfile(settings: AssistantSettings): ConfiguredModelProfile? =
        settings.activeModelProfileId
            ?.let { id -> settings.modelProfiles.firstOrNull { it.profile.id == id } }
            ?.takeIf { AssistantSettingsValidator.modelActivationErrors(it, settings).isEmpty() }

    internal fun activeAgentProfile(settings: AssistantSettings): ConfiguredAgentProfile? =
        settings.activeAgentProfileId
            ?.let { id -> settings.agentProfiles.firstOrNull { it.profile.id == id } }
            ?.takeIf { AssistantSettingsValidator.agentActivationErrors(it, settings).isEmpty() }
}

/**
 * Makes the two legacy selection preferences mutually exclusive (CONV-01, DATA-05).
 *
 * - Both selected and valid: the valid agent is kept, the model preference is cleared.
 * - Invalid agent and valid model: the valid model is kept, the agent preference is cleared.
 * - No valid selection: both preferences are cleared.
 *
 * Only the two selection fields change; providers, profiles, voice and appearance settings are
 * preserved untouched. The transformation is idempotent and never invents a profile.
 */
fun AssistantSettings.normalizeActiveSelections(): AssistantSettings {
    val agent = ActiveProfileResolver.activeAgentProfile(this)
    val model = ActiveProfileResolver.activeModelProfile(this)
    val nextAgentId = agent?.profile?.id
    val nextModelId = if (agent == null) model?.profile?.id else null
    if (nextAgentId == activeAgentProfileId && nextModelId == activeModelProfileId) return this
    return copy(activeAgentProfileId = nextAgentId, activeModelProfileId = nextModelId)
}
