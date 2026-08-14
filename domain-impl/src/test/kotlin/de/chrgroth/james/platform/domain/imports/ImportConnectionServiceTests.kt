package de.chrgroth.james.platform.domain.imports

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.ImportConnectionError
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.TokenError
import de.chrgroth.james.platform.domain.model.imports.ImportConnection
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.port.out.imports.ImportConnectionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.user.TokenEncryptionPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ImportConnectionServiceTests {

  private val importConnectionRepository = mockk<ImportConnectionRepositoryPort>()
  private val importFetch = mockk<ImportFetchPort>()
  private val tokenEncryption = mockk<TokenEncryptionPort>()

  private val service = ImportConnectionService(importConnectionRepository, importFetch, tokenEncryption)

  private val connection = ImportConnection(
    id = ImportConnectionId("conn-1"),
    userId = "user-1",
    name = "My API",
    url = "https://example.com/data",
    encryptedBearerToken = "encrypted-token",
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
  )

  @Test
  fun `list connections returns connections sorted by name`() {
    val zeta = connection.copy(id = ImportConnectionId("conn-z"), name = "Zeta")
    val alpha = connection.copy(id = ImportConnectionId("conn-a"), name = "alpha")
    every { importConnectionRepository.findAllByUserId("user-1") } returns listOf(zeta, alpha)

    val result = service.listConnections("user-1")

    assertThat(result.getOrNull()).containsExactly(alpha, zeta)
  }

  @Test
  fun `create connection encrypts the bearer token and saves`() {
    every { tokenEncryption.encrypt("secret-token") } returns "encrypted-token".right()
    val saved = slot<ImportConnection>()
    justRun { importConnectionRepository.save(capture(saved)) }

    val result = service.createConnection("user-1", "My API", "https://example.com/data", "secret-token")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.userId).isEqualTo("user-1")
    assertThat(saved.captured.name).isEqualTo("My API")
    assertThat(saved.captured.url).isEqualTo("https://example.com/data")
    assertThat(saved.captured.encryptedBearerToken).isEqualTo("encrypted-token")
  }

  @Test
  fun `create connection succeeds without a bearer token`() {
    val saved = slot<ImportConnection>()
    justRun { importConnectionRepository.save(capture(saved)) }

    val result = service.createConnection("user-1", "My API", "https://example.com/data", null)

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.encryptedBearerToken).isNull()
    verify(exactly = 0) { tokenEncryption.encrypt(any()) }
  }

  @Test
  fun `create connection fails with a blank name`() {
    val result = service.createConnection("user-1", " ", "https://example.com/data", null)

    assertThat(result).isEqualTo(ImportConnectionError.BLANK_NAME.left())
  }

  @Test
  fun `create connection fails with a blank url`() {
    val result = service.createConnection("user-1", "My API", " ", null)

    assertThat(result).isEqualTo(ImportConnectionError.BLANK_URL.left())
  }

  @Test
  fun `create connection propagates token encryption failure`() {
    every { tokenEncryption.encrypt("secret-token") } returns TokenError.ENCRYPTION_FAILED.left()

    val result = service.createConnection("user-1", "My API", "https://example.com/data", "secret-token")

    assertThat(result).isEqualTo(TokenError.ENCRYPTION_FAILED.left())
  }

  @Test
  fun `update connection replaces name, url and token`() {
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.encrypt("new-token") } returns "encrypted-new-token".right()
    val saved = slot<ImportConnection>()
    justRun { importConnectionRepository.save(capture(saved)) }

    val result = service.updateConnection("user-1", "conn-1", "Renamed", "https://example.com/v2", "new-token")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.name).isEqualTo("Renamed")
    assertThat(saved.captured.url).isEqualTo("https://example.com/v2")
    assertThat(saved.captured.encryptedBearerToken).isEqualTo("encrypted-new-token")
  }

  @Test
  fun `update connection keeps the existing token when a blank token is passed`() {
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    val saved = slot<ImportConnection>()
    justRun { importConnectionRepository.save(capture(saved)) }

    val result = service.updateConnection("user-1", "conn-1", "Renamed", "https://example.com/v2", " ")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.encryptedBearerToken).isEqualTo("encrypted-token")
    verify(exactly = 0) { tokenEncryption.encrypt(any()) }
  }

  @Test
  fun `update connection clears the token when explicitly requested`() {
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    val saved = slot<ImportConnection>()
    justRun { importConnectionRepository.save(capture(saved)) }

    val result = service.updateConnection("user-1", "conn-1", "Renamed", "https://example.com/v2", null, clearBearerToken = true)

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.encryptedBearerToken).isNull()
  }

  @Test
  fun `update connection fails when connection does not exist`() {
    every { importConnectionRepository.findById(ImportConnectionId("missing")) } returns null

    val result = service.updateConnection("user-1", "missing", "Renamed", "https://example.com/v2", null)

    assertThat(result).isEqualTo(ImportConnectionError.CONNECTION_NOT_FOUND.left())
  }

  @Test
  fun `update connection fails when connection belongs to another user`() {
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection

    val result = service.updateConnection("someone-else", "conn-1", "Renamed", "https://example.com/v2", null)

    assertThat(result).isEqualTo(ImportConnectionError.CONNECTION_NOT_FOUND.left())
  }

  @Test
  fun `delete connection succeeds for owned connection`() {
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    justRun { importConnectionRepository.delete(connection.id) }

    val result = service.deleteConnection("user-1", "conn-1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { importConnectionRepository.delete(connection.id) }
  }

  @Test
  fun `delete connection fails when connection belongs to another user`() {
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection

    val result = service.deleteConnection("someone-else", "conn-1")

    assertThat(result).isEqualTo(ImportConnectionError.CONNECTION_NOT_FOUND.left())
  }

  @Test
  fun `test connection decrypts the token and performs a real fetch`() {
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"ok":true}""".right()

    val result = service.testConnection("user-1", "conn-1")

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `test connection fetches with an empty token when the connection has none`() {
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection.copy(encryptedBearerToken = null)
    every { importFetch.fetch("https://example.com/data", "") } returns """{"ok":true}""".right()

    val result = service.testConnection("user-1", "conn-1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { tokenEncryption.decrypt(any()) }
  }

  @Test
  fun `test connection propagates fetch failure`() {
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns ImportError.FETCH_FAILED.left()

    val result = service.testConnection("user-1", "conn-1")

    assertThat(result).isEqualTo(ImportError.FETCH_FAILED.left())
  }

  @Test
  fun `test connection fails when connection does not exist`() {
    every { importConnectionRepository.findById(ImportConnectionId("missing")) } returns null

    val result = service.testConnection("user-1", "missing")

    assertThat(result).isEqualTo(ImportConnectionError.CONNECTION_NOT_FOUND.left())
  }
}
