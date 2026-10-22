package dev.tsk.server.watcher.core.domain.usecase

import dev.tsk.server.watcher.core.domain.repository.IEc2Repository
import dev.tsk.server.watcher.core.domain.repository.IInstanceConfigRepository
import javax.inject.Inject

class PushInstanceConfigUseCase @Inject constructor(
    private val configRepository: IInstanceConfigRepository,
    private val ec2Repository: IEc2Repository,
) {
    suspend operator fun invoke(instanceRowId: String, ec2InstanceId: String, region: String): Result<Unit> {
        val configs = configRepository.getByInstance(instanceRowId)
        val envLines = configs.joinToString("\n") { "${it.key}=${it.value}" }
        val script = buildString {
            appendLine("#!/bin/bash")
            appendLine("cat > /etc/serverwatcher.env << 'ENVEOF'")
            append(envLines)
            appendLine()
            append("ENVEOF")
        }
        return ec2Repository.modifyUserData(ec2InstanceId, region, script.toByteArray(Charsets.UTF_8))
    }
}
