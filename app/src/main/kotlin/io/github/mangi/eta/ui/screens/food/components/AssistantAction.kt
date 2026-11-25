package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.preview.CouiComponentPreviews
import io.github.mangi.eta.ui.screens.food.FoodComponentPreview
import io.github.mangi.eta.ui.screens.food.FoodPreviewData
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.mangi.eta.ui.screens.food.AssistantSuggestionAction
import io.github.mangi.eta.ui.screens.food.CuisineContentState
import io.github.suqi8.coui.kmp.basic.*
import io.github.suqi8.coui.kmp.squircle.SquircleShape
import io.github.suqi8.coui.kmp.theme.COUITheme

@Composable
internal fun AssistantAction(
    answer: AssistantResponse?,
    error: FoodResult.Failure?,
    working: Boolean,
    applied: List<String>,
    onAsk: (String) -> Unit,
    onApply: (AssistantProposal) -> Unit,
    onClose: () -> Unit,
) {
    var question by rememberSaveable { mutableStateOf("") }
    var confirmId by rememberSaveable { mutableStateOf<String?>(null) }
    FoodSheet(stringResource(R.string.food_assistant), onClose) {
        FoodTextField(question, { question = it.take(1200) }, label = stringResource(R.string.food_question), singleLine = false, maxLines = 5)
        FoodAction(R.string.food_send, primary = true, enabled = !working && question.isNotBlank()) { onAsk(question.trim()) }
        if (working) FoodLoading()
        FoodErrorText(error)
        answer?.let { response ->
            Text(response.answer)
            response.proposals.filterNot { it.id in applied }.forEach { proposal ->
                FoodSection {
                    Text(proposal.summary)
                    FoodAction(R.string.food_apply, enabled = !working) {
                        if (proposal.impact == ProposalImpact.MAJOR) confirmId = proposal.id else onApply(proposal)
                    }
                }
            }
        }
    }
    val proposal = answer?.proposals?.firstOrNull { it.id == confirmId }
    if (proposal != null) {
        CouiConfirmDialog(
            visible = true, title = stringResource(R.string.food_proposal_confirm), message = proposal.summary,
            confirmText = stringResource(R.string.food_confirm), onDismissRequest = { confirmId = null },
            onConfirm = { confirmId = null; onApply(proposal) },
        )
    }
}

@Composable
internal fun AssistantSuggestion(
    text: String,
    actions: List<AssistantSuggestionAction>,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    state: CuisineContentState = CuisineContentState.READY,
    pressedAction: String? = null,
) {
    val colors = COUITheme.colorScheme
    val error = state == CuisineContentState.ERROR
    Card(modifier.fillMaxWidth()) {
        if (state == CuisineContentState.LOADING) {
            CuisineLoading()
        } else {
            Row(
                Modifier.fillMaxWidth().padding(if (compact) 10.dp else 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Surface(Modifier.size(if (compact) 26.dp else 34.dp), shape = SquircleShape(12.dp),
                    color = if (error) colors.container8 else colors.primary.copy(alpha = 0.13f)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(if (error) CuisineIcons.Offline else CuisineIcons.Sparkle, null, Modifier.size(18.dp),
                            tint = if (error) colors.labelSecondary else cuisineTint(colors.primary))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (error && text.isBlank()) stringResource(R.string.food_assistant_unreachable) else text,
                        style = if (compact) COUITheme.textStyles.bodyS else COUITheme.textStyles.bodyM)
                    if (actions.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            actions.forEach { action ->
                                Button(
                                    { onAction(action.id) }, minHeight = 48.dp, minWidth = 48.dp,
                                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        color = if (pressedAction == action.id) colors.cardPressed else colors.container8,
                                    ),
                                ) {
                                    Text(action.label, style = COUITheme.textStyles.buttonM)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@CouiComponentPreviews
@Composable
private fun AssistantSuggestionPreview() = FoodComponentPreview {
    val text = FoodPreviewData.suggestion
    val actions = FoodPreviewData.suggestionActions
    AssistantSuggestion(text, actions, {})
    AssistantSuggestion(text, actions, {}, compact = true)
    AssistantSuggestion(text, actions, {}, pressedAction = actions.first().id)
    AssistantSuggestion("", emptyList(), {}, state = CuisineContentState.LOADING)
    AssistantSuggestion("", emptyList(), {}, state = CuisineContentState.ERROR)
}
