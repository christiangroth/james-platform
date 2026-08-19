package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
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
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
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
  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val service = AppDataMigrationService(appRepository, appVersionRepository, appDataRepository, installedAppRepository)

  private fun appData(entityType: String, data: Map<String, String?>) = AppData(
    id = AppDataId("data-1"),
    userId = "user-1",
    installedAppId = InstalledAppId("installed-1"),
    appVersion = VersionNumber("1.0.0"),
    lastValidatedWithVersion = VersionNumber("1.0.0"),
    entityType = EntityDefinitionId(entityType),
    objectVersion = 1,
    createdAt = Instant.now(),
    lastChangedAt = Instant.now(),
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

  private fun installedApp(id: String, versionNumber: String) = InstalledApp(
    id = InstalledAppId(id),
    userId = "user-1",
    appId = AppId("app-1"),
    installedVersionNumber = VersionNumber(versionNumber),
    installedAt = Instant.now(),
  )

  @Test
  fun `backfillAppVersion updates app data whose appVersion differs from the currently installed app version`() {
    val item = appData("entity-1", emptyMap()).copy(installedAppId = InstalledAppId("installed-1"), appVersion = VersionNumber("1.0.0"))
    every { appDataRepository.findAll() } returns listOf(item)
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp("installed-1", "2.0.0")
    val savedSlot = slot<AppData>()
    justRun { appDataRepository.save(capture(savedSlot)) }

    service.backfillAppVersion()

    verify(exactly = 1) { appDataRepository.save(any()) }
    assertThat(savedSlot.captured.appVersion).isEqualTo(VersionNumber("2.0.0"))
  }

  @Test
  fun `backfillAppVersion does not save app data already matching the currently installed app version`() {
    val item = appData("entity-1", emptyMap()).copy(installedAppId = InstalledAppId("installed-1"), appVersion = VersionNumber("2.0.0"))
    every { appDataRepository.findAll() } returns listOf(item)
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns installedApp("installed-1", "2.0.0")

    service.backfillAppVersion()

    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `backfillAppVersion skips app data whose installed app no longer exists`() {
    val item = appData("entity-1", emptyMap()).copy(installedAppId = InstalledAppId("installed-1"), appVersion = VersionNumber("1.0.0"))
    every { appDataRepository.findAll() } returns listOf(item)
    every { installedAppRepository.findById(InstalledAppId("installed-1")) } returns null

    service.backfillAppVersion()

    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `backfillLastValidatedWithVersion sets lastValidatedWithVersion to appVersion when they differ`() {
    val item = appData("entity-1", emptyMap()).copy(appVersion = VersionNumber("2.0.0"), lastValidatedWithVersion = VersionNumber(""))
    every { appDataRepository.findAll() } returns listOf(item)
    val savedSlot = slot<AppData>()
    justRun { appDataRepository.save(capture(savedSlot)) }

    service.backfillLastValidatedWithVersion()

    verify(exactly = 1) { appDataRepository.save(any()) }
    assertThat(savedSlot.captured.lastValidatedWithVersion).isEqualTo(VersionNumber("2.0.0"))
  }

  @Test
  fun `backfillLastValidatedWithVersion does not save app data where lastValidatedWithVersion already matches appVersion`() {
    val item = appData("entity-1", emptyMap()).copy(appVersion = VersionNumber("2.0.0"), lastValidatedWithVersion = VersionNumber("2.0.0"))
    every { appDataRepository.findAll() } returns listOf(item)

    service.backfillLastValidatedWithVersion()

    verify(exactly = 0) { appDataRepository.save(any()) }
  }
}
