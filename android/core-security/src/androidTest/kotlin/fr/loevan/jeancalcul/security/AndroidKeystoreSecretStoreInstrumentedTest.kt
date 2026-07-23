package fr.loevan.jeancalcul.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreSecretStoreInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = AndroidKeystoreSecretStore(context)
    private val secretId = SecretId("instrumented.provider.key")

    @After
    fun tearDown() {
        runBlocking { store.reset() }
    }

    @Test
    fun encryptsWithANonExportableKeyAndSupportsReplacementAndDeletion() =
        runBlocking {
            val first = "sk-instrumented-first".toCharArray()
            val replacement = "sk-instrumented-replacement".toCharArray()

            assertSuccess(store.put(secretId, first))
            assertStoredValueDoesNotContain(first)
            assertArrayEquals(first, readSecret())

            val key = androidKeyStore().getKey(KEY_ALIAS, null) as SecretKey
            assertNull(key.encoded)

            assertSuccess(store.put(secretId, replacement))
            assertArrayEquals(replacement, readSecret())
            assertTrue(assertSuccess(store.delete(secretId)))
            assertNull(assertSuccess(store.get(secretId)))
        }

    @Test
    fun reportsMissingKeyAsRecoverableInvalidation() =
        runBlocking {
            assertSuccess(store.put(secretId, "sk-restored-data".toCharArray()))
            androidKeyStore().deleteEntry(KEY_ALIAS)

            val failure = store.get(secretId) as SecretStoreResult.Failure

            assertEquals(SecretStoreFailureReason.INVALIDATED, failure.error.reason)
            assertTrue(failure.error.isRecoverable)
            assertFalse(failure.error.userMessage.contains("sk-restored-data"))

            assertSuccess(store.reset())
            assertSuccess(store.put(secretId, "sk-reentered".toCharArray()))
            assertArrayEquals("sk-reentered".toCharArray(), readSecret())
        }

    private fun assertStoredValueDoesNotContain(secret: CharArray) {
        val values =
            context
                .getSharedPreferences(SECRET_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .all
                .values
                .joinToString()
        assertFalse(values.contains(String(secret)))
    }

    private suspend fun readSecret(): CharArray {
        val value = requireNotNull(assertSuccess(store.get(secretId)))
        return value.use { secretValue -> secretValue.useChars(CharArray::copyOf) }
    }

    private fun androidKeyStore(): KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }

    private fun <T> assertSuccess(result: SecretStoreResult<T>): T =
        when (result) {
            is SecretStoreResult.Success -> result.value
            is SecretStoreResult.Failure -> error("Expected success but got ${result.error.reason}")
        }
}
