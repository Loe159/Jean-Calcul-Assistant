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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import io.github.mangi.eta.R
import io.github.mangi.eta.food.*
import io.github.mangi.eta.ui.components.ListEmptyState
import io.github.mangi.eta.ui.screens.food.components.*
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
internal fun CookingSessionDestination(
    sessionId: String,
    onBack: () -> Unit,
    onCookLater: (String) -> Unit = {},
) {
    val repo = foodRuntime().repository
    var current by remember(sessionId) { mutableStateOf<CookingSession?>(null) }
    var loaded by remember(sessionId) { mutableStateOf(false) }
    var showFinish by rememberSaveable(sessionId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(repo, sessionId) {
        repo.session(sessionId).collect {
            current = it
            loaded = true
        }
    }
    val session = current
    when {
        session == null || session.status == CookingStatus.ABANDONED ->
            FoodPage(stringResource(R.string.food_cooking_session), onBack) {
                if (loaded) ListEmptyState(stringResource(R.string.food_cooking_session_unavailable)) else FoodLoading()
            }
        showFinish || session.status == CookingStatus.COMPLETED ->
            CookingCompletionDestination(session, onBack, onCookLater)
        else -> ActiveCookingSession(session, onBack, onFinish = { showFinish = true }, onAbandon = {
            scope.launch {
                repo.abandonSession(session.id)
                onBack()
            }
        })
    }
}

@Composable
private fun ActiveCookingSession(session: CookingSession, onBack: () -> Unit, onFinish: () -> Unit, onAbandon: () -> Unit) {
    val runtime = foodRuntime()
    val repo = runtime.repository
    val manager = runtime.timers
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exact by manager.exactAlarmsAvailable.collectAsState()
    val progress = remember(session) { session.progress() }
    val currentStep = progress.step
    val recipe = session.recipeSnapshot
    val step = remember(session, currentStep) {
        currentStep?.let { recipe.toCookingStepUi(it, progress.index + 1, session.servings, session.ingredientOverrides) }
    }
    val checkedKey = "checked:${session.id}"
    var checked by remember(session.id) {
        mutableStateOf(runtime.cookingPreferences.getStringSet(checkedKey, emptySet()).orEmpty().toSet())
    }
    var sheet by rememberSaveable { mutableStateOf<CookingSheet?>(null) }
    var ingredientId by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var drawerExpanded by rememberSaveable { mutableStateOf(false) }
    val textKey = "large-text:${session.id}"
    val awakeKey = "keep-awake:${session.id}"
    var largeText by remember(session.id) { mutableStateOf(runtime.cookingPreferences.getBoolean(textKey, false)) }
    var keepAwake by remember(session.id) { mutableStateOf(runtime.cookingPreferences.getBoolean(awakeKey, false)) }
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
    LifecycleResumeEffect(manager, session.id) {
        val refresh = scope.launch {
            manager.reschedule()
            repo.session(session.id).collectLatest { current ->
                val running = current?.timers.orEmpty().filter { it.state == TimerState.RUNNING }
                val next = running.minByOrNull { it.endsAt.orEmpty() } ?: return@collectLatest
                val deadline = next.endsAt?.let { Instant.parse(it).toEpochMilli() } ?: return@collectLatest
                delay((deadline - System.currentTimeMillis()).coerceAtLeast(0))
                runtime.launch { manager.alarmDelivered(next.id) }
            }
        }
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

    fun startTimer(stepId: String?, label: String, seconds: Int) {
        runAction {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (!permissionAsked) {
                    permissionAsked = true
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else notificationsDenied = true
            }
            manager.start(session.id, stepId, label, seconds)
        }
    }

    val closeSheet = { sheet = null }
    NavigationBackHandler(
        rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = sheet != null,
        onBackCompleted = closeSheet,
    )
    val now = rememberCookingClock(session)
    val stepsById = remember(progress.steps) { progress.steps.mapIndexed { index, value -> value.id to index + 1 }.toMap() }
    val timers = session.activeTimers().map { it.toTimerUi(now, stepsById[it.stepId]) }
    val state = CookingScreenState(
        title = recipe.title, index = progress.index, count = progress.steps.size, step = step,
        completed = progress.steps.indices.filterTo(mutableSetOf()) {
            progress.steps[it].id in session.completedStepIds
        },
        timers = timers,
        drawerExpanded = drawerExpanded,
        largeText = largeText,
        busy = busy,
        exactAlarms = exact,
        notificationsDenied = notificationsDenied,
        stepTimer = timers.lastOrNull { it.stepNumber == progress.index + 1 },
    )
    val customTimerLabel = stringResource(R.string.food_custom_timer)
    CookingScreen(
        state = state, onMinimize = onBack,
        onPrevious = { if (progress.index > 0) runAction { repo.previousStep(session.id) } },
        onNext = {
            if (currentStep != null) runAction {
                repo.completeStep(session.id)
                if (state.advanceToFinish()) onFinish()
            }
        },
        onSheet = { sheet = it },
        onIngredient = { ingredientId = it; sheet = CookingSheet.INGREDIENT },
        onTimerDrawer = { drawerExpanded = it },
        onStartTimer = {
            currentStep?.timerSuggestion?.let { startTimer(currentStep.id, it.label, it.durationSeconds) }
        },
        onNewTimer = { startTimer(null, customTimerLabel, it * 60) },
        onTextSize = {
            largeText = !largeText
            runtime.cookingPreferences.edit().putBoolean(textKey, largeText).apply()
        },
        onAllowExact = {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            runCatching { context.startActivity(intent) }
        },
        timerActions = TimerActions(
            pause = { scope.launch { manager.pause(it) } },
            resume = { scope.launch { manager.resume(it) } },
            add = { id, minutes -> scope.launch { manager.addTime(id, minutes) } },
            cancel = { scope.launch { manager.cancel(it) } },
        ),
    ) {
        when (sheet) {
            CookingSheet.INGREDIENTS -> CookingIngredientsSheet(
                IngredientChecklistUi(
                    recipe.portions(session.servings, session.ingredientOverrides).map { portion ->
                        IngredientRowUi(portion, checked = portion.ingredient.id in checked)
                    }, session.servings, stockKnown = false,
                ),
                onToggle = { id, enabled ->
                    checked = if (enabled) checked + id else checked - id
                    runtime.cookingPreferences.edit().putStringSet(checkedKey, checked).apply()
                },
                onClose = closeSheet,
            )
            CookingSheet.STEPS -> CookingStepsSheet(
                progress, session.completedStepIds.toSet(), session.skippedStepIds.toSet(),
                onStep = { id -> runAction { repo.jumpToStep(session.id, id); closeSheet() } },
                onClose = closeSheet,
            )
            CookingSheet.HOW_TO -> currentStep?.let { CookingHowToSheet(it, closeSheet) }
            CookingSheet.ASSISTANT -> CookingAssistant(session, closeSheet)
            CookingSheet.OPTIONS -> CookingOptionsSheet(
                keepAwake, session.servings, busy, onAwake = {
                    keepAwake = it
                    runtime.cookingPreferences.edit().putBoolean(awakeKey, it).apply()
                },
                onServings = { runAction { repo.setServings(session.id, it) } },
                onSkip = {
                    runAction {
                        repo.skipStep(session.id)
                        closeSheet()
                        if (state.advanceToFinish()) onFinish()
                    }
                },
                onStop = { sheet = CookingSheet.STOP }, onClose = closeSheet,
            )
            CookingSheet.INGREDIENT -> recipe.portions(session.servings, session.ingredientOverrides)
                .firstOrNull { it.ingredient.id == ingredientId }?.let { portion ->
                    CookingIngredientSheet(portion, busy, onApply = { quantity ->
                        runAction { repo.setIngredientOverride(session.id, portion.ingredient.id, quantity); closeSheet() }
                    }, onClose = closeSheet)
                }
            CookingSheet.STOP -> AbandonCookingDialog(
                replacing = false, busy = busy, onDismiss = closeSheet,
                onConfirm = { if (!busy) { busy = true; onAbandon() } },
            )
            null -> Unit
        }
    }
}

@Composable
internal fun rememberCookingClock(session: CookingSession?): Long {
    val ticking = session?.timers?.any { it.state == TimerState.RUNNING } == true
    val now by produceState(System.currentTimeMillis(), ticking) {
        value = System.currentTimeMillis()
        if (ticking) while (true) {
            delay(250)
            value = System.currentTimeMillis()
        }
    }
    return now
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
            try {
                when (val response = repo.assistant(AssistantRequest(requestId(), question, session))) {
                    is FoodResult.Success -> answer = response.value
                    is FoodResult.Failure -> error = response
                }
            } finally {
                working = false
            }
        }
    }, onApply = { proposal ->
        val response = answer
        if (response != null && !working) {
            working = true
            scope.launch {
                try {
                    repo.applyProposal(session.id, response.requestId, proposal)
                    applied = applied + proposal.id
                } finally {
                    working = false
                }
            }
        }
    }, onClose = onClose)
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
