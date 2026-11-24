package io.github.mangi.eta.food

import kotlinx.serialization.Serializable

@Serializable
internal data class AppliedProposal(val requestId: String, val proposal: AssistantProposal)

internal fun localProposals(json: String?): List<AppliedProposal> =
    json?.let { FoodContract.json.decodeFromString<List<AppliedProposal>>(it) }.orEmpty()

internal fun CookingSession.withLocalProposals(json: String?): CookingSession =
    copy(recipeSnapshot = localProposals(json).fold(recipeSnapshot) { recipe, applied ->
        recipe.withProposal(applied.proposal)
    })

private fun RecipeDefinition.withProposal(proposal: AssistantProposal): RecipeDefinition = when (proposal) {
    is AssistantProposal.ReplaceIngredient -> copy(ingredients = ingredients.map { ingredient ->
        if (ingredient.id != proposal.patch.ingredientId) ingredient
        else ingredient.copy(name = proposal.patch.newName, grocyProductId = proposal.patch.grocyProductId)
    })
    is AssistantProposal.AdjustDuration -> changeStep(proposal.patch.stepId) { step ->
        step.copy(
            durationSeconds = proposal.patch.durationSeconds,
            timerSuggestion = step.timerSuggestion?.copy(durationSeconds = proposal.patch.durationSeconds),
        )
    }
    is AssistantProposal.AdjustTemperature -> changeStep(proposal.patch.stepId) {
        it.copy(temperature = proposal.patch.temperature)
    }
    is AssistantProposal.AddStepNote -> changeStep(proposal.patch.stepId) {
        it.copy(tips = it.tips + proposal.patch.note)
    }
    else -> this
}

private fun RecipeDefinition.changeStep(id: String, transform: (RecipeStep) -> RecipeStep) =
    copy(steps = steps.map { if (it.id == id) transform(it) else it })

internal fun CookingSession.applyAssistantProposal(proposal: AssistantProposal): CookingSession = when (proposal) {
    is AssistantProposal.ReplaceIngredient -> overrideQuantity(proposal.patch.ingredientId, proposal.patch.quantity)
    is AssistantProposal.ChangeQuantity -> overrideQuantity(proposal.patch.ingredientId, proposal.patch.quantity)
    is AssistantProposal.SkipStep -> {
        val id = proposal.patch.stepId
        val steps = recipeSnapshot.steps.sortedBy { it.order }
        val index = steps.indexOfFirst { it.id == id }
        require(index >= 0)
        copy(
            currentStepId = if (currentStepId == id) steps.getOrNull(index + 1)?.id ?: id else currentStepId,
            skippedStepIds = (skippedStepIds + id).distinct(),
            completedStepIds = completedStepIds - id,
        )
    }
    else -> this
}

private fun CookingSession.overrideQuantity(id: String, measured: MeasuredQuantity): CookingSession {
    require(recipeSnapshot.ingredients.any { it.id == id } && measured.amount.isFinite() && measured.amount > 0)
    return copy(ingredientOverrides = ingredientOverrides.filterNot { it.ingredientId == id } +
        IngredientOverride(id, Quantity(measured.amount, measured.unit)))
}
