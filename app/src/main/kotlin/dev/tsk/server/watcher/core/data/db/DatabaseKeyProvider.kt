package dev.tsk.server.watcher.core.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseKeyProvider @Inject constructor(
    private val context: Context,
) {
    private companion object {
        const val PREFS_NAME = "db_key_prefs"
        const val KEY_PASSPHRASE = "db_passphrase"
        const val PASSPHRASE_LENGTH = 32
    }

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getPassphrase(): ByteArray {
        val stored = prefs.getString(KEY_PASSPHRASE, null)
        if (stored != null) {
            return android.util.Base64.decode(stored, android.util.Base64.DEFAULT)
        }
        val passphrase = ByteArray(PASSPHRASE_LENGTH).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_PASSPHRASE, android.util.Base64.encodeToString(passphrase, android.util.Base64.DEFAULT))
            .apply()
        return passphrase
    }
}
