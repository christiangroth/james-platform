package de.chrgroth.james.platform.domain.user

import de.chrgroth.james.platform.domain.port.`in`.user.UserDataMigrationPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging

@ApplicationScoped
@Suppress("Unused")
class UserDataMigrationService(
  private val userRepository: UserRepositoryPort,
) : UserDataMigrationPort {

  override fun backfillCreatedAtAndActive() {
    logger.info { "Backfilling missing createdAt and active fields for user documents" }
    userRepository.backfillCreatedAtAndActive()
    logger.info { "Backfill of createdAt and active fields completed" }
  }

  override fun backfillUserIds() {
    logger.info { "Backfilling missing user ids for user documents" }
    userRepository.backfillUserIds()
    logger.info { "Backfill of user ids completed" }
  }

  companion object : KLogging()
}
