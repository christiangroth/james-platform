package de.chrgroth.james.platform.domain.imports

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.TokenError
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.imports.ImportDocument
import de.chrgroth.james.platform.domain.model.imports.ImportDocumentId
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportDocumentRepositoryPort
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

class ImportServiceTests {

  private val installedAppRepository = mockk<InstalledAppRepositoryPort>()
  private val importDocumentRepository = mockk<ImportDocumentRepositoryPort>()
  private val importFetch = mockk<ImportFetchPort>()
  private val tokenEncryption = mockk<TokenEncryptionPort>()

  private val service = ImportService(installedAppRepository, importDocumentRepository, importFetch, tokenEncryption)

  private val installedApp = InstalledApp(
    id = InstalledAppId("installed-1"),
    userId = "user-1",
    appId = AppId("app-1"),
    installedVersionNumber = VersionNumber("1.0.0"),
    installedAt = Instant.now(),
  )

  @Test
  fun `trigger import succeeds and stores encrypted token and raw payload`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"foo":"bar"}""".right()
    every { tokenEncryption.encrypt("secret-token") } returns "encrypted-token".right()
    val saved = slot<ImportDocument>()
    justRun { importDocumentRepository.save(capture(saved)) }

    val result = service.triggerImport("user-1", "installed-1", "https://example.com/data", "secret-token")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.installedAppId).isEqualTo(InstalledAppId("installed-1"))
    assertThat(saved.captured.userId).isEqualTo("user-1")
    assertThat(saved.captured.sourceUrl).isEqualTo("https://example.com/data")
    assertThat(saved.captured.encryptedBearerToken).isEqualTo("encrypted-token")
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DOWNLOADED)
    assertThat(saved.captured.payload).isEqualTo("""{"foo":"bar"}""")
    verify(exactly = 1) { importDocumentRepository.save(any()) }
  }

  @Test
  fun `trigger import fails when installed app is not found`() {
    every { installedAppRepository.findById(InstalledAppId("unknown")) } returns null

    val result = service.triggerImport("user-1", "unknown", "https://example.com/data", "secret-token")

    assertThat(result).isEqualTo(ImportError.INSTALLED_APP_NOT_FOUND.left())
  }

  @Test
  fun `trigger import fails when installed app belongs to another user`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp

    val result = service.triggerImport("someone-else", "installed-1", "https://example.com/data", "secret-token")

    assertThat(result).isEqualTo(ImportError.INSTALLED_APP_NOT_FOUND.left())
  }

  @Test
  fun `trigger import fails with blank url`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp

    val result = service.triggerImport("user-1", "installed-1", "  ", "secret-token")

    assertThat(result).isEqualTo(ImportError.BLANK_URL.left())
  }

  @Test
  fun `trigger import fails with blank bearer token`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp

    val result = service.triggerImport("user-1", "installed-1", "https://example.com/data", " ")

    assertThat(result).isEqualTo(ImportError.BLANK_BEARER_TOKEN.left())
  }

  @Test
  fun `trigger import propagates fetch failure`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns ImportError.FETCH_FAILED.left()

    val result = service.triggerImport("user-1", "installed-1", "https://example.com/data", "secret-token")

    assertThat(result).isEqualTo(ImportError.FETCH_FAILED.left())
  }

  @Test
  fun `trigger import fails when response is not valid JSON`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns "not json".right()

    val result = service.triggerImport("user-1", "installed-1", "https://example.com/data", "secret-token")

    assertThat(result).isEqualTo(ImportError.INVALID_JSON_RESPONSE.left())
  }

  @Test
  fun `trigger import fails when response is a JSON array instead of an object`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """[1,2,3]""".right()

    val result = service.triggerImport("user-1", "installed-1", "https://example.com/data", "secret-token")

    assertThat(result).isEqualTo(ImportError.NOT_A_JSON_OBJECT.left())
  }

  @Test
  fun `trigger import propagates token encryption failure`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"foo":"bar"}""".right()
    every { tokenEncryption.encrypt("secret-token") } returns TokenError.ENCRYPTION_FAILED.left()

    val result = service.triggerImport("user-1", "installed-1", "https://example.com/data", "secret-token")

    assertThat(result).isEqualTo(TokenError.ENCRYPTION_FAILED.left())
  }

  @Test
  fun `list import documents returns documents sorted by newest first`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val older = importDocument(createdAt = Instant.now().minusSeconds(60))
    val newer = importDocument(createdAt = Instant.now())
    every { importDocumentRepository.findAllByInstalledAppId(InstalledAppId("installed-1")) } returns listOf(older, newer)

    val result = service.listImportDocuments("user-1", "installed-1")

    assertThat(result.getOrNull()).containsExactly(newer, older)
  }

  @Test
  fun `list import documents fails for unowned installed app`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp

    val result = service.listImportDocuments("someone-else", "installed-1")

    assertThat(result).isEqualTo(ImportError.INSTALLED_APP_NOT_FOUND.left())
  }

  @Test
  fun `delete import document succeeds for owned document`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val doc = importDocument()
    every { importDocumentRepository.findById(doc.id) } returns doc
    justRun { importDocumentRepository.delete(doc.id) }

    val result = service.deleteImportDocument("user-1", "installed-1", doc.id.value)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { importDocumentRepository.delete(doc.id) }
  }

  @Test
  fun `delete import document fails when document does not exist`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { importDocumentRepository.findById(ImportDocumentId("missing")) } returns null

    val result = service.deleteImportDocument("user-1", "installed-1", "missing")

    assertThat(result).isEqualTo(ImportError.IMPORT_DOCUMENT_NOT_FOUND.left())
  }

  @Test
  fun `delete import document fails when document belongs to another installed app`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val doc = importDocument(installedAppId = InstalledAppId("other-installed-app"))
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.deleteImportDocument("user-1", "installed-1", doc.id.value)

    assertThat(result).isEqualTo(ImportError.IMPORT_DOCUMENT_NOT_FOUND.left())
  }

  private fun importDocument(
    installedAppId: InstalledAppId = InstalledAppId("installed-1"),
    createdAt: Instant = Instant.now(),
  ) = ImportDocument(
    id = ImportDocumentId("doc-${System.nanoTime()}"),
    userId = "user-1",
    installedAppId = installedAppId,
    sourceUrl = "https://example.com/data",
    encryptedBearerToken = "encrypted-token",
    status = ImportStatus.DOWNLOADED,
    payload = """{"foo":"bar"}""",
    createdAt = createdAt,
    lastChangedAt = createdAt,
  )
}
