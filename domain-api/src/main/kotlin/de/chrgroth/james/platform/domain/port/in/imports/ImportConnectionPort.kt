package de.chrgroth.james.platform.domain.port.`in`.imports

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.imports.ImportConnection

interface ImportConnectionPort {
  fun listConnections(userId: String): Either<DomainError, List<ImportConnection>>
  fun createConnection(userId: String, name: String, url: String, bearerToken: String?): Either<DomainError, ImportConnection>

  /** [bearerToken] is only replaced when non-blank; pass [clearBearerToken] to remove a previously stored token instead. */
  fun updateConnection(
    userId: String,
    connectionId: String,
    name: String,
    url: String,
    bearerToken: String?,
    clearBearerToken: Boolean = false,
  ): Either<DomainError, ImportConnection>

  fun deleteConnection(userId: String, connectionId: String): Either<DomainError, Unit>

  /** Performs a real request against the connection's URL using its stored credentials, without persisting anything. */
  fun testConnection(userId: String, connectionId: String): Either<DomainError, Unit>
}
