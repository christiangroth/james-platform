package de.chrgroth.james.platform.adapter.`in`.outbox

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition
import de.chrgroth.james.platform.domain.port.`in`.app.AggregationPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionManagementPort
import de.chrgroth.james.platform.domain.port.`in`.app.TestDataGeneratorPort
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
import de.chrgroth.james.platform.domain.port.`in`.user.AdminUserManagementPort
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxDispatcher
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition
import de.chrgroth.quarkus.outbox.domain.DispatchResult
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class DomainOutboxTaskDispatcher(
  private val importPort: ImportPort,
  private val userAppStorePort: UserAppStorePort,
  private val appManagementPort: AppManagementPort,
  private val adminUserManagementPort: AdminUserManagementPort,
  private val appVersionManagementPort: AppVersionManagementPort,
  private val testDataGeneratorPort: TestDataGeneratorPort,
  private val aggregationPort: AggregationPort,
) : ApplicationOutboxDispatcher {

  override fun getAllPartitions(): List<ApplicationOutboxPartition> = DomainOutboxPartition.all

  override fun deserialize(partition: ApplicationOutboxPartition, eventType: String, payload: String): ApplicationOutboxEvent =
    DomainOutboxEvent.fromKey(eventType, payload)

  override fun dispatch(event: ApplicationOutboxEvent): DispatchResult = dispatchEvent(event as DomainOutboxEvent)

  private fun dispatchEvent(event: DomainOutboxEvent): DispatchResult = handleDomainOperation(event.key) {
    when (event) {
      is DomainOutboxEvent.AcceptDryRun -> importPort.handle(event)
      is DomainOutboxEvent.UninstallApp -> userAppStorePort.handle(event)
      is DomainOutboxEvent.DeleteApp -> appManagementPort.handle(event)
      is DomainOutboxEvent.DeleteUser -> adminUserManagementPort.handle(event)
      is DomainOutboxEvent.AutoUpgradeInstallation -> appVersionManagementPort.handle(event)
      is DomainOutboxEvent.GenerateTestData -> testDataGeneratorPort.handle(event)
      is DomainOutboxEvent.RecomputeAggregation -> aggregationPort.handle(event)
    }
  }

  private fun handleDomainOperation(taskDescription: String, operation: () -> Either<DomainError, Unit>): DispatchResult = try {
    when (val result = operation()) {
      is Either.Right -> DispatchResult.Success
      is Either.Left -> {
        logger.error { "Failed $taskDescription: ${result.value.code}" }
        DispatchResult.Failed("Failed $taskDescription: ${result.value.code}")
      }
    }
  } catch (e: Exception) {
    logger.error(e) { "Unexpected error in $taskDescription" }
    DispatchResult.Failed("Unexpected error in $taskDescription: ${e.message}", e)
  }

  companion object : KLogging()
}
