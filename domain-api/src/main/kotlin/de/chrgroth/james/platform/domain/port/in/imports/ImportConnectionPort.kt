package de.chrgroth.james.platform.domain.port.`in`.imports

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.imports.ImportConnection

interface ImportConnectionPort {
  fun listConnections(userId: String): Either<DomainError, List<ImportConnection>>
  /** [testUrl] is optional (blank = test the base URL) and must share the base URL's origin. */
  fun createConnection(userId: String, name: String, baseUrl: String, bearerToken: String?, testUrl: String? = null): Either<DomainError, ImportConnection>

  /**
   * [bearerToken] is only replaced when non-blank; pass [clearBearerToken] to remove a previously stored token instead.
   * [testUrl] is always replaced (blank removes it) and must share the base URL's origin.
   */
  fun updateConnection(
    userId: String,
    connectionId: String,
    name: String,
    baseUrl: String,
    bearerToken: String?,
    clearBearerToken: Boolean = false,
    testUrl: String? = null,
  ): Either<DomainError, ImportConnection>

  fun deleteConnection(userId: String, connectionId: String): Either<DomainError, Unit>

  /** Performs a real request against the connection's test URL (or base URL if none is set) using its stored credentials, without persisting anything. */
  fun testConnection(userId: String, connectionId: String): Either<DomainError, Unit>
}
