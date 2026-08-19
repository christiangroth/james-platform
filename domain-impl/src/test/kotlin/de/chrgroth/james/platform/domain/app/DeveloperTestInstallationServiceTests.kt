package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.app
import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.version
import de.chrgroth.james.platform.domain.app.UserAppStoreServiceTests.Companion.installedApp
import de.chrgroth.james.platform.domain.error.DeveloperTestInstallationError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeveloperTestInstallationServiceTests {

  private val appRepository: AppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val outbox: OutboxPort = mockk()
  private val service = DeveloperTestInstallationService(appRepository, appVersionRepository, installedAppRepository, outbox)

  private val existingApp = app(id = "app-1", name = "My App", developerId = "dev-1")

  // region listTestInstallations

  @Test
  fun `listTestInstallations returns only test installations for the app`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findAllByAppId(AppId("app-1")) } returns listOf(
      installedApp(id = "inst-1", appId = "app-1", isTest = true),
      installedApp(id = "inst-2", appId = "app-1", isTest = false),
    )

    val result = service.listTestInstallations("app-1", "dev-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.map { it.id }).containsExactly(InstalledAppId("inst-1"))
  }

  @Test
  fun `listTestInstallations fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.listTestInstallations("unknown", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.APP_NOT_FOUND)
  }

  @Test
  fun `listTestInstallations fails when app belongs to another developer`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp

    val result = service.listTestInstallations("app-1", "dev-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.APP_NOT_FOUND)
  }

  // endregion

  // region createTestInstallation

  @Test
  fun `createTestInstallation creates a test installation pinned to a published version`() {
    val publishedVersion = version(id = "ver-1", appId = "app-1", versionNumber = "1.0.0", status = AppVersionStatus.PUBLISHED)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns publishedVersion
    justRun { installedAppRepository.save(any()) }

    val result = service.createTestInstallation("app-1", "ver-1", "dev-1")

    assertThat(result.isRight()).isTrue()
    val created = result.getOrNull()!!
    assertThat(created.isTest).isTrue()
    assertThat(created.userId).isEqualTo("dev-1")
    assertThat(created.appId).isEqualTo(AppId("app-1"))
    assertThat(created.installedVersionId).isEqualTo(AppVersionId("ver-1"))
    assertThat(created.installedVersionNumber).isEqualTo(VersionNumber("1.0.0"))
    verify { installedAppRepository.save(created) }
  }

  @Test
  fun `createTestInstallation creates a test installation pinned to a DRAFT version`() {
    val draftVersion = version(id = "ver-draft", appId = "app-1", versionNumber = null, status = AppVersionStatus.DRAFT)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draftVersion
    justRun { installedAppRepository.save(any()) }

    val result = service.createTestInstallation("app-1", "ver-draft", "dev-1")

    assertThat(result.isRight()).isTrue()
    val created = result.getOrNull()!!
    assertThat(created.isTest).isTrue()
    assertThat(created.installedVersionId).isEqualTo(AppVersionId("ver-draft"))
  }

  @Test
  fun `createTestInstallation fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.createTestInstallation("unknown", "ver-1", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.APP_NOT_FOUND)
  }

  @Test
  fun `createTestInstallation fails when app belongs to another developer`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp

    val result = service.createTestInstallation("app-1", "ver-1", "dev-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.APP_NOT_FOUND)
  }

  @Test
  fun `createTestInstallation fails when version not found`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.createTestInstallation("app-1", "unknown", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.VERSION_NOT_FOUND)
  }

  @Test
  fun `createTestInstallation fails when version belongs to another app`() {
    val otherAppVersion = version(id = "ver-1", appId = "app-2", versionNumber = "1.0.0", status = AppVersionStatus.PUBLISHED)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns otherAppVersion

    val result = service.createTestInstallation("app-1", "ver-1", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.VERSION_NOT_FOUND)
  }

  // endregion

  // region deleteTestInstallation

  @Test
  fun `deleteTestInstallation enqueues an UninstallApp outbox event`() {
    val testInstallation = installedApp(id = "inst-1", userId = "dev-1", appId = "app-1", isTest = true)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("inst-1")) } returns testInstallation
    justRun { outbox.enqueue(any()) }

    val result = service.deleteTestInstallation("app-1", "inst-1", "dev-1")

    assertThat(result.isRight()).isTrue()
    verify { outbox.enqueue(DomainOutboxEvent.UninstallApp(installedAppId = "inst-1", userId = "dev-1")) }
  }

  @Test
  fun `deleteTestInstallation fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.deleteTestInstallation("unknown", "inst-1", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.APP_NOT_FOUND)
  }

  @Test
  fun `deleteTestInstallation fails when app belongs to another developer`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp

    val result = service.deleteTestInstallation("app-1", "inst-1", "dev-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.APP_NOT_FOUND)
  }

  @Test
  fun `deleteTestInstallation fails when installation not found`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("unknown")) } returns null

    val result = service.deleteTestInstallation("app-1", "unknown", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.INSTALLATION_NOT_FOUND)
  }

  @Test
  fun `deleteTestInstallation fails when installation is not a test installation`() {
    val realInstallation = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", isTest = false)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("inst-1")) } returns realInstallation

    val result = service.deleteTestInstallation("app-1", "inst-1", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.INSTALLATION_NOT_FOUND)
    verify(exactly = 0) { outbox.enqueue(any()) }
  }

  @Test
  fun `deleteTestInstallation fails when installation belongs to another app`() {
    val testInstallation = installedApp(id = "inst-1", userId = "dev-1", appId = "app-2", isTest = true)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { installedAppRepository.findById(InstalledAppId("inst-1")) } returns testInstallation

    val result = service.deleteTestInstallation("app-1", "inst-1", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(DeveloperTestInstallationError.INSTALLATION_NOT_FOUND)
  }

  // endregion
}
