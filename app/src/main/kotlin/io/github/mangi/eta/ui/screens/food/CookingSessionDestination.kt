package io.github.mangi.eta.ui.screens.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.mangi.eta.EtaApp
import io.github.mangi.eta.R
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.suqi8.coui.kmp.basic.Text
import kotlinx.coroutines.flow.map

/** Notification destination until the interactive cooking screen is added. */
@Composable
internal fun CookingSessionDestination(sessionId: String, onBack: () -> Unit) {
    val repository = (LocalContext.current.applicationContext as EtaApp).food.repository
    val missing = stringResource(R.string.food_cooking_session_unavailable)
    val title by remember(repository, sessionId, missing) {
        repository.session(sessionId).map { it?.recipeSnapshot?.title ?: missing }
    }.collectAsState(initial = "")
    CouiScaffoldPage(title = stringResource(R.string.food_cooking_session), onBack = onBack) {
        if (title.isNotEmpty()) Text(title)
    }
}
