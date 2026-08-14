package de.chrgroth.james.platform.domain.model.imports

import java.time.Instant

@JvmInline
value class ImportConnectionId(val value: String)

/**
 * A reusable, user-owned source configuration for [ImportJob]s: a name, a URL, and an optional encrypted bearer
 * token. Connections (and the credentials they hold) are independent of any single job and stay available until
 * the user deletes them manually - only jobs are cleaned up automatically after prolonged inactivity.
 */
data class ImportConnection(
  val id: ImportConnectionId,
  val userId: String,
  val name: String,
  val url: String,
  val encryptedBearerToken: String?,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)
