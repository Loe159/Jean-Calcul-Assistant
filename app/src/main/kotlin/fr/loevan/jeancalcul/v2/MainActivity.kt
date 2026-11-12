package fr.loevan.jeancalcul.v2

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.itos.coui.theme.CouiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { V2App() }
    }
}

@Composable
fun V2App() {
    val dark = isSystemInDarkTheme()
    val focus = LocalFocusManager.current
    var draft by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<String>() }
    BackHandler(enabled = settings) { settings = false }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFA9BCFF), background = Color(0xFF11121B),
        surface = Color(0xFF212431), surfaceVariant = Color(0xFF323544),
    ) else lightColorScheme(
        primary = Color(0xFF496BE5), background = Color(0xFFF7F8FA),
        surface = Color.White, surfaceVariant = Color(0xFFEAEDF3),
    )
    CouiTheme(darkTheme = dark) {
        MaterialTheme(colorScheme = colors) {
            Scaffold(
                modifier = Modifier.fillMaxSize().imePadding(),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Jean-Calcul", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            IconButton(onClick = { focus.clearFocus(); messages.clear(); draft = ""; settings = false }, modifier = Modifier.testTag("reset")) {
                                Icon(Icons.Default.Add, contentDescription = "Nouveau")
                            }
                            IconButton(onClick = { focus.clearFocus(); settings = !settings }, modifier = Modifier.testTag("settings")) {
                                Icon(if (settings) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Settings,
                                    contentDescription = if (settings) "Retour au chat" else "Réglages")
                            }
                        }
                        Text(if (settings) "Paramètres" else "V2 · COUI preview", style = MaterialTheme.typography.labelMedium)
                    }
                },
                bottomBar = {
                    if (!settings) {
                        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = draft, onValueChange = { draft = it },
                                modifier = Modifier.weight(1f).testTag("composer"),
                                placeholder = { Text("Demander à Jean-Calcul…") },
                                shape = RoundedCornerShape(28.dp), maxLines = 4,
                            )
                            Button(
                                enabled = draft.isNotBlank(),
                                onClick = { if (draft.isNotBlank()) { messages.add(draft.trim()); draft = "" } },
                                modifier = Modifier.testTag("send"),
                                shape = RoundedCornerShape(28.dp),
                            ) { Text("Envoyer") }
                        }
                    }
                },
            ) { padding ->
                if (settings) {
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
                        Text("Préférences", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Text("Design COUI — prototype isolé, sans moteur IA connecté.")
                    }
                } else if (messages.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).testTag("empty"),
                        contentPadding = PaddingValues(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        item { Text("Un assistant, à votre façon.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center) }
                        item { Spacer(Modifier.height(10.dp)) }
                        item { Text("Une expérience simple, fluide et personnelle.", textAlign = TextAlign.Center) }
                        item { Spacer(Modifier.height(22.dp)) }
                        item {
                            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("Prototype UI · IA non connectée", modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("messages"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(messages) { message ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.End) {
                                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(message, modifier = Modifier.padding(16.dp))
                                }
                            }
                        }
                        item { Text("Message enregistré uniquement en mémoire pour cette démo.", modifier = Modifier.padding(20.dp).testTag("prototype_notice"), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
