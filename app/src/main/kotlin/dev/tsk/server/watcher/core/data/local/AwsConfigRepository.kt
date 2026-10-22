package dev.tsk.server.watcher.core.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tsk.server.watcher.core.domain.model.AwsConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AwsConfigRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val PREFS_NAME = "aws_config"
        const val KEY_USER_POOL_ID = "user_pool_id"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_IDENTITY_POOL_ID = "identity_pool_id"
        const val KEY_REGION = "region"
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

    fun hasConfig(): Boolean = prefs.getString(KEY_REGION, null) != null

    fun getConfig(): AwsConfig? {
        val region = prefs.getString(KEY_REGION, null) ?: return null
        return AwsConfig(
            userPoolId = prefs.getString(KEY_USER_POOL_ID, "") ?: "",
            clientId = prefs.getString(KEY_CLIENT_ID, "") ?: "",
            identityPoolId = prefs.getString(KEY_IDENTITY_POOL_ID, "") ?: "",
            region = region,
        )
    }

    fun saveConfig(config: AwsConfig) {
        prefs.edit()
            .putString(KEY_USER_POOL_ID, config.userPoolId)
            .putString(KEY_CLIENT_ID, config.clientId)
            .putString(KEY_IDENTITY_POOL_ID, config.identityPoolId)
            .putString(KEY_REGION, config.region)
            .apply()
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }
}
