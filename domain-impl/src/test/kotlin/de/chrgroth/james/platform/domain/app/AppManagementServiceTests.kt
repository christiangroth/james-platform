package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.AppError
import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppName
import de.chrgroth.james.platform.domain.model.app.AppStatus
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AppManagementServiceTests {

  private val appRepository: AppRepositoryPort = mockk()
  private val service: AppManagementService = AppManagementService(appRepository)

  private val existingApp = app(id = "app-1", name = "My App", developerId = "dev-1")

  // region listApps

  @Test
  fun `listApps returns apps for developer from repository`() {
    every { appRepository.findAllByDeveloperId("dev-1") } returns listOf(existingApp)

    val result = service.listApps("dev-1")

    assertThat(result).containsExactly(existingApp)
  }

  @Test
  fun `listApps returns empty list when developer has no apps`() {
    every { appRepository.findAllByDeveloperId("dev-1") } returns emptyList()

    val result = service.listApps("dev-1")

    assertThat(result).isEmpty()
  }

  @Test
  fun `listApps does not return apps belonging to other developers`() {
    val otherApp = app(id = "app-2", name = "Other App", developerId = "dev-2")
    every { appRepository.findAllByDeveloperId("dev-1") } returns emptyList()
    every { appRepository.findAllByDeveloperId("dev-2") } returns listOf(otherApp)

    val result = service.listApps("dev-1")

    assertThat(result).isEmpty()
  }

  // endregion

  // region createApp

  @Test
  fun `createApp succeeds with valid name`() {
    every { appRepository.findByName(AppName("New App")) } returns null
    justRun { appRepository.save(any()) }

    val result = service.createApp("New App", null, "dev-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.name).isEqualTo(AppName("New App"))
    assertThat(result.getOrNull()?.developerId).isEqualTo("dev-1")
    assertThat(result.getOrNull()?.status).isEqualTo(AppStatus.ACTIVE)
    assertThat(result.getOrNull()?.description).isNull()
  }

  @Test
  fun `createApp stores description when provided`() {
    every { appRepository.findByName(AppName("New App")) } returns null
    justRun { appRepository.save(any()) }

    val result = service.createApp("New App", "A description", "dev-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.description).isEqualTo("A description")
  }

  @Test
  fun `createApp discards blank description`() {
    every { appRepository.findByName(AppName("New App")) } returns null
    justRun { appRepository.save(any()) }

    val result = service.createApp("New App", "   ", "dev-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.description).isNull()
  }

  @Test
  fun `createApp fails when name is blank`() {
    val result = service.createApp("  ", null, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.BLANK_INPUT)
  }

  @Test
  fun `createApp fails when name already exists`() {
    every { appRepository.findByName(AppName("My App")) } returns existingApp

    val result = service.createApp("My App", null, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NAME_ALREADY_EXISTS)
  }

  @Test
  fun `createApp generates unique id`() {
    every { appRepository.findByName(AppName("App A")) } returns null
    every { appRepository.findByName(AppName("App B")) } returns null
    justRun { appRepository.save(any()) }

    val resultA = service.createApp("App A", null, "dev-1")
    val resultB = service.createApp("App B", null, "dev-1")

    assertThat(resultA.getOrNull()?.id).isNotEqualTo(resultB.getOrNull()?.id)
  }

  // endregion

  // region getApp

  @Test
  fun `getApp returns app when found and owned by developer`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp

    val result = service.getApp("app-1", "dev-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).isEqualTo(existingApp)
  }

  @Test
  fun `getApp fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.getApp("unknown", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NOT_FOUND)
  }

  @Test
  fun `getApp fails when app belongs to another developer`() {
    val otherApp = app(id = "app-2", name = "Other App", developerId = "dev-2")
    every { appRepository.findById(AppId("app-2")) } returns otherApp

    val result = service.getApp("app-2", "dev-1")

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

    val result = service.updateApp("app-1", "Updated App", "New description", "dev-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.name).isEqualTo(AppName("Updated App"))
    assertThat(result.getOrNull()?.description).isEqualTo("New description")
  }

  @Test
  fun `updateApp succeeds when name is unchanged`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appRepository.findByName(AppName("My App")) } returns existingApp
    justRun { appRepository.save(any()) }

    val result = service.updateApp("app-1", "My App", null, "dev-1")

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `updateApp clears description when blank`() {
    val appWithDescription = existingApp.copy(description = "Old description")
    every { appRepository.findById(AppId("app-1")) } returns appWithDescription
    every { appRepository.findByName(AppName("My App")) } returns appWithDescription
    justRun { appRepository.save(any()) }

    val result = service.updateApp("app-1", "My App", "  ", "dev-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.description).isNull()
  }

  @Test
  fun `updateApp fails when name is blank`() {
    val result = service.updateApp("app-1", "  ", null, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.BLANK_INPUT)
  }

  @Test
  fun `updateApp fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.updateApp("unknown", "New Name", null, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NOT_FOUND)
  }

  @Test
  fun `updateApp fails when app belongs to another developer`() {
    val otherApp = app(id = "app-2", name = "Other App", developerId = "dev-2")
    every { appRepository.findById(AppId("app-2")) } returns otherApp

    val result = service.updateApp("app-2", "New Name", null, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NOT_FOUND)
  }

  @Test
  fun `updateApp fails when new name already taken by another app`() {
    val otherApp = app(id = "app-2", name = "Other App", developerId = "dev-2")
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appRepository.findByName(AppName("Other App")) } returns otherApp

    val result = service.updateApp("app-1", "Other App", null, "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NAME_ALREADY_EXISTS)
  }

  // endregion

  // region deactivateApp

  @Test
  fun `deactivateApp succeeds for active app`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    justRun { appRepository.save(any()) }

    val result = service.deactivateApp("app-1", "dev-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(AppStatus.INACTIVE)
    verify { appRepository.save(match { it.status == AppStatus.INACTIVE }) }
  }

  @Test
  fun `deactivateApp fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.deactivateApp("unknown", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NOT_FOUND)
  }

  @Test
  fun `deactivateApp fails when app belongs to another developer`() {
    val otherApp = app(id = "app-2", name = "Other App", developerId = "dev-2")
    every { appRepository.findById(AppId("app-2")) } returns otherApp

    val result = service.deactivateApp("app-2", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.APP_NOT_FOUND)
  }

  @Test
  fun `deactivateApp fails when app is already inactive`() {
    val inactiveApp = existingApp.copy(status = AppStatus.INACTIVE)
    every { appRepository.findById(AppId("app-1")) } returns inactiveApp

    val result = service.deactivateApp("app-1", "dev-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppError.ALREADY_INACTIVE)
  }

  // endregion

  companion object {
    fun app(id: String = "app-1", name: String = "Test App", status: AppStatus = AppStatus.ACTIVE, developerId: String = "dev-1") = App(
      id = AppId(id),
      name = AppName(name),
      description = null,
      developerId = developerId,
      status = status,
      createdAt = Instant.now(),
      updatedAt = Instant.now(),
    )

    fun version(
      id: String = "ver-1",
      appId: String = "app-1",
      versionNumber: String? = null,
      status: AppVersionStatus = AppVersionStatus.DRAFT,
    ) = AppVersion(
      id = AppVersionId(id),
      appId = AppId(appId),
      versionNumber = versionNumber?.let { VersionNumber(it) },
      releaseNotes = null,
      entityDefinitions = emptyList(),
      reports = emptyList(),
      status = status,
      createdAt = Instant.now(),
    )
  }
}
