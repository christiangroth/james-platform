package de.chrgroth.james.platform.domain.imports

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.TokenError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.imports.DataPath
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.ImportDocument
import de.chrgroth.james.platform.domain.model.imports.ImportDocumentId
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.MappingIssue
import de.chrgroth.james.platform.domain.model.imports.MappingType
import de.chrgroth.james.platform.domain.model.imports.NumericRange
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
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
  private val appVersionRepository = mockk<AppVersionRepositoryPort>()

  private val service = ImportService(installedAppRepository, importDocumentRepository, importFetch, tokenEncryption, appVersionRepository)

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
    assertThat(saved.captured.detectedDataPaths).isEmpty()
    assertThat(saved.captured.selectedDataPath).isNull()
    verify(exactly = 1) { importDocumentRepository.save(any()) }
  }

  @Test
  fun `trigger import auto-selects the single detected data path`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"items":[{"a":1},{"a":2}]}""".right()
    every { tokenEncryption.encrypt("secret-token") } returns "encrypted-token".right()
    val saved = slot<ImportDocument>()
    justRun { importDocumentRepository.save(capture(saved)) }

    val result = service.triggerImport("user-1", "installed-1", "https://example.com/data", "secret-token")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DATA_IDENTIFIED)
    assertThat(saved.captured.selectedDataPath).isEqualTo("items")
    assertThat(saved.captured.detectedDataPaths).containsExactly(DataPath("items", 2))
    assertThat(saved.captured.detectedSchema).containsExactly(
      SchemaProperty("a", mapOf(SchemaPropertyType.LONG to 2), mandatory = true, numericRange = NumericRange(min = 1.0, max = 2.0)),
    )
  }

  @Test
  fun `trigger import stays downloaded and stores all candidates when multiple data paths are detected`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"a":[{"x":1}],"b":[{"y":1},{"y":2}]}""".right()
    every { tokenEncryption.encrypt("secret-token") } returns "encrypted-token".right()
    val saved = slot<ImportDocument>()
    justRun { importDocumentRepository.save(capture(saved)) }

    val result = service.triggerImport("user-1", "installed-1", "https://example.com/data", "secret-token")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DOWNLOADED)
    assertThat(saved.captured.selectedDataPath).isNull()
    assertThat(saved.captured.detectedDataPaths).containsExactlyInAnyOrder(DataPath("a", 1), DataPath("b", 2))
    assertThat(saved.captured.detectedSchema).isEmpty()
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

  @Test
  fun `select data path succeeds for a valid path and identifies the data`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val doc = importDocument(payload = """{"items":[{"a":1},{"a":2}]}""")
    every { importDocumentRepository.findById(doc.id) } returns doc
    val saved = slot<ImportDocument>()
    justRun { importDocumentRepository.save(capture(saved)) }

    val result = service.selectDataPath("user-1", "installed-1", doc.id.value, "items")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DATA_IDENTIFIED)
    assertThat(saved.captured.selectedDataPath).isEqualTo("items")
    assertThat(saved.captured.detectedSchema).containsExactly(
      SchemaProperty("a", mapOf(SchemaPropertyType.LONG to 2), mandatory = true, numericRange = NumericRange(min = 1.0, max = 2.0)),
    )
  }

  @Test
  fun `select data path fails for a blank path`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val doc = importDocument()
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.selectDataPath("user-1", "installed-1", doc.id.value, " ")

    assertThat(result).isEqualTo(ImportError.BLANK_DATA_PATH.left())
  }

  @Test
  fun `select data path fails for a path that does not resolve to an array of objects`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val doc = importDocument(payload = """{"foo":"bar"}""")
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.selectDataPath("user-1", "installed-1", doc.id.value, "foo")

    assertThat(result).isEqualTo(ImportError.INVALID_DATA_PATH.left())
  }

  @Test
  fun `select data path fails when document is not in DOWNLOADED status`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val doc = importDocument(status = ImportStatus.DATA_IDENTIFIED)
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.selectDataPath("user-1", "installed-1", doc.id.value, "items")

    assertThat(result).isEqualTo(ImportError.IMPORT_DOCUMENT_NOT_DOWNLOADED.left())
  }

  @Test
  fun `select data path fails when document does not exist`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { importDocumentRepository.findById(ImportDocumentId("missing")) } returns null

    val result = service.selectDataPath("user-1", "installed-1", "missing", "items")

    assertThat(result).isEqualTo(ImportError.IMPORT_DOCUMENT_NOT_FOUND.left())
  }

  @Test
  fun `select data path fails when document belongs to another installed app`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val doc = importDocument(installedAppId = InstalledAppId("other-installed-app"))
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.selectDataPath("user-1", "installed-1", doc.id.value, "items")

    assertThat(result).isEqualTo(ImportError.IMPORT_DOCUMENT_NOT_FOUND.left())
  }

  @Test
  fun `update mapping succeeds and transitions to READY when mapping is complete and valid`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val doc = importDocument(
      status = ImportStatus.DATA_IDENTIFIED,
      detectedSchema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = true)),
    )
    every { importDocumentRepository.findById(doc.id) } returns doc
    val saved = slot<ImportDocument>()
    justRun { importDocumentRepository.save(capture(saved)) }

    val result = service.updateMapping(
      "user-1",
      "installed-1",
      doc.id.value,
      "Contact",
      MappingType.FIND,
      "entity-1",
      listOf(FieldMapping(targetPropertyId = PropertyId("prop-1"), sourcePath = "name")),
    )

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.READY)
    assertThat(saved.captured.mapping?.name).isEqualTo("Contact")
    val view = result.getOrNull()!!
    assertThat(view.validation?.isReady).isTrue()
  }

  @Test
  fun `update mapping stays DATA_IDENTIFIED and reports a missing mandatory field`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val doc = importDocument(status = ImportStatus.DATA_IDENTIFIED)
    every { importDocumentRepository.findById(doc.id) } returns doc
    val saved = slot<ImportDocument>()
    justRun { importDocumentRepository.save(capture(saved)) }

    val result = service.updateMapping("user-1", "installed-1", doc.id.value, "Contact", MappingType.FIND, "entity-1", emptyList())

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DATA_IDENTIFIED)
    assertThat(result.getOrNull()?.validation?.blockingIssues).containsExactly(MappingIssue.MissingMandatoryField(PropertyId("prop-1")))
  }

  @Test
  fun `update mapping fails when entity definition is not found`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val doc = importDocument(status = ImportStatus.DATA_IDENTIFIED)
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.updateMapping("user-1", "installed-1", doc.id.value, "Contact", MappingType.FIND, "unknown-entity", emptyList())

    assertThat(result).isEqualTo(ImportError.ENTITY_DEFINITION_NOT_FOUND.left())
  }

  @Test
  fun `update mapping fails when a field mapping targets an unknown property`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val doc = importDocument(status = ImportStatus.DATA_IDENTIFIED)
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.updateMapping(
      "user-1",
      "installed-1",
      doc.id.value,
      "Contact",
      MappingType.FIND,
      "entity-1",
      listOf(FieldMapping(targetPropertyId = PropertyId("unknown-prop"), sourcePath = "name")),
    )

    assertThat(result).isEqualTo(ImportError.MAPPING_PROPERTY_NOT_FOUND.left())
  }

  @Test
  fun `update mapping fails with a blank name`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val doc = importDocument(status = ImportStatus.DATA_IDENTIFIED)
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.updateMapping("user-1", "installed-1", doc.id.value, " ", MappingType.FIND, "entity-1", emptyList())

    assertThat(result).isEqualTo(ImportError.BLANK_MAPPING_NAME.left())
  }

  @Test
  fun `update mapping fails when document has no data path selected yet`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val doc = importDocument(status = ImportStatus.DOWNLOADED)
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.updateMapping("user-1", "installed-1", doc.id.value, "Contact", MappingType.FIND, "entity-1", emptyList())

    assertThat(result).isEqualTo(ImportError.IMPORT_DOCUMENT_NOT_MAPPABLE.left())
  }

  @Test
  fun `get mapping view returns entity definitions and no validation when nothing is mapped yet`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val doc = importDocument(status = ImportStatus.DATA_IDENTIFIED)
    every { importDocumentRepository.findById(doc.id) } returns doc

    val result = service.getMappingView("user-1", "installed-1", doc.id.value)

    assertThat(result.isRight()).isTrue()
    val view = result.getOrNull()!!
    assertThat(view.entityDefinitions).containsExactly(entityDefinition)
    assertThat(view.validation).isNull()
  }

  private val entityDefinition = EntityDefinition(
    id = EntityDefinitionId("entity-1"),
    name = "Contact",
    properties = listOf(
      Property(id = PropertyId("prop-1"), name = "Name", type = PropertyType.STRING, nullable = false),
    ),
  )

  private val appVersion = AppVersion(
    id = AppVersionId("version-1"),
    appId = AppId("app-1"),
    versionNumber = VersionNumber("1.0.0"),
    releaseNotes = null,
    entityDefinitions = listOf(entityDefinition),
    reports = emptyList(),
    status = AppVersionStatus.PUBLISHED,
    createdAt = Instant.now(),
  )

  private fun importDocument(
    installedAppId: InstalledAppId = InstalledAppId("installed-1"),
    createdAt: Instant = Instant.now(),
    status: ImportStatus = ImportStatus.DOWNLOADED,
    payload: String = """{"foo":"bar"}""",
    detectedSchema: List<SchemaProperty> = emptyList(),
  ) = ImportDocument(
    id = ImportDocumentId("doc-${System.nanoTime()}"),
    userId = "user-1",
    installedAppId = installedAppId,
    sourceUrl = "https://example.com/data",
    encryptedBearerToken = "encrypted-token",
    status = status,
    payload = payload,
    detectedSchema = detectedSchema,
    createdAt = createdAt,
    lastChangedAt = createdAt,
  )
}
