package de.chrgroth.james.platform.adapter.out.postgres.app

import arrow.core.ValidatedNel
import arrow.core.invalidNel
import arrow.core.valid
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.adapter.out.postgres.app.jooq.tables.Apps.APPS
import de.chrgroth.james.platform.adapter.out.postgres.app.jooq.tables.records.AppsRecord
import de.chrgroth.james.platform.domain.app.App
import de.chrgroth.james.platform.domain.app.AppDomainErrorCodes
import de.chrgroth.james.platform.domain.app.AppId
import de.chrgroth.james.platform.domain.app.port.`in`.DomainAppEvents
import de.chrgroth.james.platform.domain.app.port.`in`.EVENT_TOPIC_TO_DOMAIN_APP
import de.chrgroth.james.platform.domain.app.port.out.AppPersistencePort
import io.quarkus.runtime.StartupEvent
import io.vertx.core.eventbus.EventBus
import jakarta.annotation.Priority
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import mu.KLogging
import org.jooq.DSLContext
import org.jooq.exception.IntegrityConstraintViolationException
import java.sql.SQLIntegrityConstraintViolationException

@ApplicationScoped
@Suppress("Unused")
class AppPersistenceAdapter : AppPersistencePort {

  @Inject
  private lateinit var eventBus: EventBus

  @Inject
  @AppDatabase
  lateinit var dsl: DSLContext

  @Suppress("Unused", "UnusedParameter")
  fun startup(@Observes @Priority(2) event: StartupEvent) {
    eventBus.publish(EVENT_TOPIC_TO_DOMAIN_APP, DomainAppEvents.PersistenceInitialized)
  }

  override fun byId(id: AppId): ValidatedNel<DomainError, App?> = runCatching {
    dsl.selectFrom(APPS)
      .where(APPS.ID.eq(id.value))
      .fetchOne()
      ?.let { record ->
        App.fromEntity(
          id = AppId(record.id!!)
        )
      }
  }.fold(
    onSuccess = { it.valid() },
    onFailure = {
      DomainError(
        code = AppDomainErrorCodes.APP_QUERY_FAILED,
        errorMessage = it.message
      ).invalidNel()
    }
  )

  override fun all(): ValidatedNel<DomainError, Set<App>> = runCatching {
    dsl.selectFrom(APPS)
      .fetch()
      .map { it.toDomain() }
      .toSet()
  }.fold(
    onSuccess = { it.valid() },
    onFailure = {
      DomainError(
        code = AppDomainErrorCodes.APP_QUERY_FAILED,
        errorMessage = it.message
      ).invalidNel()
    }
  )

  override fun create(app: App): ValidatedNel<DomainError, Unit> = runCatching {
    dsl.insertInto(APPS)
      .set(APPS.ID, app.id.value)
      .execute()
    Unit
  }.fold(
    onSuccess = { it.valid() },
    onFailure = { ex ->
      when {
        // PostgreSQL Unique Constraint Violation
        ex is IntegrityConstraintViolationException || ex.cause is SQLIntegrityConstraintViolationException -> {
          DomainError(
            code = AppDomainErrorCodes.APP_EXISTS,
            errorMessage = null
          )
        }

        else -> {
          DomainError(
            code = AppDomainErrorCodes.APP_QUERY_FAILED,
            errorMessage = ex.message
          )
        }
      }.invalidNel()
    }
  )

  private fun AppsRecord.toDomain(): App =
    App.fromEntity(
      id = AppId(id)
    )

  companion object : KLogging()
}
