package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.AppDataError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.AppDataId
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
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.port.`in`.app.SmartDefaultPort
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AppConfigurationServiceTests {

  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val appDataRepository: AppDataRepositoryPort = mockk()
  private val smartDefault: SmartDefaultPort = mockk()
  private val service = AppConfigurationService(installedAppRepository, appVersionRepository, appDataRepository, smartDefault)

  private val installedApp = InstalledApp(InstalledAppId("inst-1"), "alice", AppId("app-1"), VersionNumber("1.0.0"), Instant.EPOCH)

  private val config = EntityDefinition(
    id = EntityDefinitionId("e-config"),
    name = "Einstellungen",
    configuration = true,
    properties = listOf(
      Property(PropertyId("p-ziel"), "ziel", PropertyType.LONG, nullable = false, default = "1000"),
      Property(PropertyId("p-start"), "start", PropertyType.DATE, nullable = false, smartDefault = "today()"),
      Property(PropertyId("p-notiz"), "notiz", PropertyType.STRING),
      Property(
        PropertyId("p-schwellen"),
        "schwellen",
        PropertyType.OBJECT,
        nullable = false,
        nestedProperties = listOf(Property(PropertyId("p-gelb"), "gelb", PropertyType.DOUBLE, nullable = false, default = "0.5")),
      ),
    ),
  )
  private val data = EntityDefinition(id = EntityDefinitionId("e-data"), name = "Lauf")

  private fun version(vararg entities: EntityDefinition) = AppVersion(
    AppVersionId("ver-1"), AppId("app-1"), VersionNumber("1.0.0"), "notes", entities.toList(), emptyList(), AppVersionStatus.PUBLISHED, Instant.EPOCH,
  )

  private fun existing(values: Map<String, String?>) = AppData(
    AppDataId("cfg-1"), "alice", installedApp.id, VersionNumber("1.0.0"), VersionNumber("1.0.0"), config.id, 3, Instant.EPOCH, Instant.EPOCH, values,
  )

  private fun stub(version: AppVersion, existing: List<AppData> = emptyList()) {
    every { installedAppRepository.findById(installedApp.id) } returns installedApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns version
    every { appDataRepository.findAllByInstalledAppIdAndEntityType(installedApp.id, config.id) } returns existing
    every { smartDefault.computeSmartDefaults(config, any()) } returns mapOf("p-start" to "2026-11-02")
  }

  @Test
  fun `ensureConfiguration creates the configuration object from defaults, smart defaults and nested defaults`() {
    stub(version(data, config))
    val saved = slot<AppData>()
    justRun { appDataRepository.save(capture(saved)) }

    service.ensureConfiguration(installedApp.id)

    assertThat(saved.captured.entityType).isEqualTo(config.id)
    assertThat(saved.captured.userId).isEqualTo("alice")
    assertThat(saved.captured.objectVersion).isEqualTo(1)
    assertThat(saved.captured.data["p-ziel"]).isEqualTo("1000")
    assertThat(saved.captured.data["p-start"]).isEqualTo("2026-11-02")
    assertThat(saved.captured.data["p-notiz"]).isNull()
    assertThat(decodeObjectValue(saved.captured.data["p-schwellen"])).isEqualTo(mapOf("p-gelb" to "0.5"))
  }

  @Test
  fun `ensureConfiguration keeps an existing configuration object`() {
    stub(version(config), listOf(existing(mapOf("p-ziel" to "5"))))

    service.ensureConfiguration(installedApp.id)

    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `ensureConfiguration does nothing without configuration entity`() {
    stub(version(data))

    service.ensureConfiguration(installedApp.id)

    verify(exactly = 0) { appDataRepository.save(any()) }
  }

  @Test
  fun `getConfiguration returns the configuration object of the user's installation`() {
    val existing = existing(mapOf("p-ziel" to "5"))
    stub(version(config), listOf(existing))

    assertThat(service.getConfiguration("alice", "inst-1").getOrNull()).isEqualTo(existing)
    assertThat(service.getConfiguration("bob", "inst-1").leftOrNull()).isEqualTo(AppDataError.INSTALLED_APP_NOT_FOUND)
  }

  @Test
  fun `getConfiguration fails without configuration entity`() {
    stub(version(data))

    assertThat(service.getConfiguration("alice", "inst-1").leftOrNull()).isEqualTo(AppDataError.CONFIGURATION_NOT_DEFINED)
  }

  @Test
  fun `configurationValues reads the object, falls back to the defaults without saving and is empty without configuration`() {
    stub(version(config), listOf(existing(mapOf("p-ziel" to "5"))))
    assertThat(service.configurationValues(installedApp, version(config))).isEqualTo(mapOf("p-ziel" to "5"))

    stub(version(config))
    assertThat(service.configurationValues(installedApp, version(config))).containsEntry("p-ziel", "1000").containsEntry("p-start", "2026-11-02")
    verify(exactly = 0) { appDataRepository.save(any()) }

    assertThat(service.configurationValues(installedApp, version(data))).isEmpty()
  }
}
