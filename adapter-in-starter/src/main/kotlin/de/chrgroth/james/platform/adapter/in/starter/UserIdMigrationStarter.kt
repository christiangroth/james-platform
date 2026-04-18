package de.chrgroth.james.platform.adapter.`in`.starter

import de.chrgroth.james.platform.domain.port.`in`.user.UserDataMigrationPort
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class UserIdMigrationStarter(
  private val userDataMigration: UserDataMigrationPort,
) : Starter {

  override val id = "UserIdMigrationStarter-v1"

  override fun execute() {
    userDataMigration.backfillUserIds()
  }
}
