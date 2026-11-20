package io.github.mangi.eta.ui.screens.home.brief

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@Composable
internal fun rememberBriefTime(active: Boolean): State<LocalDateTime> {
    val context = LocalContext.current
    val time = remember { mutableStateOf(localMinute()) }
    DisposableEffect(context, active) {
        if (!active) return@DisposableEffect onDispose {}
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                time.value = localMinute()
            }
        }
        val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        time.value = localMinute()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return time
}

private fun localMinute(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
