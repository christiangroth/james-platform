package de.chrgroth.james.platform.domain.model.imports

import java.time.Instant

@JvmInline
value class ImportConnectionId(val value: String)

/**
 * A reusable, user-owned source configuration for [ImportJob]s: a name, a base URL, and an optional encrypted
 * bearer token. Connections (and the credentials they hold) are independent of any single job and stay available
 * until the user deletes them manually - only jobs are cleaned up automatically after prolonged inactivity.
 *
 * [baseUrl] alone is used as-is by a job unless that job also carries an [ImportJob.urlPostfix], in which case the
 * two are concatenated to form the job's final fetch URL - see [resolveImportUrl].
 */
data class ImportConnection(
  val id: ImportConnectionId,
  val userId: String,
  val name: String,
  val baseUrl: String,
  val encryptedBearerToken: String?,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)

/** Concatenates a connection's [baseUrl] with a job's optional, trimmed [urlPostfix] to form the URL a job actually fetches from. */
fun resolveImportUrl(baseUrl: String, urlPostfix: String?): String = baseUrl + urlPostfix.orEmpty()
