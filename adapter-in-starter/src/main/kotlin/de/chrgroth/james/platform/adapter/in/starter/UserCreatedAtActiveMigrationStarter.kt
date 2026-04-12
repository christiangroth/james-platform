package de.chrgroth.james.platform.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.james.platform.domain.port.`in`.user.UserDataMigrationPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class UserCreatedAtActiveMigrationStarter(
  private val userDataMigration: UserDataMigrationPort,
) : Starter {

  override val id = "UserCreatedAtActiveMigrationStarter-v1"

  override fun execute() {
    userDataMigration.backfillCreatedAtAndActive()
  }
}
