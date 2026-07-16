package fr.loevan.jeancalcul.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Shared Compose entry point. Product-specific visual design is added in later issues. */
@Composable
fun jeanCalculTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
