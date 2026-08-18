package de.chrgroth.james.platform.adapter.`in`.outbox

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.outbox.DomainOutboxPartition
import de.chrgroth.james.platform.domain.port.`in`.app.UserAppStorePort
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportPort
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
) : ApplicationOutboxDispatcher {

  override fun getAllPartitions(): List<ApplicationOutboxPartition> = DomainOutboxPartition.all

  override fun deserialize(partition: ApplicationOutboxPartition, eventType: String, payload: String): ApplicationOutboxEvent =
    DomainOutboxEvent.fromKey(eventType, payload)

  override fun dispatch(event: ApplicationOutboxEvent): DispatchResult = dispatchEvent(event as DomainOutboxEvent)

  private fun dispatchEvent(event: DomainOutboxEvent): DispatchResult = handleDomainOperation(event.key) {
    when (event) {
      is DomainOutboxEvent.AcceptDryRun -> importPort.handle(event)
      is DomainOutboxEvent.UninstallApp -> userAppStorePort.handle(event)
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
