package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.app
import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.version
import de.chrgroth.james.platform.domain.error.UserAppStoreError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppStatus
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
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

class UserAppStoreServiceTests {

  private val appRepository: AppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val service = UserAppStoreService(appRepository, appVersionRepository, installedAppRepository)

  private val app1 = app(id = "app-1", name = "Alpha App", developerId = "dev-1")
  private val app2 = app(id = "app-2", name = "Beta App", developerId = "dev-2")
  private val v1 = version(id = "ver-1", appId = "app-1", versionNumber = "1.0.0", status = AppVersionStatus.PUBLISHED)
  private val v2 = version(id = "ver-2", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.PUBLISHED)
  private val v3 = version(id = "ver-3", appId = "app-2", versionNumber = "1.0.0", status = AppVersionStatus.PUBLISHED)
  private val draft = version(id = "ver-draft", appId = "app-1", versionNumber = null, status = AppVersionStatus.DRAFT)

  // region listAllPublishedApps

  @Test
  fun `listAllPublishedApps returns apps with latest published version sorted by name`() {
    every { appRepository.findAll() } returns listOf(app2, app1)
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(v1, v2, draft)
    every { appVersionRepository.findAllByAppId(AppId("app-2")) } returns listOf(v3)

    val result = service.listAllPublishedApps()

    assertThat(result).hasSize(2)
    assertThat(result[0].appName).isEqualTo("Alpha App")
    assertThat(result[0].latestVersion.id.value).isEqualTo("ver-2")
    assertThat(result[1].appName).isEqualTo("Beta App")
  }

  @Test
  fun `listAllPublishedApps excludes apps without published versions`() {
    every { appRepository.findAll() } returns listOf(app1)
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draft)

    val result = service.listAllPublishedApps()

