package de.chrgroth.james.platform.adapter.out.postgres.user

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.driver.jdbc.asJdbcDriver
import de.chrgroth.james.platform.domain.user.port.`in`.DomainUserEvents
import de.chrgroth.james.platform.domain.user.port.`in`.EVENT_TOPIC_TO_DOMAIN_USER
import io.agroal.api.AgroalDataSource
import io.quarkus.runtime.StartupEvent
import io.vertx.core.eventbus.EventBus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.enterprise.inject.Produces
import jakarta.inject.Inject
import migrations.Users
import mu.KLogging


@ApplicationScoped
@Suppress("Unused")
class SqlDelightAdapter {

  @Inject
  private lateinit var defaultDataSource: AgroalDataSource

  @Inject
  private lateinit var eventBus: EventBus

  private val arrayToSetAdapter = object : ColumnAdapter<Set<String>, Array<String>> {
    override fun decode(databaseValue: Array<String>): Set<String> =
      databaseValue.toSet()

    override fun encode(value: Set<String>): Array<String> =
      value.toTypedArray()
  }

  private lateinit var db: UserDatabase

  @Suppress("Unused")
  fun startup(@Observes @Suppress("UnusedParameter") event: StartupEvent) {
    val jdbcDriver = defaultDataSource.asJdbcDriver()

    // TODO define how to deal with migrations. keep track of old version yourself?!?

    UserDatabase.Schema.create(jdbcDriver)

    /*
    UserDatabase.Schema.migrate(
      jdbcDriver,
      0,
      UserDatabase.Schema.version
    )
    */

    db = UserDatabase(
      driver = jdbcDriver,
      usersAdapter = Users.Adapter(
        rolesAdapter = arrayToSetAdapter
      )
    )

    logger.info { "UserDatabase created." }

    eventBus.publish(EVENT_TOPIC_TO_DOMAIN_USER, DomainUserEvents.PersistenceInitialized)
  }

  @Produces
  @ApplicationScoped
  fun produceUserQueries(): UserDatabase {
    return db
  }

  companion object : KLogging()
}
