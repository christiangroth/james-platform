package de.chrgroth.james.platform.domain.imports

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.ImportConnectionError
import de.chrgroth.james.platform.domain.model.imports.ImportConnection
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.port.`in`.imports.ImportConnectionPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportConnectionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.user.TokenEncryptionPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.time.Instant
import java.util.UUID

@ApplicationScoped
@Suppress("Unused")
class ImportConnectionService(
  private val importConnectionRepository: ImportConnectionRepositoryPort,
  private val importFetch: ImportFetchPort,
  private val tokenEncryption: TokenEncryptionPort,
) : ImportConnectionPort {

  override fun listConnections(userId: String): Either<DomainError, List<ImportConnection>> =
    importConnectionRepository.findAllByUserId(userId).sortedBy { it.name.lowercase() }.right()

  override fun createConnection(userId: String, name: String, url: String, bearerToken: String?): Either<DomainError, ImportConnection> {
    if (name.isBlank()) {
      logger.warn { "Create connection failed: blank name for user: $userId" }
      return ImportConnectionError.BLANK_NAME.left()
    }
    if (url.isBlank()) {
      logger.warn { "Create connection failed: blank url for user: $userId" }
      return ImportConnectionError.BLANK_URL.left()
    }

    val encryptedBearerToken = bearerToken?.trim()?.takeIf { it.isNotBlank() }
      ?.let { tokenEncryption.encrypt(it).fold({ return it.left() }, { encrypted -> encrypted }) }

    val now = Instant.now()
    val connection = ImportConnection(
      id = ImportConnectionId(UUID.randomUUID().toString()),
      userId = userId,
      name = name.trim(),
      url = url.trim(),
      encryptedBearerToken = encryptedBearerToken,
      createdAt = now,
      lastChangedAt = now,
    )
    importConnectionRepository.save(connection)
    logger.info { "Import connection created: ${connection.id.value} for user: $userId" }
    return connection.right()
  }

  override fun updateConnection(
    userId: String,
    connectionId: String,
    name: String,
    url: String,
    bearerToken: String?,
    clearBearerToken: Boolean,
  ): Either<DomainError, ImportConnection> {
    val existing = requireOwnedConnection(userId, connectionId) ?: run {
      logger.warn { "Update connection failed: connection not found: $connectionId for user: $userId" }
      return ImportConnectionError.CONNECTION_NOT_FOUND.left()
    }
    if (name.isBlank()) {
      logger.warn { "Update connection failed: blank name for connectionId: $connectionId" }
      return ImportConnectionError.BLANK_NAME.left()
    }
    if (url.isBlank()) {
      logger.warn { "Update connection failed: blank url for connectionId: $connectionId" }
      return ImportConnectionError.BLANK_URL.left()
    }

    val trimmedToken = bearerToken?.trim()?.takeIf { it.isNotBlank() }
    val encryptedBearerToken = when {
      clearBearerToken -> null
      trimmedToken != null -> tokenEncryption.encrypt(trimmedToken).fold({ return it.left() }, { encrypted -> encrypted })
      else -> existing.encryptedBearerToken
    }

    val updated = existing.copy(
      name = name.trim(),
      url = url.trim(),
      encryptedBearerToken = encryptedBearerToken,
      lastChangedAt = Instant.now(),
    )
    importConnectionRepository.save(updated)
    logger.info { "Import connection updated: $connectionId" }
    return updated.right()
  }

  override fun deleteConnection(userId: String, connectionId: String): Either<DomainError, Unit> {
    val existing = requireOwnedConnection(userId, connectionId) ?: run {
      logger.warn { "Delete connection failed: connection not found: $connectionId for user: $userId" }
      return ImportConnectionError.CONNECTION_NOT_FOUND.left()
    }
    importConnectionRepository.delete(existing.id)
    logger.info { "Import connection deleted: $connectionId" }
    return Unit.right()
  }

  override fun testConnection(userId: String, connectionId: String): Either<DomainError, Unit> {
    val existing = requireOwnedConnection(userId, connectionId) ?: run {
      logger.warn { "Test connection failed: connection not found: $connectionId for user: $userId" }
      return ImportConnectionError.CONNECTION_NOT_FOUND.left()
    }
    val bearerToken = existing.encryptedBearerToken?.let { tokenEncryption.decrypt(it).fold({ return it.left() }, { it }) }.orEmpty()
    return importFetch.fetch(existing.url, bearerToken).fold(
      ifLeft = {
        logger.warn { "Test connection failed: fetch failed for connectionId: $connectionId" }
        it.left()
      },
      ifRight = { Unit.right() },
    )
  }

  private fun requireOwnedConnection(userId: String, connectionId: String): ImportConnection? {
    val connection = importConnectionRepository.findById(ImportConnectionId(connectionId))
    return if (connection != null && connection.userId == userId) connection else null
  }

  companion object : KLogging()
}
