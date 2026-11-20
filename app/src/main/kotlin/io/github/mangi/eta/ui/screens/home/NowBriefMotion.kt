package io.github.mangi.eta.ui.screens.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private fun briefAmbientAllowed(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java)
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale > 0f && power?.isPowerSaveMode != true
}

@Composable
internal fun rememberBriefMotionAllowed(active: Boolean): Boolean {
    val context = LocalContext.current
    var allowed by remember(context) { mutableStateOf(briefAmbientAllowed(context)) }
    DisposableEffect(context, active) {
        if (!active) return@DisposableEffect onDispose {}
        fun refresh() {
            allowed = briefAmbientAllowed(context)
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = refresh()
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        context.registerReceiver(
            receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), Context.RECEIVER_NOT_EXPORTED,
        )
        refresh()
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
    return allowed
}

internal object BriefMotionSession {
    var hasOpened by mutableStateOf(false)
    var hasClosedAfterOpening by mutableStateOf(false)
    val revealedCards = mutableSetOf<String>()
}

internal fun shouldAnimateBriefEntrance(hasClosedAfterOpening: Boolean, alreadySeen: Boolean): Boolean =
    !hasClosedAfterOpening && !alreadySeen

internal object BriefMotionTiming {
    const val CARD_MS = 600
    const val CARD_STAGGER_MS = 55L
    const val DENSITY_MS = 500
    const val WORD_MS = 700
    const val WORD_STAGGER_MS = 28
    const val WORD_START_MS = 120
    val EaseMove = CubicBezierEasing(.3f, 0f, .1f, 1f)
}

internal fun briefSceneAlpha(revealProgress: Float): Float =
    revealProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

internal fun briefSkyParallax(scrollOffsetPx: Float): Float = scrollOffsetPx.coerceAtLeast(0f) * .35f

@Composable
internal fun BriefReveal(
    id: String,
    visible: Boolean,
    motionAllowed: Boolean,
    order: Int,
    content: @Composable () -> Unit,
) {
    val progress = remember(id) {
        val animate = shouldAnimateBriefEntrance(
            BriefMotionSession.hasClosedAfterOpening,
            id in BriefMotionSession.revealedCards,
        )
        Animatable(if (animate) 0f else 1f)
    }
    LaunchedEffect(id, visible, motionAllowed) {
        if (!visible) return@LaunchedEffect
        if (motionAllowed && progress.value < 1f && id !in BriefMotionSession.revealedCards) {
            delay(order * BriefMotionTiming.CARD_STAGGER_MS)
            progress.animateTo(1f, tween(BriefMotionTiming.CARD_MS, easing = BriefMotionTiming.EaseMove))
        } else {
            progress.snapTo(1f)
        }
        BriefMotionSession.revealedCards.add(id)
    }
    Box(Modifier.fillMaxWidth().graphicsLayer {
        val fraction = if (motionAllowed && visible) progress.value else 1f
        alpha = fraction
        translationY = (1f - fraction) * 16.dp.toPx()
    }) { content() }
}
