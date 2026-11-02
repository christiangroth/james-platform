package de.chrgroth.james.platform.domain.model.imports

import java.net.URI
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
 *
 * [testUrl] is an optional, absolute URL the connection test requests instead of [baseUrl] - for sources whose base
 * URL only answers together with a postfix. It must share [baseUrl]'s origin (scheme, host, port), so the stored
 * bearer token is never sent to another server - see [isSameOrigin].
 */
data class ImportConnection(
  val id: ImportConnectionId,
  val userId: String,
  val name: String,
  val baseUrl: String,
  val encryptedBearerToken: String?,
  val createdAt: Instant,
  val lastChangedAt: Instant,
  val testUrl: String? = null,
)

/** Concatenates a connection's [baseUrl] with a job's optional, trimmed [urlPostfix] to form the URL a job actually fetches from. */
fun resolveImportUrl(baseUrl: String, urlPostfix: String?): String = baseUrl + urlPostfix.orEmpty()

/** Whether [url] is a valid absolute URL with the same scheme, host and (effective) port as [baseUrl]. */
fun isSameOrigin(baseUrl: String, url: String): Boolean {
  val base = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return false
  val other = runCatching { URI(url.trim()) }.getOrNull() ?: return false
  if (base.scheme == null || base.host == null || other.scheme == null || other.host == null) return false
  return base.scheme.equals(other.scheme, ignoreCase = true) &&
    base.host.equals(other.host, ignoreCase = true) &&
    effectivePort(base) == effectivePort(other)
}

private fun effectivePort(uri: URI): Int = when {
  uri.port != -1 -> uri.port
  uri.scheme.equals("https", ignoreCase = true) -> 443
  uri.scheme.equals("http", ignoreCase = true) -> 80
  else -> -1
}
