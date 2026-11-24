package io.github.mangi.eta.ui.screens.food

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.components.coui.CouiTopAppBarIconButton
import io.github.mangi.eta.ui.screens.food.components.*
import io.github.suqi8.coui.kmp.basic.CardListGroup
import io.github.suqi8.coui.kmp.basic.Text
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.extended.More
import io.github.suqi8.coui.kmp.preference.ArrowPreference
import io.github.suqi8.coui.kmp.preference.SwitchPreference
import io.github.suqi8.coui.kmp.theme.COUITheme
import kotlinx.coroutines.launch

@Composable
internal fun CookingSessionDestination(sessionId: String, onBack: () -> Unit) {
    val repo = foodRuntime().repository
    var current by remember(sessionId) { mutableStateOf<CookingSession?>(null) }
    var loaded by remember(sessionId) { mutableStateOf(false) }
    LaunchedEffect(repo, sessionId) {
        repo.session(sessionId).collect {
            current = it
            loaded = true
        }
    }
    val session = current
    when {
        session == null -> FoodPage(stringResource(R.string.food_cooking_session), onBack) {
            if (loaded) ListEmptyState(stringResource(R.string.food_cooking_session_unavailable)) else FoodLoading()
        }
        session.status == CookingStatus.COMPLETED -> {
            val result by remember(repo, sessionId) { repo.completionResult(sessionId) }.collectAsState(initial = null)
            FoodPage(stringResource(R.string.food_completion), onBack) { CompletionStatus(session, result) }
        }
        session.status == CookingStatus.ACTIVE -> ActiveCookingSession(session, onBack)
        else -> FoodPage(stringResource(R.string.food_cooking_session), onBack) {
            ListEmptyState(stringResource(R.string.food_cooking_session_unavailable))
        }
    }
}

private enum class CookingSheet { INGREDIENTS, OVERVIEW, ASSISTANT, OPTIONS, COMPLETION }

