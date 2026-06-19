package com.jeancalcul.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jeancalcul.assistant.data.SettingsStore
import com.jeancalcul.assistant.ui.MainUiState
import com.jeancalcul.assistant.ui.MainViewModel
import com.jeancalcul.assistant.ui.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val factory = remember { MainViewModelFactory(SettingsStore(applicationContext)) }
            val viewModel: MainViewModel = viewModel(factory = factory)
            val state by viewModel.uiState.collectAsState()
            HermesApp(state, viewModel)
        }
    }
}

@Composable
fun HermesApp(state: MainUiState, viewModel: MainViewModel) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Jean Calcul Assistant", style = MaterialTheme.typography.headlineSmall)
                SettingsCard(state, viewModel)
                Text(state.status, style = MaterialTheme.typography.bodyMedium)
                ConversationCard(state, viewModel)
            }
        }
    }
}

@Composable
private fun SettingsCard(state: MainUiState, viewModel: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Configuration Hermes", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = state.baseUrlDraft,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = state.tokenDraft,
                onValueChange = viewModel::updateToken,
                label = { Text("Token optionnel") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveSettings, enabled = !state.isBusy) { Text("Enregistrer") }
                Button(onClick = viewModel::testConnection, enabled = !state.isBusy) { Text("Tester") }
            }
        }
    }
}

@Composable
private fun ConversationCard(state: MainUiState, viewModel: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Conversation texte", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.messages) { message ->
                    Text("${message.author}: ${message.text}")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.messageDraft,
                onValueChange = viewModel::updateMessage,
                label = { Text("Message à Hermes") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::sendMessage, enabled = !state.isBusy && state.messageDraft.isNotBlank()) {
                Text("Envoyer")
            }
        }
    }
}
