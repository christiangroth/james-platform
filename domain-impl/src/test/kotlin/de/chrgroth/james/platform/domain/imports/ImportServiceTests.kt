package de.chrgroth.james.platform.domain.imports

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.app.PropertyConstraintService
import de.chrgroth.james.platform.domain.error.ImportError
import de.chrgroth.james.platform.domain.error.TokenError
import de.chrgroth.james.platform.domain.model.app.AggregationDefinition
import de.chrgroth.james.platform.domain.model.app.AggregationDefinitionId
import de.chrgroth.james.platform.domain.model.app.AggregationFunction
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.ImportProvenance
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.imports.DataPath
import de.chrgroth.james.platform.domain.model.imports.DryRunIssue
import de.chrgroth.james.platform.domain.model.imports.FieldMapping
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import de.chrgroth.james.platform.domain.model.imports.FilterSample
import de.chrgroth.james.platform.domain.model.imports.ImportConnection
import de.chrgroth.james.platform.domain.model.imports.ImportConnectionId
import de.chrgroth.james.platform.domain.model.imports.ImportDefinition
import de.chrgroth.james.platform.domain.model.imports.ImportDefinitionId
import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportJobId
import de.chrgroth.james.platform.domain.model.imports.ImportStatus
import de.chrgroth.james.platform.domain.model.imports.ImportTrigger
import de.chrgroth.james.platform.domain.model.imports.Mapping
import de.chrgroth.james.platform.domain.model.imports.MappingIssue
import de.chrgroth.james.platform.domain.model.imports.MappingSample
import de.chrgroth.james.platform.domain.model.imports.NumericRange
import de.chrgroth.james.platform.domain.model.imports.SchemaProperty
import de.chrgroth.james.platform.domain.model.imports.SchemaPropertyType
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportConnectionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportDefinitionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportFetchPort
import de.chrgroth.james.platform.domain.port.out.imports.ImportJobRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
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
  private val importJobRepository = mockk<ImportJobRepositoryPort>()
  private val importDefinitionRepository = mockk<ImportDefinitionRepositoryPort>()
  private val importConnectionRepository = mockk<ImportConnectionRepositoryPort>()
  private val importFetch = mockk<ImportFetchPort>()
  private val tokenEncryption = mockk<TokenEncryptionPort>()
  private val appVersionRepository = mockk<AppVersionRepositoryPort>()
  private val appDataRepository = mockk<AppDataRepositoryPort>()
  private val propertyConstraint = PropertyConstraintService()
  private val outboxPort = mockk<OutboxPort>()

  private val service = ImportService(
    installedAppRepository,
    importJobRepository,
    importDefinitionRepository,
    importConnectionRepository,
    importFetch,
    tokenEncryption,
    appVersionRepository,
    appDataRepository,
    propertyConstraint,
    outboxPort,
  )

  private val installedApp = InstalledApp(
    id = InstalledAppId("installed-1"),
    userId = "user-1",
    appId = AppId("app-1"),
    installedVersionNumber = VersionNumber("1.0.0"),
    installedAt = Instant.now(),
  )

  @Test
  fun `trigger import succeeds and stores connection reference and raw payload`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"foo":"bar"}""".right()
    val saved = slot<ImportJob>()
    justRun { importJobRepository.save(capture(saved)) }
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.installedAppId).isEqualTo(InstalledAppId("installed-1"))
    assertThat(saved.captured.userId).isEqualTo("user-1")
    assertThat(saved.captured.importDefinitionId).isEqualTo(savedDefinition.captured.id)
    assertThat(savedDefinition.captured.connectionId).isEqualTo(ImportConnectionId("conn-1"))
    assertThat(savedDefinition.captured.targetEntityDefinitionId).isEqualTo(EntityDefinitionId("entity-1"))
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DOWNLOADED)
    assertThat(saved.captured.payload).isEqualTo("""{"foo":"bar"}""")
    assertThat(saved.captured.detectedDataPaths).isEmpty()
    assertThat(savedDefinition.captured.selectedDataPath).isNull()
    verify(exactly = 1) { importJobRepository.save(any()) }
  }

  @Test
  fun `trigger import fetches from the connection's base URL with the given postfix appended and stores the postfix`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data/latest", "secret-token") } returns """{"foo":"bar"}""".right()
    justRun { importJobRepository.save(any()) }
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1", "/latest")

    assertThat(result.isRight()).isTrue()
    assertThat(savedDefinition.captured.urlPostfix).isEqualTo("/latest")
    verify(exactly = 1) { importFetch.fetch("https://example.com/data/latest", "secret-token") }
  }

  @Test
  fun `trigger import treats a blank url postfix as unset and fetches the connection's base URL unchanged`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"foo":"bar"}""".right()
    justRun { importJobRepository.save(any()) }
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1", "   ")

    assertThat(result.isRight()).isTrue()
    assertThat(savedDefinition.captured.urlPostfix).isNull()
  }

  @Test
  fun `trigger import fetches with an empty token when the connection has none`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection.copy(encryptedBearerToken = null)
    every { importFetch.fetch("https://example.com/data", "") } returns """{"foo":"bar"}""".right()
    justRun { importJobRepository.save(any()) }
    justRun { importDefinitionRepository.save(any()) }

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { tokenEncryption.decrypt(any()) }
  }

  @Test
  fun `trigger import auto-selects the single detected data path`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"items":[{"a":1},{"a":2}]}""".right()
    val saved = slot<ImportJob>()
    justRun { importJobRepository.save(capture(saved)) }
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DATA_IDENTIFIED)
    assertThat(savedDefinition.captured.selectedDataPath).isEqualTo("items")
    assertThat(saved.captured.detectedDataPaths).containsExactly(DataPath("items", 2))
    assertThat(saved.captured.detectedSchema).containsExactly(
      SchemaProperty("a", mapOf(SchemaPropertyType.LONG to 2), mandatory = true, numericRange = NumericRange(min = 1.0, max = 2.0)),
    )
    assertThat(saved.captured.filteredSchema).isEqualTo(saved.captured.detectedSchema)
  }

  @Test
  fun `trigger import stays downloaded and stores all candidates when multiple data paths are detected`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"a":[{"x":1}],"b":[{"y":1},{"y":2}]}""".right()
    val saved = slot<ImportJob>()
    justRun { importJobRepository.save(capture(saved)) }
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DOWNLOADED)
    assertThat(savedDefinition.captured.selectedDataPath).isNull()
    assertThat(saved.captured.detectedDataPaths).containsExactlyInAnyOrder(DataPath("a", 1), DataPath("b", 2))
    assertThat(saved.captured.detectedSchema).isEmpty()
  }

  @Test
  fun `trigger import fails when installed app is not found`() {
    every { installedAppRepository.findById(InstalledAppId("unknown")) } returns null

    val result = service.triggerImport("user-1", "unknown", "conn-1", "entity-1")

    assertThat(result).isEqualTo(ImportError.INSTALLED_APP_NOT_FOUND.left())
  }

  @Test
  fun `trigger import fails when installed app belongs to another user`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp

    val result = service.triggerImport("someone-else", "installed-1", "conn-1", "entity-1")

    assertThat(result).isEqualTo(ImportError.INSTALLED_APP_NOT_FOUND.left())
  }

  @Test
  fun `trigger import fails when target entity definition is not found`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "unknown-entity")

    assertThat(result).isEqualTo(ImportError.ENTITY_DEFINITION_NOT_FOUND.left())
  }

  @Test
  fun `trigger import fails when connection is not found`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns null

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result).isEqualTo(ImportError.CONNECTION_NOT_FOUND.left())
  }

  @Test
  fun `trigger import fails when connection belongs to another user`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection.copy(userId = "someone-else")

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result).isEqualTo(ImportError.CONNECTION_NOT_FOUND.left())
  }

  @Test
  fun `trigger import propagates fetch failure`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns ImportError.FETCH_FAILED.left()

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result).isEqualTo(ImportError.FETCH_FAILED.left())
  }

  @Test
  fun `trigger import fails when response is not valid JSON`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns "not json".right()

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result).isEqualTo(ImportError.INVALID_JSON_RESPONSE.left())
  }

  @Test
  fun `trigger import fails when response is a JSON array instead of an object`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """[1,2,3]""".right()

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result).isEqualTo(ImportError.NOT_A_JSON_OBJECT.left())
  }

  @Test
  fun `trigger import propagates token decryption failure`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns TokenError.DECRYPTION_FAILED.left()

    val result = service.triggerImport("user-1", "installed-1", "conn-1", "entity-1")

    assertThat(result).isEqualTo(TokenError.DECRYPTION_FAILED.left())
  }

  @Test
  fun `list all import jobs returns jobs sorted by newest first`() {
    val older = importJob(createdAt = Instant.now().minusSeconds(60))
    val newer = importJob(createdAt = Instant.now())
    every { importJobRepository.findAllByUserId("user-1") } returns listOf(older, newer)

    val result = service.listAllImportJobs("user-1")

    assertThat(result).containsExactly(newer, older)
  }

  @Test
  fun `delete import job succeeds for owned job`() {
    val job = importJob()
    every { importJobRepository.findById(job.id) } returns job
    justRun { importJobRepository.delete(job.id) }

    val result = service.deleteImportJob("user-1", job.id.value)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { importJobRepository.delete(job.id) }
  }

  @Test
  fun `delete import job fails when job does not exist`() {
    every { importJobRepository.findById(ImportJobId("missing")) } returns null

    val result = service.deleteImportJob("user-1", "missing")

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `delete import job fails when job belongs to another user`() {
    val job = importJob(userId = "someone-else")
    every { importJobRepository.findById(job.id) } returns job

    val result = service.deleteImportJob("user-1", job.id.value)

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `select data path succeeds for a valid path and identifies the data`() {
    val job = importJob(payload = """{"items":[{"a":1},{"a":2}]}""")
    every { importJobRepository.findById(job.id) } returns job
    val saved = slot<ImportJob>()
    justRun { importJobRepository.save(capture(saved)) }
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }

    val result = service.selectDataPath("user-1", job.id.value, "items")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DATA_IDENTIFIED)
    assertThat(savedDefinition.captured.selectedDataPath).isEqualTo("items")
    assertThat(saved.captured.detectedSchema).containsExactly(
      SchemaProperty("a", mapOf(SchemaPropertyType.LONG to 2), mandatory = true, numericRange = NumericRange(min = 1.0, max = 2.0)),
    )
    assertThat(saved.captured.filteredSchema).isEqualTo(saved.captured.detectedSchema)
  }

  @Test
  fun `select data path fails for a blank path`() {
    val job = importJob()
    every { importJobRepository.findById(job.id) } returns job

    val result = service.selectDataPath("user-1", job.id.value, " ")

    assertThat(result).isEqualTo(ImportError.BLANK_DATA_PATH.left())
  }

  @Test
  fun `select data path fails for a path that does not resolve to an array of objects`() {
    val job = importJob(payload = """{"foo":"bar"}""")
    every { importJobRepository.findById(job.id) } returns job

    val result = service.selectDataPath("user-1", job.id.value, "foo")

    assertThat(result).isEqualTo(ImportError.INVALID_DATA_PATH.left())
  }

  @Test
  fun `select data path fails when job is not in DOWNLOADED status`() {
    val job = importJob(status = ImportStatus.DATA_IDENTIFIED)
    every { importJobRepository.findById(job.id) } returns job

    val result = service.selectDataPath("user-1", job.id.value, "items")

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_DOWNLOADED.left())
  }

  @Test
  fun `select data path fails when job does not exist`() {
    every { importJobRepository.findById(ImportJobId("missing")) } returns null

    val result = service.selectDataPath("user-1", "missing", "items")

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `select data path fails when job belongs to another user`() {
    val job = importJob(userId = "someone-else")
    every { importJobRepository.findById(job.id) } returns job

    val result = service.selectDataPath("user-1", job.id.value, "items")

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `get filter view reports total and matching record counts against the configured filter`() {
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"country":"DE"},{"country":"US"},{"country":"DE"}]}""",
      selectedDataPath = "items",
      filterRules = listOf(FilterRule(FilterMode.INCLUDE, "country", FilterOperator.EQUALS, "DE")),
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.getFilterView("user-1", job.id.value)

    assertThat(result.isRight()).isTrue()
    val view = result.getOrNull()!!
    assertThat(view.totalRecordCount).isEqualTo(3)
    assertThat(view.matchingRecordCount).isEqualTo(2)
  }

  @Test
  fun `get filter view fails when job does not exist`() {
    every { importJobRepository.findById(ImportJobId("missing")) } returns null

    val result = service.getFilterView("user-1", "missing")

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `update filter replaces the job's filter rules and reports the re-evaluated matching count`() {
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"country":"DE"},{"country":"US"}]}""",
      selectedDataPath = "items",
    )
    every { importJobRepository.findById(job.id) } returns job
    justRun { importJobRepository.save(any()) }
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }

    val rules = listOf(FilterRule(FilterMode.EXCLUDE, "country", FilterOperator.EQUALS, "US"))
    val result = service.updateFilter("user-1", job.id.value, rules)

    assertThat(result.isRight()).isTrue()
    assertThat(savedDefinition.captured.filterRules).isEqualTo(rules)
    val view = result.getOrNull()!!
    assertThat(view.totalRecordCount).isEqualTo(2)
    assertThat(view.matchingRecordCount).isEqualTo(1)
  }

  @Test
  fun `update filter recomputes and persists the filtered schema, leaving the detected schema untouched`() {
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"name":"Alice"},{"other":"x"}]}""",
      selectedDataPath = "items",
      detectedSchema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = false)),
    )
    every { importJobRepository.findById(job.id) } returns job
    val saved = slot<ImportJob>()
    justRun { importJobRepository.save(capture(saved)) }
    justRun { importDefinitionRepository.save(any()) }

    val rules = listOf(FilterRule(FilterMode.INCLUDE, "name", FilterOperator.IS_NOT_NULL))
    val result = service.updateFilter("user-1", job.id.value, rules)

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.detectedSchema).isEqualTo(job.detectedSchema)
    assertThat(saved.captured.filteredSchema).containsExactly(
      SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = true, stringLengthCounts = mapOf(5 to 1)),
    )
  }

  @Test
  fun `update filter fails when job has no data path selected yet`() {
    val job = importJob(status = ImportStatus.DOWNLOADED)
    every { importJobRepository.findById(job.id) } returns job

    val result = service.updateFilter("user-1", job.id.value, emptyList())

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FILTERABLE.left())
  }

  @Test
  fun `update filter fails when job does not exist`() {
    every { importJobRepository.findById(ImportJobId("missing")) } returns null

    val result = service.updateFilter("user-1", "missing", emptyList())

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `resolve filter field values returns the distinct values seen in the raw source records`() {
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"country":"DE"},{"country":"US"},{"country":"DE"}]}""",
      selectedDataPath = "items",
      filterRules = listOf(FilterRule(FilterMode.INCLUDE, "country", FilterOperator.EQUALS, "DE")),
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.resolveFilterFieldValues("user-1", job.id.value, "country")

    assertThat(result).isEqualTo(listOf("DE", "US").right())
  }

  @Test
  fun `resolve filter field values fails when job does not exist`() {
    every { importJobRepository.findById(ImportJobId("missing")) } returns null

    val result = service.resolveFilterFieldValues("user-1", "missing", "country")

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `resolve filter sample returns the matched record at the given index and the total matched count`() {
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"country":"DE"},{"country":"US"},{"country":"DE"}]}""",
      selectedDataPath = "items",
      filterRules = listOf(FilterRule(FilterMode.INCLUDE, "country", FilterOperator.EQUALS, "DE")),
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.resolveFilterSample("user-1", job.id.value, matched = true, index = 1)

    assertThat(result).isEqualTo(FilterSample(2, """{"country":"DE"}""").right())
  }

  @Test
  fun `resolve filter sample returns the excluded record at the given index and the total excluded count`() {
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"country":"DE"},{"country":"US"},{"country":"DE"}]}""",
      selectedDataPath = "items",
      filterRules = listOf(FilterRule(FilterMode.INCLUDE, "country", FilterOperator.EQUALS, "DE")),
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.resolveFilterSample("user-1", job.id.value, matched = false, index = 0)

    assertThat(result).isEqualTo(FilterSample(1, """{"country":"US"}""").right())
  }

  @Test
  fun `resolve filter sample reports a null record when the index is out of range, including when there are no matches at all`() {
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"country":"US"}]}""",
      selectedDataPath = "items",
      filterRules = listOf(FilterRule(FilterMode.INCLUDE, "country", FilterOperator.EQUALS, "DE")),
    )
    every { importJobRepository.findById(job.id) } returns job

    val emptySide = service.resolveFilterSample("user-1", job.id.value, matched = true, index = 0)
    val outOfRange = service.resolveFilterSample("user-1", job.id.value, matched = false, index = 5)

    assertThat(emptySide).isEqualTo(FilterSample(0, null).right())
    assertThat(outOfRange).isEqualTo(FilterSample(1, null).right())
  }

  @Test
  fun `resolve filter sample fails when job does not exist`() {
    every { importJobRepository.findById(ImportJobId("missing")) } returns null

    val result = service.resolveFilterSample("user-1", "missing", matched = true, index = 0)

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `dry run only evaluates records that pass the configured filter`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returns emptyList()
    val job = importJob(
      status = ImportStatus.READY,
      payload = """{"items":[{"name":"Alice","country":"DE"},{"name":"Bob","country":"US"}]}""",
      selectedDataPath = "items",
      filterRules = listOf(FilterRule(FilterMode.INCLUDE, "country", FilterOperator.EQUALS, "DE")),
      mapping = readyMapping,
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.dryRun("user-1", job.id.value)

    assertThat(result.isRight()).isTrue()
    val report = result.getOrNull()!!
    assertThat(report.totalCount).isEqualTo(1)
    assertThat(report.validObjects.single().targetData[PropertyId("prop-1")]).isEqualTo("Alice")
  }

  @Test
  fun `resolve mapping sample returns the mapped preview for the record at the given index, computed from the given not-yet-saved field mappings`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returns emptyList()
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"name":"Alice"},{"name":"Bob"}]}""",
      selectedDataPath = "items",
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.resolveMappingSample(
      "user-1",
      job.id.value,
      index = 1,
      fieldMappings = listOf(FieldMapping(targetPropertyId = PropertyId("prop-1"), sourcePath = "name")),
    )

    assertThat(result.isRight()).isTrue()
    val sample = result.getOrNull()!!
    assertThat(sample.total).isEqualTo(2)
    assertThat(sample.dryRunObject?.isValid).isTrue()
    assertThat(sample.dryRunObject?.targetData).isEqualTo(mapOf(PropertyId("prop-1") to "Bob"))
  }

  @Test
  fun `resolve mapping sample reflects unsaved field mappings rather than the job's persisted mapping`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returns emptyList()
    val job = importJob(
      status = ImportStatus.READY,
      payload = """{"items":[{"name":"Alice","nickname":"Ally"}]}""",
      selectedDataPath = "items",
      mapping = readyMapping,
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.resolveMappingSample(
      "user-1",
      job.id.value,
      index = 0,
      fieldMappings = listOf(FieldMapping(targetPropertyId = PropertyId("prop-1"), sourcePath = "nickname")),
    )

    assertThat(result.getOrNull()?.dryRunObject?.targetData).isEqualTo(mapOf(PropertyId("prop-1") to "Ally"))
  }

  @Test
  fun `resolve mapping sample reports a null dry-run object when the index is out of range`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"name":"Alice"}]}""",
      selectedDataPath = "items",
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.resolveMappingSample("user-1", job.id.value, index = 5, fieldMappings = emptyList())

    assertThat(result).isEqualTo(MappingSample(1, null).right())
  }

  @Test
  fun `resolve mapping sample fails when a field mapping targets an unknown property`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(status = ImportStatus.DATA_IDENTIFIED, payload = """{"items":[{"name":"Alice"}]}""", selectedDataPath = "items")
    every { importJobRepository.findById(job.id) } returns job

    val result = service.resolveMappingSample(
      "user-1",
      job.id.value,
      index = 0,
      fieldMappings = listOf(FieldMapping(targetPropertyId = PropertyId("unknown-prop"), sourcePath = "name")),
    )

    assertThat(result).isEqualTo(ImportError.MAPPING_PROPERTY_NOT_FOUND.left())
  }

  @Test
  fun `resolve mapping sample fails when job does not exist`() {
    every { importJobRepository.findById(ImportJobId("missing")) } returns null

    val result = service.resolveMappingSample("user-1", "missing", index = 0, fieldMappings = emptyList())

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `update mapping succeeds and transitions to READY when mapping is complete and valid`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"name":"Alice"}]}""",
      selectedDataPath = "items",
      detectedSchema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = true)),
    )
    every { importJobRepository.findById(job.id) } returns job
    val saved = slot<ImportJob>()
    justRun { importJobRepository.save(capture(saved)) }
    justRun { importDefinitionRepository.save(any()) }

    val result = service.updateMapping(
      "user-1",
      job.id.value,
      listOf(FieldMapping(targetPropertyId = PropertyId("prop-1"), sourcePath = "name")),
    )

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.READY)
    val view = result.getOrNull()!!
    assertThat(view.validation?.isReady).isTrue()
  }

  @Test
  fun `update mapping validates against the persisted filtered schema rather than the stale unfiltered detected schema`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"name":"Alice"},{"other":"x"}]}""",
      selectedDataPath = "items",
      // Stale: computed over the unfiltered records, where "name" is only present on one of two objects.
      detectedSchema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = false)),
      // Persisted by a prior updateFilter call: "name" is mandatory once the filter excludes the record without it.
      filteredSchema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = true)),
      filterRules = listOf(FilterRule(FilterMode.INCLUDE, "name", FilterOperator.IS_NOT_NULL)),
    )
    every { importJobRepository.findById(job.id) } returns job
    val saved = slot<ImportJob>()
    justRun { importJobRepository.save(capture(saved)) }
    justRun { importDefinitionRepository.save(any()) }

    val result = service.updateMapping(
      "user-1",
      job.id.value,
      listOf(FieldMapping(targetPropertyId = PropertyId("prop-1"), sourcePath = "name")),
    )

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.READY)
    assertThat(result.getOrNull()?.validation?.blockingIssues).isEmpty()
  }

  @Test
  fun `update mapping stays DATA_IDENTIFIED and reports a missing mandatory field`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(status = ImportStatus.DATA_IDENTIFIED)
    every { importJobRepository.findById(job.id) } returns job
    val saved = slot<ImportJob>()
    justRun { importJobRepository.save(capture(saved)) }
    justRun { importDefinitionRepository.save(any()) }

    val result = service.updateMapping("user-1", job.id.value, emptyList())

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.DATA_IDENTIFIED)
    assertThat(result.getOrNull()?.validation?.blockingIssues).containsExactly(MappingIssue.MissingMandatoryField(PropertyId("prop-1")))
  }

  @Test
  fun `update mapping fails when the job's target entity definition no longer exists`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(status = ImportStatus.DATA_IDENTIFIED, targetEntityDefinitionId = EntityDefinitionId("deleted-entity"))
    every { importJobRepository.findById(job.id) } returns job

    val result = service.updateMapping("user-1", job.id.value, emptyList())

    assertThat(result).isEqualTo(ImportError.ENTITY_DEFINITION_NOT_FOUND.left())
  }

  @Test
  fun `update mapping fails when a field mapping targets an unknown property`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(status = ImportStatus.DATA_IDENTIFIED)
    every { importJobRepository.findById(job.id) } returns job

    val result = service.updateMapping(
      "user-1",
      job.id.value,
      listOf(FieldMapping(targetPropertyId = PropertyId("unknown-prop"), sourcePath = "name")),
    )

    assertThat(result).isEqualTo(ImportError.MAPPING_PROPERTY_NOT_FOUND.left())
  }

  @Test
  fun `update mapping fails when job has no data path selected yet`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val job = importJob(status = ImportStatus.DOWNLOADED)
    every { importJobRepository.findById(job.id) } returns job

    val result = service.updateMapping("user-1", job.id.value, emptyList())

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_MAPPABLE.left())
  }

  @Test
  fun `get mapping view returns the target entity definition and no validation when nothing is mapped yet`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(status = ImportStatus.DATA_IDENTIFIED)
    every { importJobRepository.findById(job.id) } returns job

    val result = service.getMappingView("user-1", job.id.value)

    assertThat(result.isRight()).isTrue()
    val view = result.getOrNull()!!
    assertThat(view.targetEntityDefinition).isEqualTo(entityDefinition)
    assertThat(view.validation).isNull()
  }

  @Test
  fun `get mapping view validates against the persisted filtered schema rather than the stale unfiltered detected schema`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(
      status = ImportStatus.READY,
      payload = """{"items":[{"name":"Alice"},{"other":"x"}]}""",
      selectedDataPath = "items",
      // Stale: computed over the unfiltered records, where "name" is only present on one of two objects.
      detectedSchema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = false)),
      // Persisted by a prior updateFilter call: "name" is mandatory once the filter excludes the record without it.
      filteredSchema = listOf(SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = true)),
      filterRules = listOf(FilterRule(FilterMode.INCLUDE, "name", FilterOperator.IS_NOT_NULL)),
      mapping = Mapping(fieldMappings = listOf(FieldMapping(targetPropertyId = PropertyId("prop-1"), sourcePath = "name"))),
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.getMappingView("user-1", job.id.value)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.validation?.blockingIssues).isEmpty()
  }

  @Test
  fun `dry run fails when import job has no mapping yet`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val job = importJob(status = ImportStatus.DATA_IDENTIFIED)
    every { importJobRepository.findById(job.id) } returns job

    val result = service.dryRun("user-1", job.id.value)

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_READY.left())
  }

  @Test
  fun `dry run runs and reports issues even when the mapping still has blocking validation issues`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returns emptyList()
    val job = importJob(
      status = ImportStatus.DATA_IDENTIFIED,
      payload = """{"items":[{"name":"Alice"}]}""",
      selectedDataPath = "items",
      mapping = Mapping(fieldMappings = emptyList()),
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.dryRun("user-1", job.id.value)

    assertThat(result.isRight()).isTrue()
    val report = result.getOrNull()!!
    assertThat(report.totalCount).isEqualTo(1)
    assertThat(report.invalidObjects.single().issues).containsExactly(DryRunIssue.MissingMandatoryValue(PropertyId("prop-1")))
  }

  @Test
  fun `dry run validates every source record and reports a report with valid and invalid objects`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returns emptyList()
    val job = importJob(
      status = ImportStatus.READY,
      payload = """{"items":[{"name":"Alice"},{"name":null}]}""",
      selectedDataPath = "items",
      mapping = readyMapping,
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.dryRun("user-1", job.id.value)

    assertThat(result.isRight()).isTrue()
    val report = result.getOrNull()!!
    assertThat(report.totalCount).isEqualTo(2)
    assertThat(report.validCount).isEqualTo(1)
    assertThat(report.invalidCount).isEqualTo(1)
    assertThat(report.invalidObjects.single().issues).containsExactly(DryRunIssue.MissingMandatoryValue(PropertyId("prop-1")))
  }

  @Test
  fun `dry run fails when import job belongs to another user`() {
    val job = importJob(userId = "someone-else")
    every { importJobRepository.findById(job.id) } returns job

    val result = service.dryRun("user-1", job.id.value)

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_FOUND.left())
  }

  @Test
  fun `accept dry run marks the job as ACCEPTING and enqueues an outbox event instead of running synchronously`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val job = importJob(
      status = ImportStatus.READY,
      payload = """{"items":[{"name":"Alice"},{"name":null}]}""",
      selectedDataPath = "items",
      mapping = readyMapping,
    )
    every { importJobRepository.findById(job.id) } returns job
    val saved = slot<ImportJob>()
    justRun { importJobRepository.save(capture(saved)) }
    val enqueued = slot<DomainOutboxEvent.AcceptDryRun>()
    justRun { outboxPort.enqueue(capture(enqueued)) }

    val result = service.acceptDryRun("user-1", job.id.value, replaceExisting = false)

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.status).isEqualTo(ImportStatus.ACCEPTING)
    assertThat(result.getOrNull()?.status).isEqualTo(ImportStatus.ACCEPTING)
    assertThat(enqueued.captured).isEqualTo(DomainOutboxEvent.AcceptDryRun(importJobId = job.id.value, userId = "user-1", replaceExisting = false))
    verify(exactly = 0) { appDataRepository.save(any()) }
    verify(exactly = 0) { importJobRepository.delete(any()) }
  }

  @Test
  fun `accept dry run fails when import job is not ready`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    val job = importJob(status = ImportStatus.DATA_IDENTIFIED)
    every { importJobRepository.findById(job.id) } returns job

    val result = service.acceptDryRun("user-1", job.id.value, replaceExisting = false)

    assertThat(result).isEqualTo(ImportError.IMPORT_JOB_NOT_READY.left())
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `handle accept dry run saves valid objects, discards invalid ones and deletes the import job`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returns emptyList()
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    val job = importJob(
      status = ImportStatus.ACCEPTING,
      payload = """{"items":[{"name":"Alice"},{"name":null}]}""",
      selectedDataPath = "items",
      mapping = readyMapping,
    )
    every { importJobRepository.findById(job.id) } returns job
    val savedAppData = slot<AppData>()
    justRun { appDataRepository.save(capture(savedAppData)) }
    justRun { importJobRepository.delete(job.id) }

    val result = service.handle(DomainOutboxEvent.AcceptDryRun(importJobId = job.id.value, userId = "user-1", replaceExisting = false))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAppData.captured.data).isEqualTo(mapOf("prop-1" to "Alice"))
    assertThat(savedAppData.captured.installedAppId).isEqualTo(InstalledAppId("installed-1"))
    assertThat(savedAppData.captured.entityType).isEqualTo(EntityDefinitionId("entity-1"))
    assertThat(savedAppData.captured.importProvenance).isEqualTo(
      ImportProvenance(connectionId = ImportConnectionId("conn-1"), connectionName = "My API", sourceUrl = "https://example.com/data"),
    )
    verify(exactly = 1) { appDataRepository.save(any()) }
    verify(exactly = 1) { importJobRepository.delete(job.id) }
    verify(exactly = 0) { appDataRepository.deleteAllByInstalledAppIdAndEntityType(any(), any()) }
  }

  @Test
  fun `handle accept dry run fails when the import connection was deleted before processing`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returns emptyList()
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns null
    val job = importJob(
      status = ImportStatus.ACCEPTING,
      payload = """{"items":[{"name":"Alice"}]}""",
      selectedDataPath = "items",
      mapping = readyMapping,
    )
    every { importJobRepository.findById(job.id) } returns job

    val result = service.handle(DomainOutboxEvent.AcceptDryRun(importJobId = job.id.value, userId = "user-1", replaceExisting = false))

    assertThat(result).isEqualTo(ImportError.CONNECTION_NOT_FOUND.left())
    verify(exactly = 0) { appDataRepository.save(any()) }
    verify(exactly = 0) { importJobRepository.delete(any()) }
  }

  @Test
  fun `handle accept dry run with replaceExisting clears existing data first and re-evaluates the dry run against the now-empty state`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    val existingAppData = AppData(
      id = AppDataId("existing-1"),
      userId = "user-1",
      installedAppId = InstalledAppId("installed-1"),
      appVersion = VersionNumber("1.0.0"),
      lastValidatedWithVersion = VersionNumber("1.0.0"),
      entityType = EntityDefinitionId("entity-1"),
      objectVersion = 1,
      createdAt = Instant.now(),
      lastChangedAt = Instant.now(),
      data = mapOf("prop-1" to "Alice"),
    )
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returnsMany
      listOf(listOf(existingAppData), emptyList())
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    justRun { appDataRepository.deleteAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) }
    val job = importJob(
      status = ImportStatus.ACCEPTING,
      payload = """{"items":[{"name":"Alice"}]}""",
      selectedDataPath = "items",
      mapping = readyMapping,
    )
    every { importJobRepository.findById(job.id) } returns job
    val savedAppData = slot<AppData>()
    justRun { appDataRepository.save(capture(savedAppData)) }
    justRun { importJobRepository.delete(job.id) }

    val result = service.handle(DomainOutboxEvent.AcceptDryRun(importJobId = job.id.value, userId = "user-1", replaceExisting = true))

    assertThat(result.isRight()).isTrue()
    assertThat(savedAppData.captured.data).isEqualTo(mapOf("prop-1" to "Alice"))
    verify(exactly = 1) { appDataRepository.deleteAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) }
    verify(exactly = 1) { appDataRepository.save(any()) }
  }

  @Test
  fun `handle accept dry run enqueues a recompute for every aggregation declared on the target entity`() {
    val aggregation = AggregationDefinition(id = AggregationDefinitionId("agg-1"), name = "Count", function = AggregationFunction.COUNT, sourceProperty = PropertyId("prop-1"))
    val entityDefinitionWithAggregation = entityDefinition.copy(aggregations = listOf(aggregation))
    val appVersionWithAggregation = appVersion.copy(entityDefinitions = listOf(entityDefinitionWithAggregation))
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersionWithAggregation
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returns emptyList()
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    val job = importJob(
      status = ImportStatus.ACCEPTING,
      payload = """{"items":[{"name":"Alice"}]}""",
      selectedDataPath = "items",
      mapping = readyMapping,
    )
    every { importJobRepository.findById(job.id) } returns job
    justRun { appDataRepository.save(any()) }
    justRun { importJobRepository.delete(job.id) }
    val enqueued = slot<DomainOutboxEvent.RecomputeAggregation>()
    justRun { outboxPort.enqueue(capture(enqueued)) }

    val result = service.handle(DomainOutboxEvent.AcceptDryRun(importJobId = job.id.value, userId = "user-1", replaceExisting = false))

    assertThat(result.isRight()).isTrue()
    assertThat(enqueued.captured).isEqualTo(DomainOutboxEvent.RecomputeAggregation(installedAppId = "installed-1", aggregationDefinitionId = "agg-1"))
  }

  @Test
  fun `handle accept dry run does not enqueue a recompute when nothing was saved and replaceExisting is false`() {
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(InstalledAppId("installed-1"), EntityDefinitionId("entity-1")) } returns emptyList()
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    val job = importJob(
      status = ImportStatus.ACCEPTING,
      payload = """{"items":[{"name":null}]}""",
      selectedDataPath = "items",
      mapping = readyMapping,
    )
    every { importJobRepository.findById(job.id) } returns job
    justRun { importJobRepository.delete(job.id) }

    val result = service.handle(DomainOutboxEvent.AcceptDryRun(importJobId = job.id.value, userId = "user-1", replaceExisting = false))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `handle accept dry run is a no-op success when the import job was already processed or deleted`() {
    every { importJobRepository.findById(ImportJobId("missing")) } returns null

    val result = service.handle(DomainOutboxEvent.AcceptDryRun(importJobId = "missing", userId = "user-1", replaceExisting = false))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `trigger scheduled import reuses the definition's stored configuration and accepts via the same outbox handler as a manual accept`() {
    val definition = importDefinition(selectedDataPath = "items", mapping = readyMapping)
    every { installedAppRepository.findAllByUserId("user-1") } returns listOf(installedApp)
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"items":[{"name":"Alice"}]}""".right()
    val savedJobs = mutableListOf<ImportJob>()
    justRun { importJobRepository.save(capture(savedJobs)) }
    every { importJobRepository.findById(any()) } answers { savedJobs.lastOrNull() }
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }
    val enqueued = slot<DomainOutboxEvent.AcceptDryRun>()
    justRun { outboxPort.enqueue(capture(enqueued)) }

    val result = service.triggerScheduledImport(definition.id.value)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(ImportStatus.ACCEPTING)
    assertThat(savedJobs).hasSize(2)
    assertThat(savedJobs[0].status).isEqualTo(ImportStatus.READY)
    assertThat(savedJobs[0].triggeredBy).isEqualTo(ImportTrigger.SYSTEM)
    assertThat(savedJobs[1].status).isEqualTo(ImportStatus.ACCEPTING)
    assertThat(enqueued.captured).isEqualTo(DomainOutboxEvent.AcceptDryRun(importJobId = savedJobs[0].id.value, userId = "user-1", replaceExisting = false))
    assertThat(savedDefinition.captured.lastRunAt).isNotNull()
    assertThat(savedDefinition.captured.lastKnownSchema).containsExactly(
      SchemaProperty("name", mapOf(SchemaPropertyType.STRING to 1), mandatory = true, stringLengthCounts = mapOf(5 to 1)),
    )
  }

  @Test
  fun `trigger scheduled import fails when the definition is not found`() {
    every { importDefinitionRepository.findById(ImportDefinitionId("missing")) } returns null

    val result = service.triggerScheduledImport("missing")

    assertThat(result).isEqualTo(ImportError.DEFINITION_NOT_FOUND.left())
  }

  @Test
  fun `trigger scheduled import aborts and still stamps lastRunAt when the definition has no mapping configured`() {
    val definition = importDefinition(selectedDataPath = "items", mapping = null)
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }

    val result = service.triggerScheduledImport(definition.id.value)

    assertThat(result).isEqualTo(ImportError.DEFINITION_NOT_CONFIGURED.left())
    assertThat(savedDefinition.captured.lastRunAt).isNotNull()
    verify(exactly = 0) { importJobRepository.save(any()) }
  }

  @Test
  fun `trigger scheduled import aborts without accepting when the detected schema deviates from the last known baseline`() {
    val definition = importDefinition(selectedDataPath = "items", mapping = readyMapping)
    val baseline = listOf(SchemaProperty("other", mapOf(SchemaPropertyType.STRING to 1), mandatory = true))
    every { importDefinitionRepository.findById(definition.id) } returns definition.copy(lastKnownSchema = baseline)
    every { installedAppRepository.findAllByUserId("user-1") } returns listOf(installedApp)
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns appVersion
    every { importConnectionRepository.findById(ImportConnectionId("conn-1")) } returns connection
    every { tokenEncryption.decrypt("encrypted-token") } returns "secret-token".right()
    every { importFetch.fetch("https://example.com/data", "secret-token") } returns """{"items":[{"name":"Alice"}]}""".right()
    val savedDefinition = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(savedDefinition)) }

    val result = service.triggerScheduledImport(definition.id.value)

    assertThat(result).isEqualTo(ImportError.SCHEMA_DRIFT_DETECTED.left())
    assertThat(savedDefinition.captured.lastRunAt).isNotNull()
    assertThat(savedDefinition.captured.lastKnownSchema).isEqualTo(baseline)
    verify(exactly = 0) { importJobRepository.save(any()) }
    verify(exactly = 0) { outboxPort.enqueue(any()) }
  }

  @Test
  fun `update schedule sets a valid cron expression on a fully configured definition`() {
    val definition = importDefinition(selectedDataPath = "items", mapping = readyMapping)
    val saved = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(saved)) }

    val result = service.updateSchedule("user-1", definition.id.value, "0 0 3 * * ?")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.schedule).isEqualTo("0 0 3 * * ?")
  }

  @Test
  fun `update schedule clears the schedule when given a blank value`() {
    val definition = importDefinition(selectedDataPath = "items", mapping = readyMapping)
    every { importDefinitionRepository.findById(definition.id) } returns definition.copy(schedule = "0 0 3 * * ?")
    val saved = slot<ImportDefinition>()
    justRun { importDefinitionRepository.save(capture(saved)) }

    val result = service.updateSchedule("user-1", definition.id.value, " ")

    assertThat(result.isRight()).isTrue()
    assertThat(saved.captured.schedule).isNull()
  }

  @Test
  fun `update schedule rejects an invalid cron expression`() {
    val definition = importDefinition(selectedDataPath = "items", mapping = readyMapping)

    val result = service.updateSchedule("user-1", definition.id.value, "not a cron")

    assertThat(result).isEqualTo(ImportError.INVALID_CRON_SCHEDULE.left())
    verify(exactly = 0) { importDefinitionRepository.save(any()) }
  }

  @Test
  fun `update schedule rejects setting a schedule before the definition has a mapping configured`() {
    val definition = importDefinition(selectedDataPath = "items", mapping = null)

    val result = service.updateSchedule("user-1", definition.id.value, "0 0 3 * * ?")

    assertThat(result).isEqualTo(ImportError.DEFINITION_NOT_CONFIGURED.left())
  }

  @Test
  fun `update schedule fails when the definition belongs to another user`() {
    val definition = importDefinition(userId = "someone-else", selectedDataPath = "items", mapping = readyMapping)

    val result = service.updateSchedule("user-1", definition.id.value, "0 0 3 * * ?")

    assertThat(result).isEqualTo(ImportError.DEFINITION_NOT_FOUND.left())
  }

  private val connection = ImportConnection(
    id = ImportConnectionId("conn-1"),
    userId = "user-1",
    name = "My API",
    baseUrl = "https://example.com/data",
    encryptedBearerToken = "encrypted-token",
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
  )

  private val readyMapping = Mapping(
    fieldMappings = listOf(FieldMapping(targetPropertyId = PropertyId("prop-1"), sourcePath = "name")),
  )

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

  /** Builds an [ImportDefinition], stubs [importDefinitionRepository] to serve it, and returns it - the counterpart to [importJob]'s definition. */
  private fun importDefinition(
    userId: String = "user-1",
    createdAt: Instant = Instant.now(),
    selectedDataPath: String? = null,
    filterRules: List<FilterRule> = emptyList(),
    mapping: Mapping? = null,
    targetEntityDefinitionId: EntityDefinitionId = EntityDefinitionId("entity-1"),
    connectionId: ImportConnectionId = ImportConnectionId("conn-1"),
    urlPostfix: String? = null,
  ): ImportDefinition {
    val definition = ImportDefinition(
      id = ImportDefinitionId("def-${System.nanoTime()}"),
      userId = userId,
      connectionId = connectionId,
      name = "Test Definition",
      urlPostfix = urlPostfix,
      targetEntityDefinitionId = targetEntityDefinitionId,
      selectedDataPath = selectedDataPath,
      filterRules = filterRules,
      mapping = mapping,
      createdAt = createdAt,
      lastChangedAt = createdAt,
    )
    every { importDefinitionRepository.findById(definition.id) } returns definition
    return definition
  }

  private fun importJob(
    installedAppId: InstalledAppId = InstalledAppId("installed-1"),
    userId: String = "user-1",
    createdAt: Instant = Instant.now(),
    status: ImportStatus = ImportStatus.DOWNLOADED,
    payload: String = """{"foo":"bar"}""",
    detectedSchema: List<SchemaProperty> = emptyList(),
    filteredSchema: List<SchemaProperty> = detectedSchema,
    selectedDataPath: String? = null,
    filterRules: List<FilterRule> = emptyList(),
    mapping: Mapping? = null,
    targetEntityDefinitionId: EntityDefinitionId = EntityDefinitionId("entity-1"),
  ): ImportJob {
    val definition = importDefinition(
      userId = userId,
      createdAt = createdAt,
      selectedDataPath = selectedDataPath,
      filterRules = filterRules,
      mapping = mapping,
      targetEntityDefinitionId = targetEntityDefinitionId,
    )
    return ImportJob(
      id = ImportJobId("job-${System.nanoTime()}"),
      userId = userId,
      installedAppId = installedAppId,
      importDefinitionId = definition.id,
      status = status,
      payload = payload,
      detectedSchema = detectedSchema,
      filteredSchema = filteredSchema,
      createdAt = createdAt,
      lastChangedAt = createdAt,
    )
  }
}
