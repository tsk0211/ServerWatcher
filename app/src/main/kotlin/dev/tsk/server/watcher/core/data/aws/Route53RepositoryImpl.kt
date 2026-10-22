package dev.tsk.server.watcher.core.data.aws

import android.util.Log
import aws.sdk.kotlin.services.route53.model.Change
import aws.sdk.kotlin.services.route53.model.ChangeAction
import aws.sdk.kotlin.services.route53.model.ChangeBatch
import aws.sdk.kotlin.services.route53.model.ChangeResourceRecordSetsRequest
import aws.sdk.kotlin.services.route53.model.ListHostedZonesRequest
import aws.sdk.kotlin.services.route53.model.ResourceRecord
import aws.sdk.kotlin.services.route53.model.ResourceRecordSet
import aws.sdk.kotlin.services.route53.model.RrType
import dev.tsk.server.watcher.core.domain.model.HostedZoneSummary
import dev.tsk.server.watcher.core.domain.repository.IRoute53Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SW"

class Route53RepositoryImpl @Inject constructor(
    private val clientProvider: AwsClientProvider,
    private val authInterceptor: AuthInterceptor,
) : IRoute53Repository {

    override suspend fun listHostedZones(): Result<List<HostedZoneSummary>> {
        Log.d(TAG, "[Route53] listHostedZones")
        return authInterceptor.execute {
            withContext(Dispatchers.IO) {
                clientProvider.getRoute53Client().use { client ->
                    (client.listHostedZones(ListHostedZonesRequest {}).hostedZones ?: emptyList())
                        .map { hz ->
                            HostedZoneSummary(
                                id = hz.id?.removePrefix("/hostedzone/") ?: "",
                                name = hz.name?.trimEnd('.') ?: "",
                            )
                        }
                        .filter { it.id.isNotEmpty() }
                        .also { Log.d(TAG, "[Route53] listHostedZones count=${it.size}") }
                }
            }
        }
    }

    override suspend fun upsertARecord(hostedZoneId: String, recordName: String, ip: String, ttl: Long): Result<Unit> {
        Log.d(TAG, "[Route53] upsertARecord zone=$hostedZoneId name=$recordName ip=$ip ttl=$ttl")
        return authInterceptor.execute {
            withContext(Dispatchers.IO) {
                clientProvider.getRoute53Client().use { client ->
                    client.changeResourceRecordSets(ChangeResourceRecordSetsRequest {
                        this.hostedZoneId = hostedZoneId
                        changeBatch = ChangeBatch {
                            changes = listOf(
                                Change {
                                    action = ChangeAction.Upsert
                                    resourceRecordSet = ResourceRecordSet {
                                        name = recordName
                                        type = RrType.A
                                        this.ttl = ttl
                                        resourceRecords = listOf(ResourceRecord { value = ip })
                                    }
                                }
                            )
                        }
                    })
                    Log.d(TAG, "[Route53] upsertARecord SUCCESS zone=$hostedZoneId name=$recordName ip=$ip")
                }
            }
        }
    }
}
