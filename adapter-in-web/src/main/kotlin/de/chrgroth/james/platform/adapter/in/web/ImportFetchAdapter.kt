package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.ImportFetchFailedError
import de.chrgroth.james.platform.domain.error.ImportInvalidUrlError
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Fetches a JSON document from a user-supplied URL. Since the URL is attacker-controlled, this adapter
 * protects against SSRF: only `http`/`https` schemes are allowed, the resolved target address must not fall
 * into a loopback/link-local/site-local/multicast range (this also covers the `169.254.169.254` cloud metadata
 * endpoint, which lies within the link-local range), redirects are never followed (a public URL could otherwise
 * redirect to a blocked target after the initial host check passed), and the response is read up to a fixed
 * byte limit rather than buffered without bound.
 */
@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class ImportFetchAdapter : ImportFetchPort {

  override fun fetch(url: String, bearerToken: String): Either<DomainError, String> {
    val uri = validate(url).fold({ return it.left() }, { it })
    return performRequest(uri, bearerToken)
  }

  private fun validate(url: String): Either<DomainError, URI> {
    val uri = try {
      URI.create(url)
    } catch (e: Exception) {
      logger.warn { "Import fetch rejected: malformed URL" }
      return ImportInvalidUrlError("Malformed URL: ${e.message}").left()
    }
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") {
      logger.warn { "Import fetch rejected: unsupported scheme: $scheme" }
      return ImportInvalidUrlError("Unsupported URL scheme '${scheme ?: uri}'. Only http and https are allowed.").left()
    }
    val host = uri.host
    if (host.isNullOrBlank()) {
      logger.warn { "Import fetch rejected: missing host in URL" }
      return ImportInvalidUrlError("URL has no host.").left()
    }
    val addresses = try {
      InetAddress.getAllByName(host)
    } catch (e: Exception) {
      logger.warn { "Import fetch rejected: could not resolve host: $host" }
      return ImportInvalidUrlError("Host could not be resolved: $host").left()
    }
    if (addresses.isEmpty() || addresses.any { isBlockedAddress(it) }) {
      logger.warn { "Import fetch rejected: host resolves to a blocked address range: $host" }
      return ImportInvalidUrlError("Host '$host' resolves to a disallowed address range (e.g. loopback, link-local or private network).").left()
    }
    return uri.right()
  }

  private fun isBlockedAddress(address: InetAddress): Boolean =
    address.isLoopbackAddress ||
      address.isLinkLocalAddress ||
      address.isSiteLocalAddress ||
      address.isMulticastAddress ||
      address.isAnyLocalAddress

  internal fun performRequest(uri: URI, bearerToken: String): Either<DomainError, String> = try {
    val request = HttpRequest.newBuilder()
      .uri(uri)
      .timeout(REQUEST_TIMEOUT)
      .header("Authorization", "Bearer $bearerToken")
      .header("Accept", "application/json")
      .GET()
      .build()
    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
    response.body().use { input ->
      if (response.statusCode() !in HTTP_OK_RANGE) {
        logger.warn { "Import fetch failed with status ${response.statusCode()} for url: $uri" }
        return@use ImportFetchFailedError("Server responded with HTTP status ${response.statusCode()}.").left()
      }
      val buffer = ByteArrayOutputStream()
      val chunk = ByteArray(READ_BUFFER_SIZE)
      var total = 0
      while (true) {
        val read = input.read(chunk)
        if (read == -1) break
        total += read
        if (total > MAX_RESPONSE_BYTES) {
          logger.warn { "Import fetch rejected: response exceeds size limit for url: $uri" }
          return@use ImportError.RESPONSE_TOO_LARGE.left()
        }
        buffer.write(chunk, 0, read)
      }
      buffer.toString(Charsets.UTF_8).right()
    }
  } catch (e: Exception) {
    logger.warn(e) { "Import fetch failed for url: $uri" }
    ImportFetchFailedError("${e::class.simpleName}: ${e.message ?: "no further details"}").left()
  }

  companion object : KLogging() {
    private const val MAX_RESPONSE_BYTES = 5 * 1024 * 1024
    private const val READ_BUFFER_SIZE = 8192
    private val HTTP_OK_RANGE = 200..299
    private val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(10)
    private val httpClient: HttpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build()
  }
}
