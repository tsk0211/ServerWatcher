package dev.tsk.server.watcher.core.data.aws

import android.util.Log
import aws.sdk.kotlin.services.cognitoidentity.CognitoIdentityClient
import aws.sdk.kotlin.services.cognitoidentity.model.GetCredentialsForIdentityRequest
import aws.sdk.kotlin.services.cognitoidentity.model.GetIdRequest
import aws.sdk.kotlin.services.ec2.Ec2Client
import aws.sdk.kotlin.services.route53.Route53Client
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.Attributes
import dev.tsk.server.watcher.core.data.local.AwsConfigRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SW"

@Singleton
class AwsClientProvider @Inject constructor(
    private val tokenManager: CognitoTokenManager,
    private val configRepository: AwsConfigRepository,
) {
    // Fresh client per request — no cache. Caching caused zombie SupervisorJobs
    // when concurrent getOrPut calls raced and one client was silently dropped.
    suspend fun getEc2Client(region: String): Ec2Client {
        val config = configRepository.getConfig()
        val effectiveRegion = region.ifBlank { config?.region ?: "us-east-1" }
        Log.d(TAG, "[EC2] creating client for region=$effectiveRegion")
        return Ec2Client {
            this.region = effectiveRegion
            this.credentialsProvider = CognitoCredentialsProvider(tokenManager, configRepository, effectiveRegion)
        }
    }

    suspend fun getRoute53Client(): Route53Client {
        Log.d(TAG, "[Route53] creating client")
        return Route53Client {
            this.region = "us-east-1"
            this.credentialsProvider = CognitoCredentialsProvider(tokenManager, configRepository, "us-east-1")
        }
    }

    fun closeAll() { /* clients are per-request, closed via .use{} in the repository */ }
}

private class CognitoCredentialsProvider(
    private val tokenManager: CognitoTokenManager,
    private val configRepository: AwsConfigRepository,
    private val ec2Region: String,
) : CredentialsProvider {
    override suspend fun resolve(attributes: Attributes): Credentials = withContext(Dispatchers.IO) {
        val config = configRepository.getConfig() ?: error("AWS not configured — complete onboarding first")
        Log.d(TAG, "[Creds] resolving credentials — identityPool=${config.identityPoolId} poolRegion=${config.region} ec2Region=$ec2Region")
        val idToken = try {
            Log.d(TAG, "[Creds] fetching valid ID token (will refresh if expiring)")
            tokenManager.getValidIdToken()
        } catch (e: Exception) {
            Log.e(TAG, "[Creds] failed to get ID token: ${e.message}", e)
            throw e
        }

        // Cognito Identity client must always use the Identity Pool's region, not the EC2 region
        val identityClient = CognitoIdentityClient { this.region = config.region }
        val providerName = "cognito-idp.${config.region}.amazonaws.com/${config.userPoolId}"
        Log.d(TAG, "[Creds] providerName=$providerName")

        try {
            val identityId = identityClient.getId(
                GetIdRequest {
                    identityPoolId = config.identityPoolId
                    logins = mapOf(providerName to idToken)
                }
            ).identityId ?: error("GetId returned null identityId")
            Log.d(TAG, "[Creds] identityId=$identityId")

            val creds = identityClient.getCredentialsForIdentity(
                GetCredentialsForIdentityRequest {
                    this.identityId = identityId
                    logins = mapOf(providerName to idToken)
                }
            ).credentials ?: error("GetCredentialsForIdentity returned null")

            Log.d(TAG, "[Creds] credentials resolved OK, expiry=${creds.expiration}")
            Credentials(
                accessKeyId = creds.accessKeyId ?: error("Missing accessKeyId"),
                secretAccessKey = creds.secretKey ?: error("Missing secretKey"),
                sessionToken = creds.sessionToken,
            )
        } catch (e: Exception) {
            Log.e(TAG, "[Creds] FAILED — ${e::class.qualifiedName}: ${e.message}", e)
            throw e
        } finally {
            runCatching { identityClient.close() }
        }
    }
}
