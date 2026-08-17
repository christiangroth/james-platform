package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.app.encodeListValue
import de.chrgroth.james.platform.domain.model.app.encodeObjectValue
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.DurationPropertyLocation
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AppDataMigrationServiceTests {

  private val appRepository: AppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val appDataRepository: AppDataRepositoryPort = mockk()
  private val service = AppDataMigrationService(appRepository, appVersionRepository, appDataRepository, "1.0.0")

  private val entityWithDisplayText = EntityDefinition(
    id = EntityDefinitionId("entity-1"),
    name = "EntityWithText",
    displayText = "some template",
  )
  private val entityWithoutDisplayText = EntityDefinition(
    id = EntityDefinitionId("entity-2"),
    name = "EntityWithoutText",
    displayText = null,
  )

  private fun version(id: String, entities: List<EntityDefinition>) = AppVersion(
    id = AppVersionId(id),
    appId = AppId("app-1"),
    versionNumber = VersionNumber("1.0.0"),
    releaseNotes = "notes",
    entityDefinitions = entities,
    reports = emptyList(),
    status = AppVersionStatus.PUBLISHED,
    createdAt = Instant.now(),
  )

  @Test
  fun `backfillEntityDisplayText does nothing when all entities already have displayText`() {
    val ver = version("ver-1", listOf(entityWithDisplayText))
    every { appVersionRepository.findAll() } returns listOf(ver)

    service.backfillEntityDisplayText()

    verify(exactly = 0) { appVersionRepository.save(any()) }
  }

  @Test
  fun `backfillEntityDisplayText does nothing when no versions exist`() {
    every { appVersionRepository.findAll() } returns emptyList()

    service.backfillEntityDisplayText()

    verify(exactly = 0) { appVersionRepository.save(any()) }
  }

  @Test
  fun `backfillEntityDisplayText backfills null displayText with fallback static text`() {
    val ver = version("ver-1", listOf(entityWithoutDisplayText))
    every { appVersionRepository.findAll() } returns listOf(ver)
    val savedSlot = slot<AppVersion>()
    justRun { appVersionRepository.save(capture(savedSlot)) }

    service.backfillEntityDisplayText()

    verify(exactly = 1) { appVersionRepository.save(any()) }
    val saved = savedSlot.captured
    val entity = saved.entityDefinitions.single()
    assertThat(entity.displayText).isEqualTo(AppDataMigrationService.FALLBACK_DISPLAY_TEXT)
  }

  @Test
  fun `backfillEntityDisplayText only updates entities without displayText and leaves others unchanged`() {
    val ver = version("ver-1", listOf(entityWithDisplayText, entityWithoutDisplayText))
    every { appVersionRepository.findAll() } returns listOf(ver)
    val savedSlot = slot<AppVersion>()
    justRun { appVersionRepository.save(capture(savedSlot)) }

    service.backfillEntityDisplayText()

    verify(exactly = 1) { appVersionRepository.save(any()) }
    val saved = savedSlot.captured
    val (first, second) = saved.entityDefinitions
    assertThat(first.displayText).isEqualTo("some template")
    assertThat(second.displayText).isEqualTo(AppDataMigrationService.FALLBACK_DISPLAY_TEXT)
  }

  @Test
  fun `backfillEntityDisplayText saves only versions that have at least one null displayText`() {
    val versionNeedingUpdate = version("ver-1", listOf(entityWithoutDisplayText))
    val versionOk = version("ver-2", listOf(entityWithDisplayText))
    every { appVersionRepository.findAll() } returns listOf(versionNeedingUpdate, versionOk)
    justRun { appVersionRepository.save(any()) }

    service.backfillEntityDisplayText()

    verify(exactly = 1) { appVersionRepository.save(match { it.id == AppVersionId("ver-1") }) }
    verify(exactly = 0) { appVersionRepository.save(match { it.id == AppVersionId("ver-2") }) }
  }

  private fun appData(entityType: String, data: Map<String, String?>) = AppData(
    id = AppDataId("data-1"),
    userId = "user-1",
    installedAppId = InstalledAppId("installed-1"),
    appVersion = VersionNumber("1.0.0"),
    entityType = EntityDefinitionId(entityType),
    objectVersion = 1,
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
    appBuildVersion = "1.0.0",
    data = data,
  )

  @Test
  fun `migrateDurationProperties does nothing when there are no DURATION properties to migrate`() {
    every { appVersionRepository.migrateDurationProperties(TimeGranularity.MINUTES) } returns emptyList()

    service.migrateDurationProperties()

    verify(exactly = 0) { appDataRepository.findAll() }
  }

  @Test
  fun `migrateDurationProperties converts a scalar DURATION value from legacy text to seconds`() {
    val location = DurationPropertyLocation(EntityDefinitionId("entity-1"), listOf(PropertyId("duration")), isListItem = false)
    every { appVersionRepository.migrateDurationProperties(TimeGranularity.MINUTES) } returns listOf(location)
    val item = appData("entity-1", mapOf("duration" to "1:30:15"))
    every { appDataRepository.findAll() } returns listOf(item)
    val savedSlot = slot<AppData>()
    justRun { appDataRepository.save(capture(savedSlot)) }

    service.migrateDurationProperties()

    assertThat(savedSlot.captured.data["duration"]).isEqualTo((3600 + 30 * 60 + 15).toString())
  }

  @Test
  fun `migrateDurationProperties converts each item of a LIST DURATION value`() {
    val location = DurationPropertyLocation(EntityDefinitionId("entity-1"), listOf(PropertyId("durations")), isListItem = true)
    every { appVersionRepository.migrateDurationProperties(TimeGranularity.MINUTES) } returns listOf(location)
    val item = appData("entity-1", mapOf("durations" to encodeListValue(listOf("1:00:00", "30m"))))
    every { appDataRepository.findAll() } returns listOf(item)
    val savedSlot = slot<AppData>()
    justRun { appDataRepository.save(capture(savedSlot)) }

    service.migrateDurationProperties()

    val migrated = savedSlot.captured.data["durations"]?.let { de.chrgroth.james.platform.domain.model.app.decodeListValue(it) }
    assertThat(migrated).containsExactly("3600", "1800")
  }

  @Test
  fun `migrateDurationProperties converts a DURATION value nested inside an OBJECT property`() {
    val location = DurationPropertyLocation(EntityDefinitionId("entity-1"), listOf(PropertyId("info"), PropertyId("duration")), isListItem = false)
    every { appVersionRepository.migrateDurationProperties(TimeGranularity.MINUTES) } returns listOf(location)
    val item = appData("entity-1", mapOf("info" to encodeObjectValue(mapOf("duration" to "45s", "other" to "unchanged"))))
    every { appDataRepository.findAll() } returns listOf(item)
    val savedSlot = slot<AppData>()
    justRun { appDataRepository.save(capture(savedSlot)) }

    service.migrateDurationProperties()

    val migrated = savedSlot.captured.data["info"]?.let { de.chrgroth.james.platform.domain.model.app.decodeObjectValue(it) }
    assertThat(migrated?.get("duration")).isEqualTo("45")
    assertThat(migrated?.get("other")).isEqualTo("unchanged")
  }

  @Test
  fun `migrateDurationProperties skips app data of entities without a matching location and leaves values unconverted`() {
    val location = DurationPropertyLocation(EntityDefinitionId("entity-1"), listOf(PropertyId("duration")), isListItem = false)
    every { appVersionRepository.migrateDurationProperties(TimeGranularity.MINUTES) } returns listOf(location)
    val item = appData("entity-2", mapOf("duration" to "1:00:00"))
    every { appDataRepository.findAll() } returns listOf(item)

    service.migrateDurationProperties()

    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `migrateDurationProperties is idempotent - already-numeric values are left unchanged and not re-saved`() {
    val location = DurationPropertyLocation(EntityDefinitionId("entity-1"), listOf(PropertyId("duration")), isListItem = false)
    every { appVersionRepository.migrateDurationProperties(TimeGranularity.MINUTES) } returns listOf(location)
    val item = appData("entity-1", mapOf("duration" to "5415"))
    every { appDataRepository.findAll() } returns listOf(item)

    service.migrateDurationProperties()

    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `backfillAppBuildVersion delegates to the repository with the current build version`() {
    justRun { appDataRepository.backfillAppBuildVersion("1.0.0") }

    service.backfillAppBuildVersion()

    verify(exactly = 1) { appDataRepository.backfillAppBuildVersion("1.0.0") }
  }
}
