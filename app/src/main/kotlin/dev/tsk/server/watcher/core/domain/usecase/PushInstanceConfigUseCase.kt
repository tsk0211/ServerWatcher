package dev.tsk.server.watcher.core.domain.usecase

import dev.tsk.server.watcher.core.domain.repository.IEc2Repository
import dev.tsk.server.watcher.core.domain.repository.IInstanceConfigRepository
import javax.inject.Inject

class PushInstanceConfigUseCase @Inject constructor(
    private val configRepository: IInstanceConfigRepository,
    private val ec2Repository: IEc2Repository,
) {
    suspend operator fun invoke(ec2InstanceId: String, region: String): Result<Unit> {
        val configs = configRepository.getByInstance(ec2InstanceId)

        // cloud-config with bootcmd runs on EVERY boot, not just first launch.
        // A plain #!/bin/bash user data script only runs once (cloud-init semaphore).
        val cloudConfig = buildString {
            appendLine("#cloud-config")
            appendLine("bootcmd:")
            appendLine("  - |")
            appendLine("    mkdir -p /etc")
            appendLine("    cat > /etc/serverwatcher.env << 'SWENV'")
            configs.forEach { appendLine("    ${it.key}=${it.value.replace("\n", "").replace("\r", "")}") }
            appendLine("    SWENV")
            append("    chmod 644 /etc/serverwatcher.env")
        }

        return ec2Repository.modifyUserData(ec2InstanceId, region, cloudConfig.toByteArray(Charsets.UTF_8))
    }
}
