package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.app
import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.version
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AppVersionManagementServiceTests {

  private val appRepository: AppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val service: AppVersionManagementService = AppVersionManagementService(appRepository, appVersionRepository)

  private val existingApp = app(id = "app-1", name = "My App")
  private val draftVersion = version(id = "ver-1", appId = "app-1", versionNumber = "1.0.0", status = AppVersionStatus.DRAFT)
  private val publishedVersion = version(id = "ver-2", appId = "app-1", versionNumber = "1.1.0", status = AppVersionStatus.PUBLISHED)
  private val deprecatedVersion = version(id = "ver-3", appId = "app-1", versionNumber = "0.9.0", status = AppVersionStatus.DEPRECATED)

  // region listVersions

  @Test
  fun `listVersions returns all versions for app`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion, publishedVersion)

    val result = service.listVersions("app-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).containsExactly(draftVersion, publishedVersion)
  }

  @Test
  fun `listVersions fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.listVersions("unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.APP_NOT_FOUND)
  }

  // endregion

  // region createVersion

  @Test
  fun `createVersion succeeds with valid semantic version`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("2.0.0")) } returns null
    justRun { appVersionRepository.save(any()) }

    val result = service.createVersion("app-1", "2.0.0", "New features")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber("2.0.0"))
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.DRAFT)
    assertThat(result.getOrNull()?.releaseNotes).isEqualTo("New features")
    assertThat(result.getOrNull()?.publishedAt).isNull()
  }

  @Test
  fun `createVersion discards blank release notes`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("2.0.0")) } returns null
    justRun { appVersionRepository.save(any()) }

    val result = service.createVersion("app-1", "2.0.0", "   ")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.releaseNotes).isNull()
  }

  @Test
  fun `createVersion fails when version number is blank`() {
    val result = service.createVersion("app-1", "  ", null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.BLANK_INPUT)
  }

  @Test
  fun `createVersion fails for invalid version number format`() {
    listOf("1", "1.0", "v1.0.0", "1.0.0.0", "1.0.x", "abc").forEach { invalid ->
      val result = service.createVersion("app-1", invalid, null)
      assertThat(result.isLeft()).withFailMessage { "Expected failure for: $invalid" }.isTrue()
      assertThat(result.leftOrNull()).withFailMessage { "Expected INVALID_VERSION_NUMBER_FORMAT for: $invalid" }
        .isEqualTo(AppVersionError.INVALID_VERSION_NUMBER_FORMAT)
    }
  }

  @Test
  fun `createVersion accepts valid semantic version formats`() {
    listOf("0.0.0", "1.0.0", "10.20.30", "0.0.1").forEach { valid ->
      every { appRepository.findById(AppId("app-1")) } returns existingApp
      every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber(valid)) } returns null
      justRun { appVersionRepository.save(any()) }

      val result = service.createVersion("app-1", valid, null)
      assertThat(result.isRight()).withFailMessage { "Expected success for: $valid" }.isTrue()
    }
  }

  @Test
  fun `createVersion fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.createVersion("unknown", "1.0.0", null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.APP_NOT_FOUND)
  }

  @Test
  fun `createVersion fails when version number already exists for app`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findByAppIdAndVersionNumber(AppId("app-1"), VersionNumber("1.0.0")) } returns draftVersion

    val result = service.createVersion("app-1", "1.0.0", null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NUMBER_ALREADY_EXISTS)
  }

  // endregion

  // region getVersion

  @Test
  fun `getVersion returns version when found and belongs to app`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.getVersion("app-1", "ver-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).isEqualTo(draftVersion)
  }

  @Test
  fun `getVersion fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.getVersion("app-1", "unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `getVersion fails when version belongs to different app`() {
    val versionOfOtherApp = version(id = "ver-1", appId = "app-2")
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns versionOfOtherApp

    val result = service.getVersion("app-1", "ver-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  // endregion

  // region publishVersion

  @Test
  fun `publishVersion succeeds for draft version`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "ver-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.PUBLISHED)
    assertThat(result.getOrNull()?.publishedAt).isNotNull()
    verify { appVersionRepository.save(match { it.status == AppVersionStatus.PUBLISHED }) }
  }

  @Test
  fun `publishVersion fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.publishVersion("app-1", "unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `publishVersion fails when version belongs to different app`() {
    val versionOfOtherApp = version(id = "ver-1", appId = "app-2")
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns versionOfOtherApp

    val result = service.publishVersion("app-1", "ver-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `publishVersion fails when version is already published`() {
    every { appVersionRepository.findById(AppVersionId("ver-2")) } returns publishedVersion

    val result = service.publishVersion("app-1", "ver-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_IN_DRAFT)
  }

  @Test
  fun `publishVersion fails when version is deprecated`() {
    every { appVersionRepository.findById(AppVersionId("ver-3")) } returns deprecatedVersion

    val result = service.publishVersion("app-1", "ver-3")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_IN_DRAFT)
  }

  // endregion

  // region deprecateVersion

  @Test
  fun `deprecateVersion succeeds for published version`() {
    every { appVersionRepository.findById(AppVersionId("ver-2")) } returns publishedVersion
    justRun { appVersionRepository.save(any()) }

    val result = service.deprecateVersion("app-1", "ver-2")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.DEPRECATED)
    verify { appVersionRepository.save(match { it.status == AppVersionStatus.DEPRECATED }) }
  }

  @Test
  fun `deprecateVersion fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.deprecateVersion("app-1", "unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `deprecateVersion fails when version belongs to different app`() {
    val versionOfOtherApp = version(id = "ver-2", appId = "app-2", status = AppVersionStatus.PUBLISHED)
    every { appVersionRepository.findById(AppVersionId("ver-2")) } returns versionOfOtherApp

    val result = service.deprecateVersion("app-1", "ver-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `deprecateVersion fails when version is still in draft`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.deprecateVersion("app-1", "ver-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_PUBLISHED)
  }

  @Test
  fun `deprecateVersion fails when version is already deprecated`() {
    every { appVersionRepository.findById(AppVersionId("ver-3")) } returns deprecatedVersion

    val result = service.deprecateVersion("app-1", "ver-3")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_PUBLISHED)
  }

  // endregion

  // region deleteVersion

  @Test
  fun `deleteVersion succeeds for draft version`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
    justRun { appVersionRepository.delete(AppVersionId("ver-1")) }

    val result = service.deleteVersion("app-1", "ver-1")

    assertThat(result.isRight()).isTrue()
    verify { appVersionRepository.delete(AppVersionId("ver-1")) }
  }

  @Test
  fun `deleteVersion fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.deleteVersion("app-1", "unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `deleteVersion fails when version belongs to different app`() {
    val versionOfOtherApp = version(id = "ver-1", appId = "app-2")
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns versionOfOtherApp

    val result = service.deleteVersion("app-1", "ver-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `deleteVersion fails for published version`() {
    every { appVersionRepository.findById(AppVersionId("ver-2")) } returns publishedVersion

    val result = service.deleteVersion("app-1", "ver-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.CANNOT_DELETE_NON_DRAFT_VERSION)
  }

  @Test
  fun `deleteVersion fails for deprecated version`() {
    every { appVersionRepository.findById(AppVersionId("ver-3")) } returns deprecatedVersion

    val result = service.deleteVersion("app-1", "ver-3")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.CANNOT_DELETE_NON_DRAFT_VERSION)
  }

  // endregion
}
