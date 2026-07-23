package fr.loevan.jeancalcul.security

import android.annotation.SuppressLint
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android implementation backed by an AES key that never leaves Android Keystore. */
class AndroidKeystoreSecretStore(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecretStore by EncryptedSecretStore(
        cipher = AndroidKeystoreSecretCipher(),
        storage = SharedPreferencesEncryptedSecretStorage(context.applicationContext),
        dispatcher = dispatcher,
    )

internal class AndroidKeystoreSecretCipher : SecretCipher {
    private val keyStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED, ::createKeyStore)

    override fun encrypt(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): EncryptedSecret =
        try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, existingKey() ?: generateKey())
            cipher.updateAAD(associatedData)
            EncryptedSecret(
                initializationVector = cipher.iv.copyOf(),
                ciphertext = cipher.doFinal(plaintext),
            )
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw SecretMaterialInvalidatedException()
        } catch (_: InvalidKeyException) {
            throw SecretMaterialInvalidatedException()
        } catch (_: GeneralSecurityException) {
            throw SecretStorageException()
        } catch (_: ProviderException) {
            throw SecretStorageException()
        }

    override fun decrypt(
        encryptedSecret: EncryptedSecret,
        associatedData: ByteArray,
    ): ByteArray =
        try {
            val key = existingKey() ?: throw SecretMaterialInvalidatedException()
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BITS, encryptedSecret.initializationVector),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(encryptedSecret.ciphertext)
        } catch (error: SecretMaterialInvalidatedException) {
            throw error
        } catch (_: KeyPermanentlyInvalidatedException) {
            throw SecretMaterialInvalidatedException()
        } catch (_: AEADBadTagException) {
            throw SecretMaterialInvalidatedException()
        } catch (_: BadPaddingException) {
            throw SecretMaterialInvalidatedException()
        } catch (_: InvalidKeyException) {
            throw SecretMaterialInvalidatedException()
        } catch (_: GeneralSecurityException) {
            throw SecretStorageException()
        } catch (_: ProviderException) {
            throw SecretStorageException()
        }

    override fun deleteKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (_: KeyStoreException) {
            throw SecretStorageException()
        } catch (_: ProviderException) {
            throw SecretStorageException()
        }
    }

    private fun createKeyStore(): KeyStore =
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
                load(null)
            }
        } catch (_: GeneralSecurityException) {
            throw SecretStorageException()
        } catch (_: IOException) {
            throw SecretStorageException()
        }

    private fun existingKey(): SecretKey? =
        try {
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (_: UnrecoverableKeyException) {
            throw SecretMaterialInvalidatedException()
        } catch (_: GeneralSecurityException) {
            throw SecretStorageException()
        }

    private fun generateKey(): SecretKey =
        try {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
            generator.init(
                KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generator.generateKey()
        } catch (_: GeneralSecurityException) {
            throw SecretStorageException()
        } catch (_: ProviderException) {
            throw SecretStorageException()
        }
}

internal class SharedPreferencesEncryptedSecretStorage(
    context: Context,
) : EncryptedSecretStorage {
    private val preferences =
        context.getSharedPreferences(SECRET_PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(id: SecretId): EncryptedSecret? {
        val serialized =
            try {
                preferences.getString(id.preferenceKey(), null)
            } catch (_: ClassCastException) {
                throw SecretMaterialInvalidatedException()
            } ?: return null
        return deserialize(serialized)
    }

    @SuppressLint("ApplySharedPref")
    override fun write(
        id: SecretId,
        encryptedSecret: EncryptedSecret,
    ) {
        val committed =
            preferences
                .edit()
                .putString(id.preferenceKey(), encryptedSecret.serialize())
                .commit()
        if (!committed) throw SecretStorageException()
    }

    @SuppressLint("ApplySharedPref")
    override fun remove(id: SecretId): Boolean {
        if (!preferences.contains(id.preferenceKey())) return false
        if (!preferences.edit().remove(id.preferenceKey()).commit()) throw SecretStorageException()
        return true
    }

    @SuppressLint("ApplySharedPref")
    override fun clear() {
        if (!preferences.edit().clear().commit()) throw SecretStorageException()
    }

    private fun SecretId.preferenceKey(): String = "$SECRET_PREFERENCE_PREFIX$value"

    private fun EncryptedSecret.serialize(): String =
        listOf(
            SERIALIZATION_VERSION,
            initializationVector.toBase64(),
            ciphertext.toBase64(),
        ).joinToString(SERIALIZATION_SEPARATOR)

    private fun deserialize(serialized: String): EncryptedSecret {
        val fields = serialized.split(SERIALIZATION_SEPARATOR)
        if (fields.size != SERIALIZED_FIELD_COUNT || fields.first() != SERIALIZATION_VERSION) {
            invalidatedSecretMaterial()
        }
        return try {
            val encryptedSecret =
                EncryptedSecret(
                    initializationVector = fields[1].fromBase64(),
                    ciphertext = fields[2].fromBase64(),
                )
            if (
                encryptedSecret.initializationVector.size != GCM_INITIALIZATION_VECTOR_SIZE_BYTES ||
                encryptedSecret.ciphertext.size < GCM_AUTHENTICATION_TAG_SIZE_BYTES
            ) {
                invalidatedSecretMaterial()
            }
            encryptedSecret
        } catch (_: IllegalArgumentException) {
            invalidatedSecretMaterial()
        }
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.fromBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private fun invalidatedSecretMaterial(): Nothing = throw SecretMaterialInvalidatedException()
}

internal const val SECRET_PREFERENCES_NAME = "jean_calcul_encrypted_secrets"
internal const val KEY_ALIAS = "fr.loevan.jeancalcul.secret_store.aes_gcm.v1"
private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_SIZE_BITS = 256
private const val AUTHENTICATION_TAG_LENGTH_BITS = 128
private const val GCM_INITIALIZATION_VECTOR_SIZE_BYTES = 12
private const val GCM_AUTHENTICATION_TAG_SIZE_BYTES = AUTHENTICATION_TAG_LENGTH_BITS / Byte.SIZE_BITS
private const val SECRET_PREFERENCE_PREFIX = "secret."
private const val SERIALIZATION_VERSION = "1"
private const val SERIALIZATION_SEPARATOR = ":"
private const val SERIALIZED_FIELD_COUNT = 3
