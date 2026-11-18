package io.github.mangi.eta.ui.screens.settings

import android.content.Intent
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.itos.coui.layout.Footer
import com.itos.coui.layout.Group
import io.github.mangi.eta.agent.development.DevelopmentGitHubClient
import io.github.mangi.eta.agent.development.DevelopmentMonitor
import io.github.mangi.eta.agent.development.DevelopmentPolicy
import io.github.mangi.eta.agent.development.DevelopmentStore
import io.github.mangi.eta.data.update.PreviewUpdates
import io.github.mangi.eta.ui.components.coui.CouiButton
import io.github.mangi.eta.ui.components.coui.CouiButtonRole
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiPreference
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun AppImprovementsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { DevelopmentStore(context) }
    var notificationsAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsAllowed = granted
    }
    var connected by remember { mutableStateOf(store.enabled) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val status by DevelopmentMonitor.status.collectAsState()
    var proposal by remember { mutableStateOf(store.proposal()) }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block() } catch (e: Exception) { message = e.message ?: "App improvement request failed" }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) {
        DevelopmentMonitor.start(context)
        runCatching { DevelopmentMonitor.refresh(context) }
    }
    LaunchedEffect(status) { proposal = store.proposal() }
    CouiScaffoldPage(title = "App improvements", onBack = onBack, compactTitle = true) {
        Group(title = "Improve Jean-Calcul") {
            CouiPreference(title = "Describe a bug or feature", showChevron = false,
                summary = "While viewing Jean-Calcul, hold Power and describe what should change. The assistant uses this screen and your configured model to implement the change. You can close the assistant while it works; a notification brings you back when it needs an answer or finishes.")
        }
        Footer(text = "GitHub Actions builds and tests each PR, then publishes a signed update. Install it here, test it, and approve the PR below. Android still asks you to confirm installation.")
        Group(title = "GitHub connection") {
            CouiPreference(title = if (connected) "GitHub connected" else "Connect GitHub once", showChevron = false,
                summary = DevelopmentPolicy.REPOSITORY)
            if (!connected) {
                Column(Modifier.padding(16.dp)) {
                    CouiTextField(value = token, onValueChange = { token = it }, label = "Fine-grained GitHub token",
                        visualTransformation = PasswordVisualTransformation(), showClearButton = false)
                    CouiButton(text = if (busy) "Connecting…" else "Connect and enable", enabled = token.isNotBlank() && !busy,
                        role = CouiButtonRole.Primary, onClick = { action {
                            val candidate = token.trim()
                            withContext(Dispatchers.IO) {
                                val repository = DevelopmentGitHubClient({ candidate }).api("")
                                require(repository.getJSONObject("permissions").optBoolean("push")) { "This account cannot write to the Jean-Calcul repository" }
                                store.connect(candidate)
                            }
                            token = ""; connected = true; message = "Connected. Open a Jean-Calcul screen and hold Power to request an improvement."
                            if (!notificationsAllowed) notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            DevelopmentMonitor.start(context)
                        } })
                }
                CouiPreference(title = "Create a repository-scoped token", summary = "Only this repository: Contents and Pull requests read/write; Actions and Checks read. Workflows permission is unnecessary.", onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new?name=Jean-Calcul&description=In-app%20improvements&target_name=Loe159&contents=write&pull_requests=write&actions=read&checks=read")))
                })
            } else CouiPreference(title = "Disconnect GitHub", summary = "Removes the encrypted token and disables new code changes", onClick = { action {
                withContext(Dispatchers.IO) { store.disconnect() }; connected = false; message = "GitHub disconnected"
            } })
        }
        if (connected && !notificationsAllowed) {
            Group(title = "Task notifications") {
                CouiPreference(title = "Allow notifications", summary = "Get a message when Jean-Calcul needs an answer or finishes a task.",
                    onClick = { notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }
        }
        Footer(text = "Your token stays encrypted in Android Keystore and is never sent to the model or placed in an APK. Your implementation request and code changes are published to the repository. Screenshots and raw app text are used by your selected model and are not attached to GitHub PRs. Credential screens are withheld.")
        if (proposal != null) {
            val current = proposal!!
            Group(title = "Latest improvement") {
                CouiPreference(title = current.optString("title", "Improvement PR"),
                    summary = "PR #${current.optInt("number")} · ${current.optString("head").take(8)}", showChevron = false)
                val runs = status?.optJSONArray("runs")
                val latest = runs?.let { list -> (0 until list.length()).map(list::getJSONObject).firstOrNull { it.optString("head_sha") == current.optString("head") } }
                CouiPreference(title = "Build and tests", summary = latest?.let { "${it.optString("status")} · ${it.optString("conclusion").takeUnless { value -> value == "null" }.orEmpty()}" } ?: "Waiting for GitHub Actions", showChevron = false)
                CouiPreference(title = "Open PR and results", enabled = current.optInt("number") > 0, onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${DevelopmentPolicy.REPOSITORY}/pull/${current.optInt("number")}")))
                })
                CouiPreference(title = "Refresh progress", enabled = connected && !busy, onClick = { action {
                    val fresh = DevelopmentMonitor.refresh(context)
                    fresh?.optJSONObject("pull_request")?.optString("head")?.takeIf { it.isNotBlank() }?.let {
                        withContext(Dispatchers.IO) { store.saveProposal(JSONObject(current.toString()).put("head", it)) }
                    }
                    proposal = store.proposal(); message = "Progress refreshed"
                } })
                CouiPreference(title = "Install latest test update", enabled = !busy,
                    onClick = { PreviewUpdates.check(context, manual = true) })
                CouiPreference(title = if (current.optBoolean("merged")) "PR merged" else "Approve and merge PR #${current.optInt("number")}",
                    summary = "Use after testing this version. Merges revision ${current.optString("head").take(8)} into the foundation branch only when the Android checks pass.",
                    enabled = connected && !busy && !current.optBoolean("merged") && !current.optBoolean("closed") && latest?.optString("conclusion") == "success",
                    onClick = { action {
                        val result = withContext(Dispatchers.IO) { DevelopmentGitHubClient(store::token).approve(current) }
                        check(result.optBoolean("merged")) { "GitHub did not merge the PR" }
                        withContext(Dispatchers.IO) { store.saveProposal(JSONObject(current.toString()).put("merged", true)) }
                        proposal = store.proposal(); message = "PR merged. Your improvement is now part of Jean-Calcul."
                    } })
            }
        }
        if (message.isNotBlank()) Footer(text = message)
        CouiPageBottomSpacer()
    }
}
