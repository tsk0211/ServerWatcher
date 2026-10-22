package dev.tsk.server.watcher.core.data.db.mapper

import dev.tsk.server.watcher.core.data.db.entity.DomainEntity
import dev.tsk.server.watcher.core.data.db.entity.InstanceConfigEntity
import dev.tsk.server.watcher.core.data.db.entity.InstanceEntity
import dev.tsk.server.watcher.core.data.db.entity.SubdomainEntity
import dev.tsk.server.watcher.core.domain.model.Domain
import dev.tsk.server.watcher.core.domain.model.Instance
import dev.tsk.server.watcher.core.domain.model.InstanceConfig
import dev.tsk.server.watcher.core.domain.model.Subdomain

fun DomainEntity.toModel() = Domain(
    id = id,
    name = name,
    region = region,
    isMasterOn = isMasterOn,
    hostedZoneId = hostedZoneId,
    hostedZoneName = hostedZoneName,
)

fun Domain.toEntity() = DomainEntity(
    id = id,
    name = name,
    region = region,
    isMasterOn = isMasterOn,
    hostedZoneId = hostedZoneId,
    hostedZoneName = hostedZoneName,
)

fun SubdomainEntity.toModel() = Subdomain(
    id = id,
    domainId = domainId,
    prefix = prefix,
    isApex = isApex,
    healthPath = healthPath,
    healthRangeStart = healthRangeStart,
    healthRangeEnd = healthRangeEnd,
    healthIntervalSeconds = healthIntervalSeconds,
)

fun Subdomain.toEntity() = SubdomainEntity(
    id = id,
    domainId = domainId,
    prefix = prefix,
    isApex = isApex,
    healthPath = healthPath,
    healthRangeStart = healthRangeStart,
    healthRangeEnd = healthRangeEnd,
    healthIntervalSeconds = healthIntervalSeconds,
)

fun InstanceEntity.toModel() = Instance(
    id = id,
    instanceId = instanceId,
    subdomainId = subdomainId,
    label = label,
    region = region,
    isPrimary = isPrimary,
)

fun Instance.toEntity() = InstanceEntity(
    id = id,
    instanceId = instanceId,
    subdomainId = subdomainId,
    label = label,
    region = region,
    isPrimary = isPrimary,
)

fun InstanceConfigEntity.toModel() = InstanceConfig(
    id = id,
    instanceId = instanceId,
    key = key,
    value = value,
)

fun InstanceConfig.toEntity() = InstanceConfigEntity(
    id = id,
    instanceId = instanceId,
    key = key,
    value = value,
)