@Composable
private fun ActiveCookingSession(session: CookingSession, onBack: () -> Unit) {
    val runtime = foodRuntime()
    val repo = runtime.repository
    val manager = runtime.timers
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exact by manager.exactAlarmsAvailable.collectAsState()
    val progress = session.progress()
    val currentStep = progress.step
    var sheet by rememberSaveable { mutableStateOf<CookingSheet?>(null) }
    var busy by remember { mutableStateOf(false) }
    var largeText by rememberSaveable { mutableStateOf(false) }
    var keepAwake by rememberSaveable { mutableStateOf(false) }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var notificationsDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsDenied = !granted
    }
    val activity = remember(context) { context.activity() }
    LifecycleResumeEffect(activity, keepAwake) {
        val window = if (keepAwake) activity?.window else null
        val wasAwake = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onPauseOrDispose {
            if (!wasAwake) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LifecycleResumeEffect(manager) {
        val refresh = scope.launch { manager.reschedule() }
        onPauseOrDispose { refresh.cancel() }
    }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                action()
            } finally {
                busy = false
            }
        }
    }

    val scroll = rememberScrollState()
    LaunchedEffect(currentStep?.id) { scroll.scrollTo(0) }
    val overlay: @Composable () -> Unit = {
        val closeSheet = { sheet = null }
        when (sheet) {
            CookingSheet.INGREDIENTS -> FoodSheet(stringResource(R.string.food_ingredients), closeSheet) {
                IngredientChecklist(session.recipeSnapshot.portions(session.servings, session.ingredientOverrides), showStock = false)
            }
            CookingSheet.OVERVIEW -> FoodSheet(stringResource(R.string.food_steps), closeSheet) {
                CardListGroup {
                    progress.steps.forEachIndexed { index, step ->
                        item {
                            val position = stringResource(R.string.food_step_position, index + 1, progress.steps.size)
                            val status = when (step.id) {
                                in session.skippedStepIds -> stringResource(R.string.food_step_skipped)
                                in session.completedStepIds -> stringResource(R.string.food_step_done)
                                else -> null
                            }
                            ArrowPreference(
                                title = step.title,
                                summary = listOfNotNull(position, status).joinToString(" · "),
                                enabled = !busy,
                                onClick = {
                                    runAction { repo.jumpToStep(session.id, step.id) }
                                    closeSheet()
                                },
                            )
                        }
                    }
                }
            }
            CookingSheet.ASSISTANT -> CookingAssistant(session, closeSheet)
            CookingSheet.OPTIONS -> FoodSheet(stringResource(R.string.food_cooking_options), closeSheet) {
                CardListGroup {
                    item {
                        SwitchPreference(title = stringResource(R.string.food_keep_awake), checked = keepAwake, onCheckedChange = { keepAwake = it })
                    }
                    item {
                        SwitchPreference(title = stringResource(R.string.food_large_text), checked = largeText, onCheckedChange = { largeText = it })
                    }
                    if (currentStep != null) item {
                        ArrowPreference(
                            title = stringResource(R.string.food_step_skip),
                            enabled = !busy,
                            onClick = {
                                runAction { repo.skipStep(session.id) }
                                closeSheet()
                            },
                        )
                    }
                }
            }
            CookingSheet.COMPLETION -> {
                val stock by remember(repo) { repo.pantry() }.collectAsState(initial = null)
                LaunchedEffect(repo) { repo.refreshPantry() }
                FoodSheet(stringResource(R.string.food_completion), { if (!busy) closeSheet() }) {
                    RecipeCompletion(session, stock?.value?.locations.orEmpty(), busy) { draft ->
                        runAction {
                            repo.completeSession(session.id, draft.servingsMade, draft.rating, draft.consumptions, draft.leftovers)
                            closeSheet()
                        }
                    }
                }
            }
            null -> Unit
        }
    }

    FoodPage(
        session.recipeSnapshot.title, onBack, scrollState = scroll, compactTitle = true, overlay = overlay,
        actions = {
            CouiTopAppBarIconButton(
                COUIIcons.More, stringResource(R.string.food_cooking_options), { sheet = CookingSheet.OPTIONS },
                Modifier.size(48.dp),
            )
        },
    ) {
        if (currentStep != null) {
            CookingProgress(
                progress, busy,
                onPrevious = { runAction { repo.previousStep(session.id) } },
                onNext = { runAction { repo.nextStep(session.id) } },
            )
            FoodSection {
                FoodHeading(currentStep.title)
                Text(FoodQuantities.renderStep(session, currentStep),
                    style = if (largeText) COUITheme.textStyles.headlineM else COUITheme.textStyles.bodyXL)
                StepIngredientList(stepPortions(session, currentStep))
                RecipePicture(currentStep.image)
                currentStep.durationSeconds?.let { FoodSupporting(stringResource(R.string.food_minutes, (it + 59) / 60)) }
                currentStep.temperature?.value?.let { FoodSupporting(stringResource(R.string.food_temperature, it.toString())) }
                currentStep.tips.forEach { FoodSupporting(it) }
                currentStep.timerSuggestion?.let { suggestion ->
                    FoodButton(
                        stringResource(R.string.food_timer_start, stringResource(R.string.food_minutes, (suggestion.durationSeconds + 59) / 60)),
                        enabled = !busy, onClick = {
                            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                if (!permissionAsked && session.timers.isEmpty()) {
                                    permissionAsked = true
                                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else notificationsDenied = true
                            }
                            runAction { manager.start(session.id, currentStep.id, suggestion.label, suggestion.durationSeconds) }
                        },
                    )
                }
            }
            StepActions(
                progress, busy, progress.finished == progress.steps.size,
                onDone = { runAction { repo.completeStep(session.id) } },
                onFinish = { sheet = CookingSheet.COMPLETION },
            )
        }
        val timers = session.activeTimers()
        if (timers.isNotEmpty()) {
            FoodSection {
                TimerStack(timers, TimerActions(
                    pause = { scope.launch { manager.pause(it) } },
                    resume = { scope.launch { manager.resume(it) } },
                    add = { id, minutes -> scope.launch { manager.addTime(id, minutes) } },
                    cancel = { scope.launch { manager.cancel(it) } },
                ))
            }
        }
        if (!exact) {
            FoodSection {
                FoodSupporting(stringResource(R.string.food_exact_alarms))
                FoodAction(R.string.food_allow_exact) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(intent) }
                }
            }
        }
        if (notificationsDenied) FoodSupporting(stringResource(R.string.food_notifications_denied))
        CardListGroup {
            item { ArrowPreference(title = stringResource(R.string.food_ingredients), onClick = { sheet = CookingSheet.INGREDIENTS }) }
            item { ArrowPreference(title = stringResource(R.string.food_steps), onClick = { sheet = CookingSheet.OVERVIEW }) }
            item { ArrowPreference(title = stringResource(R.string.food_assistant), onClick = { sheet = CookingSheet.ASSISTANT }) }
        }
    }
}

@Composable
private fun CookingAssistant(session: CookingSession, onClose: () -> Unit) {
    val repo = foodRuntime().repository
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf<AssistantResponse?>(null) }
    var error by remember { mutableStateOf<FoodResult.Failure?>(null) }
    var working by remember { mutableStateOf(false) }
    var applied by remember { mutableStateOf(emptyList<String>()) }
    AssistantAction(answer, error, working, applied, onAsk = { question ->
        working = true
        answer = null
        error = null
        applied = emptyList()
        scope.launch {
            when (val response = repo.assistant(AssistantRequest(requestId(), question, session))) {
                is FoodResult.Success -> answer = response.value
                is FoodResult.Failure -> error = response
            }
            working = false
        }
    }, onApply = { proposal ->
        val response = answer
        if (response != null && !working) {
            working = true
            scope.launch {
                repo.applyProposal(session.id, response.requestId, proposal)
                applied = applied + proposal.id
                working = false
            }
        }
    }, onClose = onClose)
}

private fun stepPortions(session: CookingSession, step: RecipeStep): List<IngredientPortion> {
    val ingredients = session.recipeSnapshot.portions(session.servings, session.ingredientOverrides).associateBy { it.ingredient.id }
    return step.ingredientRefs.mapNotNull { ref ->
        val line = ingredients[ref.ingredientId] ?: return@mapNotNull null
        line.copy(quantity = line.quantity.copy(amount = line.quantity.amount?.times(ref.fraction)))
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
