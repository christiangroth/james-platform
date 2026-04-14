package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.AppError
import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppName
import de.chrgroth.james.platform.domain.model.app.AppStatus
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AppManagementServiceTests {

  private val appRepository: AppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val service: AppManagementService = AppManagementService(appRepository, appVersionRepository)

  private val existingApp = app(id = "app-1", name = "My App")

  // region listApps

  @Test
  fun `listApps returns all apps from repository`() {
    every { appRepository.findAll() } returns listOf(existingApp)

    val result = service.listApps()

    assertThat(result).containsExactly(existingApp)
  }

  @Test
  fun `listApps returns empty list when no apps exist`() {
    every { appRepository.findAll() } returns emptyList()

    val result = service.listApps()

    assertThat(result).isEmpty()
  }

  // endregion

  // region createApp

  @Test
  fun `createApp succeeds with valid name`() {
    every { appRepository.findByName(AppName("New App")) } returns null
    justRun { appRepository.save(any()) }

    val result = service.createApp("New App", null)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.name).isEqualTo(AppName("New App"))
    assertThat(result.getOrNull()?.status).isEqualTo(AppStatus.ACTIVE)
    assertThat(result.getOrNull()?.description).isNull()
  }

  @Test
  fun `createApp stores description when provided`() {
    every { appRepository.findByName(AppName("New App")) } returns null
    justRun { appRepository.save(any()) }

    val result = service.createApp("New App", "A description")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.description).isEqualTo("A description")
  }

  @Test
  fun `createApp discards blank description`() {
    every { appRepository.findByName(AppName("New App")) } returns null
    justRun { appRepository.save(any()) }

    val result = service.createApp("New App", "   ")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.description).isNull()
  }

  @Test
  fun `createApp fails when name is blank`() {
    val result = service.createApp("  ", null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.BLANK_INPUT)
  }

  @Test
  fun `createApp fails when name already exists`() {
    every { appRepository.findByName(AppName("My App")) } returns existingApp

    val result = service.createApp("My App", null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NAME_ALREADY_EXISTS)
  }

  @Test
  fun `createApp generates unique id`() {
    every { appRepository.findByName(AppName("App A")) } returns null
    every { appRepository.findByName(AppName("App B")) } returns null
    justRun { appRepository.save(any()) }

    val resultA = service.createApp("App A", null)
    val resultB = service.createApp("App B", null)

    assertThat(resultA.getOrNull()?.id).isNotEqualTo(resultB.getOrNull()?.id)
  }

  // endregion

  // region getApp

  @Test
  fun `getApp returns app when found`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp

    val result = service.getApp("app-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).isEqualTo(existingApp)
  }

  @Test
  fun `getApp fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.getApp("unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NOT_FOUND)
  }

  // endregion

  // region updateApp

  @Test
  fun `updateApp succeeds with new name`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appRepository.findByName(AppName("Updated App")) } returns null
    justRun { appRepository.save(any()) }

    val result = service.updateApp("app-1", "Updated App", "New description")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.name).isEqualTo(AppName("Updated App"))
    assertThat(result.getOrNull()?.description).isEqualTo("New description")
  }

  @Test
  fun `updateApp succeeds when name is unchanged`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appRepository.findByName(AppName("My App")) } returns existingApp
    justRun { appRepository.save(any()) }

    val result = service.updateApp("app-1", "My App", null)

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `updateApp clears description when blank`() {
    val appWithDescription = existingApp.copy(description = "Old description")
    every { appRepository.findById(AppId("app-1")) } returns appWithDescription
    every { appRepository.findByName(AppName("My App")) } returns appWithDescription
    justRun { appRepository.save(any()) }

    val result = service.updateApp("app-1", "My App", "  ")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.description).isNull()
  }

  @Test
  fun `updateApp fails when name is blank`() {
    val result = service.updateApp("app-1", "  ", null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.BLANK_INPUT)
  }

  @Test
  fun `updateApp fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.updateApp("unknown", "New Name", null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NOT_FOUND)
  }

  @Test
  fun `updateApp fails when new name already taken by another app`() {
    val otherApp = app(id = "app-2", name = "Other App")
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appRepository.findByName(AppName("Other App")) } returns otherApp

    val result = service.updateApp("app-1", "Other App", null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NAME_ALREADY_EXISTS)
  }

  // endregion

  // region deleteApp

  @Test
  fun `deleteApp succeeds when app has no published versions`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns emptyList()
    justRun { appVersionRepository.deleteAllByAppId(AppId("app-1")) }
    justRun { appRepository.delete(AppId("app-1")) }

    val result = service.deleteApp("app-1")

    assertThat(result.isRight()).isTrue()
    verify { appVersionRepository.deleteAllByAppId(AppId("app-1")) }
    verify { appRepository.delete(AppId("app-1")) }
  }

  @Test
  fun `deleteApp succeeds when app has only draft versions`() {
    val draftVersion = version(appId = "app-1", status = AppVersionStatus.DRAFT)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion)
    justRun { appVersionRepository.deleteAllByAppId(AppId("app-1")) }
    justRun { appRepository.delete(AppId("app-1")) }

    val result = service.deleteApp("app-1")

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `deleteApp fails when app has published versions`() {
    val publishedVersion = version(appId = "app-1", status = AppVersionStatus.PUBLISHED)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(publishedVersion)

    val result = service.deleteApp("app-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.HAS_PUBLISHED_VERSIONS)
  }

  @Test
  fun `deleteApp fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.deleteApp("unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NOT_FOUND)
  }

  // endregion

  companion object {
    fun app(id: String = "app-1", name: String = "Test App", status: AppStatus = AppStatus.ACTIVE) = App(
      id = AppId(id),
      name = AppName(name),
      description = null,
      status = status,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
    )

    fun version(
      id: String = "ver-1",
      appId: String = "app-1",
      versionNumber: String = "1.0.0",
      status: AppVersionStatus = AppVersionStatus.DRAFT,
    ) = AppVersion(
      id = AppVersionId(id),
      appId = AppId(appId),
      versionNumber = VersionNumber(versionNumber),
      releaseNotes = null,
      status = status,
      publishedAt = if (status == AppVersionStatus.PUBLISHED || status == AppVersionStatus.DEPRECATED) Instant.now() else null,
      createdAt = Instant.now(),
    )
  }
}