    assertThat(result).isEmpty()
  }

  @Test
  fun `listAllPublishedApps excludes inactive apps`() {
    val inactiveApp = app1.copy(status = AppStatus.INACTIVE)
    every { appRepository.findAll() } returns listOf(inactiveApp)

    val result = service.listAllPublishedApps()

    assertThat(result).isEmpty()
  }

  // endregion

  // region getPublishedApp

  @Test
  fun `getPublishedApp returns detail for existing app with published version`() {
    every { appRepository.findById(AppId("app-1")) } returns app1
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(v1, v2)

    val result = service.getPublishedApp("app-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.appId).isEqualTo("app-1")
    assertThat(result.getOrNull()?.appName).isEqualTo("Alpha App")
    assertThat(result.getOrNull()?.latestVersion?.id?.value).isEqualTo("ver-2")
  }

  @Test
  fun `getPublishedApp fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.getPublishedApp("unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAppStoreError.APP_NOT_FOUND)
  }

  @Test
  fun `getPublishedApp fails when no published version exists`() {
    every { appRepository.findById(AppId("app-1")) } returns app1
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draft)

    val result = service.getPublishedApp("app-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAppStoreError.NO_PUBLISHED_VERSION)
  }

  // endregion

  // region getInstalledApps

  @Test
  fun `getInstalledApps returns installed apps for user sorted by name`() {
    val installed1 = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.0.0")
    val installed2 = installedApp(id = "inst-2", userId = "user-1", appId = "app-2", versionNumber = "1.0.0")
    every { installedAppRepository.findAllByUserId("user-1") } returns listOf(installed2, installed1)
    every { appRepository.findById(AppId("app-1")) } returns app1
    every { appRepository.findById(AppId("app-2")) } returns app2
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns v1
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-2"), VersionNumber("1.0.0")) } returns v3
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(v1, v2)
    every { appVersionRepository.findAllByAppId(AppId("app-2")) } returns listOf(v3)

    val result = service.getInstalledApps("user-1")

    assertThat(result).hasSize(2)
    assertThat(result[0].appName).isEqualTo("Alpha App")
    assertThat(result[0].installedVersion.id.value).isEqualTo("ver-1")
    assertThat(result[0].latestVersion.id.value).isEqualTo("ver-2")
    assertThat(result[1].appName).isEqualTo("Beta App")
  }

  @Test
  fun `getInstalledApps returns empty list when user has no installed apps`() {
    every { installedAppRepository.findAllByUserId("user-1") } returns emptyList()

    val result = service.getInstalledApps("user-1")

    assertThat(result).isEmpty()
  }

  // endregion

  // region installApp

  @Test
  fun `installApp succeeds for valid app with published version`() {
    every { appRepository.findById(AppId("app-1")) } returns app1
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(v1, v2)
    every { installedAppRepository.findByUserIdAndAppId("user-1", AppId("app-1")) } returns null
    justRun { installedAppRepository.save(any()) }

    val result = service.installApp("user-1", "app-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.userId).isEqualTo("user-1")
    assertThat(result.getOrNull()?.appId).isEqualTo(AppId("app-1"))
    assertThat(result.getOrNull()?.installedVersionNumber).isEqualTo(VersionNumber("2.0.0"))
    verify { installedAppRepository.save(any()) }
  }

  @Test
  fun `installApp fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.installApp("user-1", "unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAppStoreError.APP_NOT_FOUND)
  }

  @Test
  fun `installApp fails when no published version exists`() {
    every { appRepository.findById(AppId("app-1")) } returns app1
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draft)

    val result = service.installApp("user-1", "app-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAppStoreError.NO_PUBLISHED_VERSION)
  }

  @Test
  fun `installApp fails when app already installed`() {
    val existing = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.0.0")
    every { appRepository.findById(AppId("app-1")) } returns app1
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(v1, v2)
    every { installedAppRepository.findByUserIdAndAppId("user-1", AppId("app-1")) } returns existing

    val result = service.installApp("user-1", "app-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAppStoreError.ALREADY_INSTALLED)
  }

  // endregion

  // region upgradeApp

  @Test
  fun `upgradeApp succeeds when newer version is available`() {
    val existing = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.0.0")
    every { installedAppRepository.findById(InstalledAppId("inst-1")) } returns existing
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(v1, v2)
    val savedSlot = slot<InstalledApp>()
    justRun { installedAppRepository.save(capture(savedSlot)) }

    val result = service.upgradeApp("user-1", "inst-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.installedVersionNumber).isEqualTo(VersionNumber("2.0.0"))
    assertThat(savedSlot.captured.installedVersionNumber).isEqualTo(VersionNumber("2.0.0"))
  }

  @Test
  fun `upgradeApp fails when installed app not found`() {
    every { installedAppRepository.findById(InstalledAppId("unknown")) } returns null

    val result = service.upgradeApp("user-1", "unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAppStoreError.INSTALLED_APP_NOT_FOUND)
  }

  @Test
  fun `upgradeApp fails when installed app belongs to another user`() {
    val existing = installedApp(id = "inst-1", userId = "user-2", appId = "app-1", versionNumber = "1.0.0")
    every { installedAppRepository.findById(InstalledAppId("inst-1")) } returns existing

    val result = service.upgradeApp("user-1", "inst-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAppStoreError.INSTALLED_APP_NOT_FOUND)
  }

  @Test
  fun `upgradeApp fails when already on latest version`() {
    val existing = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "2.0.0")
    every { installedAppRepository.findById(InstalledAppId("inst-1")) } returns existing
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(v1, v2)

    val result = service.upgradeApp("user-1", "inst-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAppStoreError.ALREADY_UP_TO_DATE)
  }

  // endregion

  companion object {
    fun installedApp(
      id: String = "inst-1",
      userId: String = "user-1",
      appId: String = "app-1",
      versionNumber: String = "1.0.0",
    ) = InstalledApp(
      id = InstalledAppId(id),
      userId = userId,
      appId = AppId(appId),
      installedVersionNumber = VersionNumber(versionNumber),
      installedAt = Instant.now(),
    )
  }
}
