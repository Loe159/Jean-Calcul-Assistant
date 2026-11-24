package io.github.mangi.eta.ui.screens.food.components

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.coui.CouiConfirmDialog
import io.github.suqi8.coui.kmp.basic.Text

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
