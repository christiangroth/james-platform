package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.AppVersionMigrationScriptFailedError
import de.chrgroth.james.platform.domain.error.AppVersionMigrationValidationFailedError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.`in`.app.AppDataPort
import de.chrgroth.james.platform.domain.port.`in`.app.MigrationScriptResult
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AppVersionMigrationServiceTests {

  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val appDataRepository: AppDataRepositoryPort = mockk()
  private val appDataPort: AppDataPort = AppDataService(mockk(), appVersionRepository, appDataRepository, PropertyConstraintService())
  private val service = AppVersionMigrationService(appVersionRepository, appDataRepository, appDataPort, ScriptMetrics(SimpleMeterRegistry()), 30_000L)

  private val appId = AppId("app-1")
  private val installedAppId = InstalledAppId("inst-1")
  private val entityId = EntityDefinitionId("e-1")

  private fun publishedVersion(id: String, versionNumber: String, entities: List<EntityDefinition>, createdAt: Instant) = AppVersion(
    id = AppVersionId(id),
    appId = appId,
    versionNumber = VersionNumber(versionNumber),
    releaseNotes = "notes",
    entityDefinitions = entities,
    reports = emptyList(),
    status = AppVersionStatus.PUBLISHED,
    createdAt = createdAt,
  )

  private fun appData(id: String, lastValidatedWithVersion: String, data: Map<String, String?> = emptyMap()) = AppData(
    id = AppDataId(id),
    userId = "user-1",
    installedAppId = installedAppId,
    appVersion = VersionNumber(lastValidatedWithVersion),
    lastValidatedWithVersion = VersionNumber(lastValidatedWithVersion),
    entityType = entityId,
    objectVersion = 1,
    createdAt = Instant.parse("2024-01-01T00:00:00Z"),
    lastChangedAt = Instant.parse("2024-01-01T00:00:00Z"),
    data = data,
  )

  // region runScript

  @Test
  fun `runScript returns original data unchanged when entity has no migration script`() {
    val entity = EntityDefinition(id = entityId, name = "Order")

    val result = service.runScript(entity, entity, mapOf("a" to "1"))

    assertThat(result).isEqualTo(MigrationScriptResult.Success(mapOf("a" to "1")))
  }

  @Test
  fun `runScript transforms data via script`() {
    val entity = EntityDefinition(id = entityId, name = "Order", migrationScript = "it + (\"b\" to \"2\")")

    val result = service.runScript(entity, entity, mapOf("a" to "1"))

    assertThat(result).isEqualTo(MigrationScriptResult.Success(mapOf("a" to "1", "b" to "2")))
  }

  @Test
  fun `runScript exposes previous and new entity definitions to the script`() {
    val previous = EntityDefinition(id = entityId, name = "OldName")
    val new = EntityDefinition(
      id = entityId,
      name = "NewName",
      migrationScript = "mapOf(\"prev\" to previousEntity.name, \"new\" to newEntity.name)",
    )

    val result = service.runScript(previous, new, emptyMap())

    assertThat(result).isEqualTo(MigrationScriptResult.Success(mapOf("prev" to "OldName", "new" to "NewName")))
  }

  @Test
  fun `runScript fails when script throws`() {
    val entity = EntityDefinition(id = entityId, name = "Order", migrationScript = "throw RuntimeException(\"boom\")")

    val result = service.runScript(entity, entity, emptyMap())

    assertThat(result).isInstanceOf(MigrationScriptResult.Failure::class.java)
  }

  @Test
  fun `runScript fails when script does not return a map`() {
    val entity = EntityDefinition(id = entityId, name = "Order", migrationScript = "\"not a map\"")

    val result = service.runScript(entity, entity, emptyMap())

    assertThat(result).isInstanceOf(MigrationScriptResult.Failure::class.java)
  }

  @Test
  fun `runScript fails when script exceeds timeout`() {
    val entity = EntityDefinition(id = entityId, name = "Order", migrationScript = "Thread.sleep(2000); it")
    val shortTimeoutService = AppVersionMigrationService(appVersionRepository, appDataRepository, appDataPort, ScriptMetrics(SimpleMeterRegistry()), 50L)

    val result = shortTimeoutService.runScript(entity, entity, emptyMap())

    assertThat(result).isInstanceOf(MigrationScriptResult.Failure::class.java)
  }

  // endregion

  // region migrateInstallation

  @Test
  fun `migrateInstallation succeeds without touching AppData when there are no published versions in range`() {
    every { appVersionRepository.findAllByAppId(appId) } returns listOf(publishedVersion("v1", "1.0.0", emptyList(), Instant.parse("2024-01-01T00:00:00Z")))

    val result = service.migrateInstallation(installedAppId, appId, VersionNumber("1.0.0"), VersionNumber("1.0.0"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `migrateInstallation succeeds without touching AppData when no Entity in range has a migration script`() {
    val entity = EntityDefinition(id = entityId, name = "Order")
    val v1 = publishedVersion("ver-1", "1.0.0", listOf(entity), Instant.parse("2024-01-01T00:00:00Z"))
    val v2 = publishedVersion("ver-2", "2.0.0", listOf(entity), Instant.parse("2024-02-01T00:00:00Z"))
    every { appVersionRepository.findAllByAppId(appId) } returns listOf(v1, v2)

    val result = service.migrateInstallation(installedAppId, appId, VersionNumber("1.0.0"), VersionNumber("2.0.0"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `migrateInstallation migrates pending AppData and bumps lastValidatedWithVersion and objectVersion`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = true)
    val entityV1 = EntityDefinition(id = entityId, name = "Order", properties = listOf(prop))
    val entityV2 = entityV1.copy(migrationScript = "it + (\"${prop.id.value}\" to \"42\")")
    val v1 = publishedVersion("ver-1", "1.0.0", listOf(entityV1), Instant.parse("2024-01-01T00:00:00Z"))
    val v2 = publishedVersion("ver-2", "2.0.0", listOf(entityV2), Instant.parse("2024-02-01T00:00:00Z"))
    every { appVersionRepository.findAllByAppId(appId) } returns listOf(v1, v2)

    val existingAppData = appData("data-1", "1.0.0")
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(existingAppData)
    val savedSlot = slot<AppData>()
    justRun { appDataRepository.save(capture(savedSlot)) }

    val result = service.migrateInstallation(installedAppId, appId, VersionNumber("1.0.0"), VersionNumber("2.0.0"))

    assertThat(result.isRight()).isTrue()
    assertThat(savedSlot.captured.data[prop.id.value]).isEqualTo("42")
    assertThat(savedSlot.captured.lastValidatedWithVersion).isEqualTo(VersionNumber("2.0.0"))
    assertThat(savedSlot.captured.objectVersion).isEqualTo(2)
  }

  @Test
  fun `migrateInstallation skips AppData already validated with a version at or after the migration`() {
    val entityV1 = EntityDefinition(id = entityId, name = "Order")
    val entityV2 = entityV1.copy(migrationScript = "it + (\"x\" to \"1\")")
    val v1 = publishedVersion("ver-1", "1.0.0", listOf(entityV1), Instant.parse("2024-01-01T00:00:00Z"))
    val v2 = publishedVersion("ver-2", "2.0.0", listOf(entityV2), Instant.parse("2024-02-01T00:00:00Z"))
    every { appVersionRepository.findAllByAppId(appId) } returns listOf(v1, v2)
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(appData("data-1", "2.0.0"))

    val result = service.migrateInstallation(installedAppId, appId, VersionNumber("1.0.0"), VersionNumber("2.0.0"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `migrateInstallation aborts and persists nothing when script fails`() {
    val entityV1 = EntityDefinition(id = entityId, name = "Order")
    val entityV2 = entityV1.copy(migrationScript = "throw RuntimeException(\"boom\")")
    val v1 = publishedVersion("ver-1", "1.0.0", listOf(entityV1), Instant.parse("2024-01-01T00:00:00Z"))
    val v2 = publishedVersion("ver-2", "2.0.0", listOf(entityV2), Instant.parse("2024-02-01T00:00:00Z"))
    every { appVersionRepository.findAllByAppId(appId) } returns listOf(v1, v2)
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(appData("data-1", "1.0.0"))

    val result = service.migrateInstallation(installedAppId, appId, VersionNumber("1.0.0"), VersionNumber("2.0.0"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isInstanceOf(AppVersionMigrationScriptFailedError::class.java)
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `migrateInstallation aborts and persists nothing when migrated data fails re-validation`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = true, constraints = setOf(PropertyConstraint.MinLong(100)))
    val entityV1 = EntityDefinition(id = entityId, name = "Order", properties = listOf(prop))
    val entityV2 = entityV1.copy(migrationScript = "it + (\"${prop.id.value}\" to \"5\")")
    val v1 = publishedVersion("ver-1", "1.0.0", listOf(entityV1), Instant.parse("2024-01-01T00:00:00Z"))
    val v2 = publishedVersion("ver-2", "2.0.0", listOf(entityV2), Instant.parse("2024-02-01T00:00:00Z"))
    every { appVersionRepository.findAllByAppId(appId) } returns listOf(v1, v2)
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(appData("data-1", "1.0.0"))

    val result = service.migrateInstallation(installedAppId, appId, VersionNumber("1.0.0"), VersionNumber("2.0.0"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isInstanceOf(AppVersionMigrationValidationFailedError::class.java)
    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `migrateInstallation applies chained migrations across multiple pending versions in order`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = true)
    val entityV1 = EntityDefinition(id = entityId, name = "Order", properties = listOf(prop))
    val entityV2 = entityV1.copy(migrationScript = "it + (\"${prop.id.value}\" to \"10\")")
    val entityV3 = entityV1.copy(migrationScript = "it + (\"${prop.id.value}\" to ((it[\"${prop.id.value}\"]?.toLongOrNull() ?: 0L) * 2).toString())")
    val v1 = publishedVersion("ver-1", "1.0.0", listOf(entityV1), Instant.parse("2024-01-01T00:00:00Z"))
    val v2 = publishedVersion("ver-2", "2.0.0", listOf(entityV2), Instant.parse("2024-02-01T00:00:00Z"))
    val v3 = publishedVersion("ver-3", "3.0.0", listOf(entityV3), Instant.parse("2024-03-01T00:00:00Z"))
    every { appVersionRepository.findAllByAppId(appId) } returns listOf(v1, v2, v3)
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedAppId, entityId) } returns listOf(appData("data-1", "1.0.0"))
    val savedSlot = slot<AppData>()
    justRun { appDataRepository.save(capture(savedSlot)) }

    val result = service.migrateInstallation(installedAppId, appId, VersionNumber("1.0.0"), VersionNumber("3.0.0"))

    assertThat(result.isRight()).isTrue()
    assertThat(savedSlot.captured.data[prop.id.value]).isEqualTo("20")
    assertThat(savedSlot.captured.lastValidatedWithVersion).isEqualTo(VersionNumber("3.0.0"))
  }

  // endregion
}
