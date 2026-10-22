package dev.tsk.server.watcher.core.domain.usecase

import dev.tsk.server.watcher.core.domain.repository.IEc2Repository
import javax.inject.Inject

class StartInstanceUseCase @Inject constructor(
    private val ec2Repository: IEc2Repository,
) {
    suspend operator fun invoke(instanceId: String, region: String): Result<Unit> =
        ec2Repository.startInstance(instanceId, region)
}
