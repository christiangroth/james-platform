package de.chrgroth.james.platform.adapter.`in`.web

import arrow.core.Either
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.ImportFetchFailedError
import de.chrgroth.james.platform.domain.error.ImportInvalidUrlError
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URI

class ImportFetchAdapterTests {

  private val adapter = ImportFetchAdapter()
  private var server: HttpServer? = null

  @AfterEach
  fun tearDown() {
    server?.stop(0)
  }

  @Test
  fun `fetch rejects loopback host`() {
    val result = adapter.fetch("http://127.0.0.1:1/data", "token")
    assertThat(result.isLeft()).isTrue()
    val error = (result as Either.Left).value
    assertThat(error.code).isEqualTo(ImportError.INVALID_URL.code)
    assertThat((error as ImportInvalidUrlError).detail).isNotBlank()
  }

  @Test
  fun `fetch rejects localhost hostname`() {
    val result = adapter.fetch("http://localhost:1/data", "token")
    assertThat(result.isLeft()).isTrue()
    val error = (result as Either.Left).value
    assertThat(error.code).isEqualTo(ImportError.INVALID_URL.code)
    assertThat((error as ImportInvalidUrlError).detail).isNotBlank()
  }

  @Test
  fun `fetch rejects cloud metadata address`() {
    val result = adapter.fetch("http://169.254.169.254/latest/meta-data", "token")
    assertThat(result.isLeft()).isTrue()
    val error = (result as Either.Left).value
    assertThat(error.code).isEqualTo(ImportError.INVALID_URL.code)
    assertThat((error as ImportInvalidUrlError).detail).isNotBlank()
  }

  @Test
  fun `fetch rejects unsupported scheme`() {
    val result = adapter.fetch("ftp://example.com/data", "token")
    assertThat(result.isLeft()).isTrue()
    val error = (result as Either.Left).value
    assertThat(error.code).isEqualTo(ImportError.INVALID_URL.code)
    assertThat((error as ImportInvalidUrlError).detail).contains("ftp")
  }

  @Test
  fun `fetch rejects malformed url`() {
    val result = adapter.fetch("not a url", "token")
    assertThat(result.isLeft()).isTrue()
    val error = (result as Either.Left).value
    assertThat(error.code).isEqualTo(ImportError.INVALID_URL.code)
    assertThat((error as ImportInvalidUrlError).detail).isNotBlank()
  }

  @Test
  fun `performRequest returns body and sends bearer token`() {
    var receivedAuthHeader: String? = null
    val httpServer = startServer { exchange ->
      receivedAuthHeader = exchange.requestHeaders.getFirst("Authorization")
      val body = """{"foo":"bar"}""".toByteArray()
      exchange.sendResponseHeaders(200, body.size.toLong())
      exchange.responseBody.use { it.write(body) }
    }

    val result = adapter.performRequest(URI.create("http://127.0.0.1:${httpServer.address.port}/data"), "secret-token")

    assertThat(result.isRight()).isTrue()
    assertThat((result as Either.Right).value).isEqualTo("""{"foo":"bar"}""")
    assertThat(receivedAuthHeader).isEqualTo("Bearer secret-token")
  }

  @Test
  fun `performRequest fails on non-2xx status`() {
    val httpServer = startServer { exchange ->
      exchange.sendResponseHeaders(500, -1)
      exchange.close()
    }

    val result = adapter.performRequest(URI.create("http://127.0.0.1:${httpServer.address.port}/data"), "token")

    assertThat(result.isLeft()).isTrue()
    val error = (result as Either.Left).value
    assertThat(error.code).isEqualTo(ImportError.FETCH_FAILED.code)
    assertThat((error as ImportFetchFailedError).detail).contains("500")
  }

  @Test
  fun `performRequest fails when response exceeds the size limit`() {
    val oversized = ByteArray(MAX_RESPONSE_BYTES_FOR_TEST + 1)
    val httpServer = startServer { exchange ->
      exchange.sendResponseHeaders(200, oversized.size.toLong())
      exchange.responseBody.use { it.write(oversized) }
    }

    val result = adapter.performRequest(URI.create("http://127.0.0.1:${httpServer.address.port}/data"), "token")

    assertThat(result.isLeft()).isTrue()
    assertThat((result as Either.Left).value).isEqualTo(ImportError.RESPONSE_TOO_LARGE)
  }

  private fun startServer(handler: (HttpExchange) -> Unit): HttpServer {
    val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    httpServer.createContext("/data", handler)
    httpServer.start()
    server = httpServer
    return httpServer
  }

  companion object {
    // mirrors the private MAX_RESPONSE_BYTES constant in ImportFetchAdapter
    private const val MAX_RESPONSE_BYTES_FOR_TEST = 5 * 1024 * 1024
  }
}
