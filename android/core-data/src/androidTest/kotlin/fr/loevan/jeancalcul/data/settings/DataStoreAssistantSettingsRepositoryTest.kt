package fr.loevan.jeancalcul.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.loevan.jeancalcul.domain.AssistantSettings
import fr.loevan.jeancalcul.domain.ProviderConnection
import fr.loevan.jeancalcul.domain.ProviderKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreAssistantSettingsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = DataStoreAssistantSettingsRepository(context)

    @Before
    fun clearBefore() = runTest { repository.update { AssistantSettings() } }

    @After
    fun clearAfter() = runTest { repository.update { AssistantSettings() } }

    @Test
    fun settingsSurviveRepositoryRecreation() =
        runTest {
            val provider =
                ProviderConnection(
                    id = "ollama",
                    displayName = "Ollama local",
                    kind = ProviderKind.OLLAMA,
                    baseUrl = "http://192.168.1.10:11434",
                )
            repository.update { it.copy(providers = listOf(provider)) }

            val restored = DataStoreAssistantSettingsRepository(context).settings.first()

            assertEquals(provider, restored.providers.single())
        }
}
