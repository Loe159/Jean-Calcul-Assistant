package fr.loevan.jeancalcul.domain

data class ProfileSelectionPolicy(
    val primaryProfileId: String,
    val fallbackProfileIds: List<String> = emptyList(),
) {
    init {
        require(primaryProfileId.isNotBlank())
        require(fallbackProfileIds.none(String::isBlank))
        require(primaryProfileId !in fallbackProfileIds)
        require(fallbackProfileIds.distinct().size == fallbackProfileIds.size)
    }

    internal val orderedProfileIds: List<String>
        get() = listOf(primaryProfileId) + fallbackProfileIds
}

data class ModelCandidate(
    val profile: ModelProfile,
    val capabilities: ModelCapabilities,
    val available: Boolean = true,
)

data class AgentCandidate(
    val profile: AgentProfile,
    val capabilities: AgentCapabilities,
    val available: Boolean = true,
)

enum class SelectionRejectionReason {
    MISSING,
    DISABLED,
    UNAVAILABLE,
    CAPABILITY_MISMATCH,
    FALLBACK_NOT_ALLOWED,
}

data class SelectionRejection(
    val profileId: String,
    val reason: SelectionRejectionReason,
)

sealed interface ProfileSelectionResult<out T> {
    data class Selected<T>(
        val value: T,
        val isFallback: Boolean,
    ) : ProfileSelectionResult<T>

    data class Unavailable(
        val rejections: List<SelectionRejection>,
    ) : ProfileSelectionResult<Nothing>
}

/** Deterministic, platform-neutral selection for direct models and agents. */
object ProviderSelector {
    fun selectModel(
        policy: ProfileSelectionPolicy,
        candidates: List<ModelCandidate>,
        requirements: ModelCapabilityRequirements,
    ): ProfileSelectionResult<ModelCandidate> =
        select(
            orderedIds = policy.orderedProfileIds,
            candidatesById = candidates.associateBy { it.profile.id },
            rules =
                CandidateRules(
                    isEnabled = { it.profile.enabled },
                    isAvailable = ModelCandidate::available,
                    supports = { it.capabilities.supports(requirements) },
                ),
        )

    fun selectAgent(
        policy: ProfileSelectionPolicy,
        candidates: List<AgentCandidate>,
        requirements: AgentCapabilityRequirements,
    ): ProfileSelectionResult<AgentCandidate> =
        select(
            orderedIds = policy.orderedProfileIds,
            candidatesById = candidates.associateBy { it.profile.id },
            rules =
                CandidateRules(
                    isEnabled = { it.profile.enabled },
                    isAvailable = AgentCandidate::available,
                    supports = { it.capabilities.supports(requirements) },
                ),
        )

    fun fallbackModel(
        policy: ProfileSelectionPolicy,
        currentProfileId: String,
        error: ProviderError,
        candidates: List<ModelCandidate>,
        requirements: ModelCapabilityRequirements,
    ): ProfileSelectionResult<ModelCandidate> {
        val remainingIds = policy.remainingFallbacks(currentProfileId)
        if (!error.fallbackEligible) return fallbackNotAllowed(currentProfileId)
        return select(
            orderedIds = remainingIds,
            candidatesById = candidates.associateBy { it.profile.id },
            rules =
                CandidateRules(
                    isEnabled = { it.profile.enabled },
                    isAvailable = ModelCandidate::available,
                    supports = { it.capabilities.supports(requirements) },
                ),
            fallback = true,
        )
    }

    fun fallbackAgent(
        policy: ProfileSelectionPolicy,
        currentProfileId: String,
        error: ProviderError,
        candidates: List<AgentCandidate>,
        requirements: AgentCapabilityRequirements,
    ): ProfileSelectionResult<AgentCandidate> {
        val remainingIds = policy.remainingFallbacks(currentProfileId)
        if (!error.fallbackEligible) return fallbackNotAllowed(currentProfileId)
        return select(
            orderedIds = remainingIds,
            candidatesById = candidates.associateBy { it.profile.id },
            rules =
                CandidateRules(
                    isEnabled = { it.profile.enabled },
                    isAvailable = AgentCandidate::available,
                    supports = { it.capabilities.supports(requirements) },
                ),
            fallback = true,
        )
    }

    private fun <T> select(
        orderedIds: List<String>,
        candidatesById: Map<String, T>,
        rules: CandidateRules<T>,
        fallback: Boolean = false,
    ): ProfileSelectionResult<T> {
        val rejections = mutableListOf<SelectionRejection>()
        orderedIds.forEachIndexed { index, profileId ->
            val candidate = candidatesById[profileId]
            val reason =
                when {
                    candidate == null -> SelectionRejectionReason.MISSING
                    !rules.isEnabled(candidate) -> SelectionRejectionReason.DISABLED
                    !rules.isAvailable(candidate) -> SelectionRejectionReason.UNAVAILABLE
                    !rules.supports(candidate) -> SelectionRejectionReason.CAPABILITY_MISMATCH
                    else -> return ProfileSelectionResult.Selected(candidate, fallback || index > 0)
                }
            rejections += SelectionRejection(profileId, reason)
        }
        return ProfileSelectionResult.Unavailable(rejections)
    }

    private fun fallbackNotAllowed(profileId: String): ProfileSelectionResult.Unavailable =
        ProfileSelectionResult.Unavailable(
            listOf(SelectionRejection(profileId, SelectionRejectionReason.FALLBACK_NOT_ALLOWED)),
        )
}

private data class CandidateRules<T>(
    val isEnabled: (T) -> Boolean,
    val isAvailable: (T) -> Boolean,
    val supports: (T) -> Boolean,
)

private fun ProfileSelectionPolicy.remainingFallbacks(currentProfileId: String): List<String> {
    val currentIndex = orderedProfileIds.indexOf(currentProfileId)
    return if (currentIndex < 0) emptyList() else orderedProfileIds.drop(currentIndex + 1)
}
