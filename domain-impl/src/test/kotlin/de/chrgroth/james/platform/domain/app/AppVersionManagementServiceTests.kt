package de.chrgroth.james.platform.domain.app

import arrow.core.left
import arrow.core.right
import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.app
import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.version
import de.chrgroth.james.platform.domain.app.UserAppStoreServiceTests.Companion.installedApp
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.AppVersionMigrationScriptFailedError
import de.chrgroth.james.platform.domain.error.DisplayTextInvalidError
import de.chrgroth.james.platform.domain.error.InvalidObjectStructureError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppStatus
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.ComputedPropertyId
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.ReportId
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.`in`.app.AppVersionMigrationPort
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.stream.Stream

class AppVersionManagementServiceTests {

  enum class NotFoundTarget { VERSION, ENTITY, PROPERTY }

  private val appRepository: AppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val propertyConstraintPort: PropertyConstraintPort = mockk()
  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val appVersionMigration: AppVersionMigrationPort = mockk {
    every { migrateInstallation(any(), any(), any(), any()) } returns Unit.right()
    every { dryRunMigration(any(), any()) } returns Unit.right()
  }
  private val outbox: OutboxPort = mockk {
    justRun { enqueue(any()) }
  }
  private val service: AppVersionManagementService =
    AppVersionManagementService(appRepository, appVersionRepository, propertyConstraintPort, installedAppRepository, appVersionMigration, outbox)

  private val existingApp = app(id = "app-1", name = "My App")
  private val draftVersion = version(id = "ver-1", appId = "app-1", versionNumber = null, status = AppVersionStatus.DRAFT)
  private val publishedVersion = version(id = "ver-2", appId = "app-1", versionNumber = "1.1.0", status = AppVersionStatus.PUBLISHED)
  private val draftVersionWithNewEntity = draftVersion.copy(entityDefinitions = listOf(EntityDefinition(id = EntityDefinitionId("e-new"), name = "NewEntity")))
  private val releaseNotes = "Initial release notes."

  init {
    // Default: app "app-1" is active, as most tests below only exercise version/entity/property/report mutations and don't care about app status.
    every { appRepository.findById(AppId("app-1")) } returns existingApp
  }

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
  fun `listVersions returns draft first then published sorted by createdAt descending`() {
    val olderPublished = version(id = "ver-3", appId = "app-1", versionNumber = "0.1.0", status = AppVersionStatus.PUBLISHED)
      .copy(createdAt = publishedVersion.createdAt.minusSeconds(100))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(olderPublished, publishedVersion, draftVersion)

    val result = service.listVersions("app-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()).containsExactly(draftVersion, publishedVersion, olderPublished)
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
  fun `createVersion creates fresh draft when no versions exist`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns emptyList()
    justRun { appVersionRepository.save(any()) }

    val result = service.createVersion("app-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.versionNumber).isNull()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.DRAFT)
  }

  @Test
  fun `createVersion copies from latest published version when one exists`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(publishedVersion)
    justRun { appVersionRepository.save(any()) }

    val result = service.createVersion("app-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.versionNumber).isNull()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.DRAFT)
    assertThat(result.getOrNull()?.appId).isEqualTo(AppId("app-1"))
  }

  @Test
  fun `createVersion copies entity definitions and reports from latest published version`() {
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val report = Report(id = ReportId("r-1"), name = "Sales Report")
    val publishedWithContent = publishedVersion.copy(entityDefinitions = listOf(entityDef), reports = listOf(report))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(publishedWithContent)
    justRun { appVersionRepository.save(any()) }

    val result = service.createVersion("app-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions).isEqualTo(listOf(entityDef))
    assertThat(result.getOrNull()?.reports).isEqualTo(listOf(report))
    assertThat(result.getOrNull()?.releaseNotes).isNull()
  }

  @Test
  fun `createVersion fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.createVersion("unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.APP_NOT_FOUND)
  }

  @Test
  fun `createVersion fails when a draft version already exists for the app`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion)

    val result = service.createVersion("app-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.DRAFT_VERSION_ALREADY_EXISTS)
  }

  @Test
  fun `createVersion fails when app is inactive`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp.copy(status = AppStatus.INACTIVE)

    val result = service.createVersion("app-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.APP_INACTIVE)
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
  fun `publishVersion succeeds as first version with any bump type`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.PUBLISHED)
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber(AppVersionManagementService.FIRST_VERSION))
    verify { appVersionRepository.save(match { it.status == AppVersionStatus.PUBLISHED && it.versionNumber == VersionNumber(AppVersionManagementService.FIRST_VERSION) }) }
  }

  @Test
  fun `publishVersion saves release notes in published version`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "BUGFIX", "  My release notes  ")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.releaseNotes).isEqualTo("My release notes")
    verify { appVersionRepository.save(match { it.releaseNotes == "My release notes" }) }
  }

  @Test
  fun `publishVersion fails when release notes are blank`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion)
    listOf("", "   ").forEach { blankNotes ->
      val result = service.publishVersion("app-1", "BUGFIX", blankNotes)
      assertThat(result.isLeft()).withFailMessage { "Expected failure for notes: '$blankNotes'" }.isTrue()
      assertThat(result.leftOrNull()).withFailMessage { "Expected BLANK_RELEASE_NOTES for: '$blankNotes'" }
        .isEqualTo(AppVersionError.BLANK_RELEASE_NOTES)
    }
  }

  @Test
  fun `publishVersion succeeds as feature bump`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersionWithNewEntity, publishedVersion)
    justRun { appVersionRepository.save(any()) }
    every { installedAppRepository.findAllByAppId(AppId("app-1")) } returns emptyList()

    val result = service.publishVersion("app-1", "FEATURE", releaseNotes)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.PUBLISHED)
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber("1.2.0"))
  }

  @Test
  fun `publishVersion succeeds as bugfix bump`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersionWithNewEntity, publishedVersion)
    justRun { appVersionRepository.save(any()) }
    every { installedAppRepository.findAllByAppId(AppId("app-1")) } returns emptyList()

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.PUBLISHED)
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber("1.1.1"))
  }

  @Test
  fun `publishVersion uses major bump when breaking changes are present regardless of bump type`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val publishedWithEntity = publishedVersion.copy(entityDefinitions = listOf(entity))
    val draftWithoutEntity = draftVersion.copy(entityDefinitions = emptyList(), reports = listOf(Report(ReportId("r-1"), "Report")))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftWithoutEntity, publishedWithEntity)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber("2.0.0"))
    verify(exactly = 0) { installedAppRepository.findAllByAppId(any()) }
  }

  @Test
  fun `publishVersion publishes as Feature bump and enqueues auto-upgrade for installations when a compensating migration dry-run reconciles a breaking change`() {
    val publishedProp = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = true)
    val draftProp = publishedProp.copy(nullable = false)
    val publishedWithEntity =
      publishedVersion.copy(entityDefinitions = listOf(EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(publishedProp))))
    val draftEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(draftProp), migrationScript = "it")
    val draftWithMigrationScript = draftVersion.copy(entityDefinitions = listOf(draftEntity))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftWithMigrationScript, publishedWithEntity)
    justRun { appVersionRepository.save(any()) }
    val inst = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.1.0")
    every { installedAppRepository.findAllByAppId(AppId("app-1")) } returns listOf(inst)

    val result = service.publishVersion("app-1", "FEATURE", releaseNotes)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber("1.2.0"))
    verify(exactly = 1) { appVersionMigration.dryRunMigration(AppId("app-1"), any()) }
    verify(exactly = 1) {
      outbox.enqueue(DomainOutboxEvent.AutoUpgradeInstallation("inst-1", "app-1", "1.1.0", "1.2.0"))
    }
    verify(exactly = 0) { installedAppRepository.save(any()) }
  }

  @Test
  fun `publishVersion succeeds as first version without bump type`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", null, releaseNotes)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.PUBLISHED)
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber(AppVersionManagementService.FIRST_VERSION))
    verify { appVersionRepository.save(match { it.status == AppVersionStatus.PUBLISHED && it.versionNumber == VersionNumber(AppVersionManagementService.FIRST_VERSION) }) }
    verify(exactly = 0) { installedAppRepository.findAllByAppId(any()) }
  }

  @Test
  fun `publishVersion enqueues auto-upgrade for installations without breaking changes`() {
    val inst1 = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.1.0")
    val inst2 = installedApp(id = "inst-2", userId = "user-2", appId = "app-1", versionNumber = "1.1.0")
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersionWithNewEntity, publishedVersion)
    justRun { appVersionRepository.save(any()) }
    every { installedAppRepository.findAllByAppId(AppId("app-1")) } returns listOf(inst1, inst2)

    val result = service.publishVersion("app-1", "FEATURE", releaseNotes)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outbox.enqueue(DomainOutboxEvent.AutoUpgradeInstallation("inst-1", "app-1", "1.1.0", "1.2.0")) }
    verify(exactly = 1) { outbox.enqueue(DomainOutboxEvent.AutoUpgradeInstallation("inst-2", "app-1", "1.1.0", "1.2.0")) }
    verify(exactly = 0) { installedAppRepository.save(any()) }
  }

  @Test
  fun `publishVersion does not enqueue auto-upgrade for installations that are not on the previous version`() {
    val instOnOldVersion = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.0.0")
    val instOnCurrentVersion = installedApp(id = "inst-2", userId = "user-2", appId = "app-1", versionNumber = "1.1.0")
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersionWithNewEntity, publishedVersion)
    justRun { appVersionRepository.save(any()) }
    every { installedAppRepository.findAllByAppId(AppId("app-1")) } returns listOf(instOnOldVersion, instOnCurrentVersion)

    val result = service.publishVersion("app-1", "FEATURE", releaseNotes)

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { outbox.enqueue(any()) }
    verify(exactly = 1) { outbox.enqueue(DomainOutboxEvent.AutoUpgradeInstallation("inst-2", "app-1", "1.1.0", "1.2.0")) }
  }

  @Test
  fun `publishVersion skips auto-upgrade when breaking changes are detected`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val publishedWithEntity = publishedVersion.copy(entityDefinitions = listOf(entity))
    val draftWithoutEntity = draftVersion.copy(entityDefinitions = emptyList(), reports = listOf(Report(ReportId("r-1"), "Report")))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftWithoutEntity, publishedWithEntity)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber("2.0.0"))
    verify(exactly = 0) { installedAppRepository.findAllByAppId(any()) }
    verify(exactly = 0) { installedAppRepository.save(any()) }
  }

  @Test
  fun `publishVersion fails for invalid bump type when not first version`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersionWithNewEntity, publishedVersion)
    listOf("invalid", " ", "1.0.0", "PATCH", null, "").forEach { invalid ->
      val result = service.publishVersion("app-1", invalid, releaseNotes)
      assertThat(result.isLeft()).withFailMessage { "Expected failure for: $invalid" }.isTrue()
      assertThat(result.leftOrNull()).withFailMessage { "Expected INVALID_BUMP_TYPE for: $invalid" }
        .isEqualTo(AppVersionError.INVALID_BUMP_TYPE)
    }
  }

  @Test
  fun `publishVersion fails when no changes in entities or reports`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion, publishedVersion)

    val result = service.publishVersion("app-1", "FEATURE", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.NO_CHANGES)
  }

  @Test
  fun `publishVersion succeeds and enqueues auto-upgrade when only a migration script changed`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val publishedWithEntity = publishedVersion.copy(entityDefinitions = listOf(entity))
    val draftWithMigrationScript = draftVersion.copy(entityDefinitions = listOf(entity.copy(migrationScript = "it")))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftWithMigrationScript, publishedWithEntity)
    justRun { appVersionRepository.save(any()) }
    val inst = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.1.0")
    every { installedAppRepository.findAllByAppId(AppId("app-1")) } returns listOf(inst)

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isRight()).isTrue()
    // a migration-script-only change can never be breaking, so the bump stays a plain BUGFIX bump instead of a forced Major
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber("1.1.1"))
    verify(exactly = 1) { outbox.enqueue(DomainOutboxEvent.AutoUpgradeInstallation("inst-1", "app-1", "1.1.0", "1.1.1")) }
    verify(exactly = 0) { installedAppRepository.save(any()) }
  }

  @Test
  fun `publishVersion fails when version number already exists for app`() {
    // collidingPublished is at 1.1.1 (the expected BUGFIX bump from publishedVersion 1.1.0),
    // with an older createdAt so publishedVersion is the latest and used as the base for bump calculation
    val collidingPublished = version(id = "ver-3", appId = "app-1", versionNumber = "1.1.1", status = AppVersionStatus.PUBLISHED)
      .copy(createdAt = publishedVersion.createdAt.minusSeconds(10))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersionWithNewEntity, publishedVersion, collidingPublished)

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NUMBER_ALREADY_EXISTS)
  }

  @Test
  fun `publishVersion fails when no draft version found for app`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(publishedVersion)

    val result = service.publishVersion("app-1", "FEATURE", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `publishVersion fails when app not found`() {
    every { appRepository.findById(AppId("app-1")) } returns null

    val result = service.publishVersion("app-1", "FEATURE", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.APP_NOT_FOUND)
  }

  @Test
  fun `publishVersion fails when app is inactive`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp.copy(status = AppStatus.INACTIVE)

    val result = service.publishVersion("app-1", "FEATURE", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.APP_INACTIVE)
  }

  @Test
  fun `publishVersion fails with InvalidObjectStructureError when an OBJECT property has zero nested properties`() {
    val objectProp = Property(id = PropertyId("p-1"), name = "Meta", type = PropertyType.OBJECT)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(objectProp))
    val draftWithInvalidObject = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftWithInvalidObject)

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    val error = result.leftOrNull()
    assertThat(error).isInstanceOf(InvalidObjectStructureError::class.java)
    assertThat((error as InvalidObjectStructureError).entityNames).containsExactly("Order")
    assertThat(error.code).isEqualTo(AppVersionError.INVALID_OBJECT_STRUCTURE.code)
  }

  @Test
  fun `publishVersion fails with InvalidObjectStructureError when a nested OBJECT property has zero nested properties`() {
    val innerObjectProp = Property(id = PropertyId("p-2"), name = "Inner", type = PropertyType.OBJECT)
    val outerObjectProp = Property(id = PropertyId("p-1"), name = "Meta", type = PropertyType.OBJECT, nestedProperties = listOf(innerObjectProp))
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(outerObjectProp))
    val draftWithInvalidObject = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftWithInvalidObject)

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isInstanceOf(InvalidObjectStructureError::class.java)
  }

  @Test
  fun `publishVersion succeeds when an OBJECT property has at least one nested property`() {
    val nestedProp = Property(id = PropertyId("p-2"), name = "Nested", type = PropertyType.STRING)
    val objectProp = Property(id = PropertyId("p-1"), name = "Meta", type = PropertyType.OBJECT, nestedProperties = listOf(nestedProp))
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(objectProp))
    val draftWithValidObject = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftWithValidObject)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.PUBLISHED)
  }

  // endregion

  // region computeVersionBump

  @Test
  fun `computeVersionBump returns first version 0_1_0 when no published versions exist`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion)

    val result = service.computeVersionBump("app-1", "ver-1")

    assertThat(result.isRight()).isTrue()
    val bump = result.getOrNull()!!
    assertThat(bump.hasBreakingChanges).isFalse()
    assertThat(bump.hasChanges).isTrue()
    assertThat(bump.suggestedVersionOnFeature).isEqualTo(VersionNumber(AppVersionManagementService.FIRST_VERSION))
    assertThat(bump.suggestedVersionOnBugfix).isEqualTo(VersionNumber(AppVersionManagementService.FIRST_VERSION))
    assertThat(bump.suggestedVersionOnBreaking).isEqualTo(VersionNumber(AppVersionManagementService.FIRST_VERSION))
  }

  @Test
  fun `computeVersionBump suggests correct next versions based on latest published`() {
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(EntityDefinition(id = EntityDefinitionId("e-new"), name = "NewEntity")))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(publishedVersion, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    val bump = result.getOrNull()!!
    assertThat(bump.hasChanges).isTrue()
    assertThat(bump.suggestedVersionOnBreaking).isEqualTo(VersionNumber("2.0.0"))
    assertThat(bump.suggestedVersionOnFeature).isEqualTo(VersionNumber("1.2.0"))
    assertThat(bump.suggestedVersionOnBugfix).isEqualTo(VersionNumber("1.1.1"))
  }

  @Test
  fun `computeVersionBump returns hasChanges=false when draft is identical to published`() {
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityDef))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityDef))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    val bump = result.getOrNull()!!
    assertThat(bump.hasChanges).isFalse()
    assertThat(bump.hasBreakingChanges).isFalse()
  }

  @Test
  fun `computeVersionBump detects no breaking changes when draft is identical to published`() {
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityDef))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityDef))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isFalse()
  }

  @Test
  fun `computeVersionBump returns hasChanges=true when property default changes`() {
    val prop = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING, default = null)
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop))
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityDef))
    val draftProp = prop.copy(default = "new-default")
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityDef.copy(properties = listOf(draftProp))))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasChanges).isTrue()
  }

  @Test
  fun `computeVersionBump returns hasChanges=true when property smartDefault changes`() {
    val prop = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING, smartDefault = null)
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop))
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityDef))
    val draftProp = prop.copy(smartDefault = "now.toString()")
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityDef.copy(properties = listOf(draftProp))))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasChanges).isTrue()
  }

  @Test
  fun `computeVersionBump returns hasChanges=true when property valueProposals changes`() {
    val prop = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING, valueProposals = emptyList())
    val filterProp = Property(id = PropertyId("p-2"), name = "Group", type = PropertyType.STRING)
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop, filterProp))
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityDef))
    val draftProp = prop.copy(valueProposals = listOf("p-2"))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityDef.copy(properties = listOf(draftProp, filterProp))))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasChanges).isTrue()
  }

  @Test
  fun `computeVersionBump detects no breaking changes when entity definition added`() {
    val pub = publishedVersion.copy(entityDefinitions = emptyList())
    val newEntity = EntityDefinition(id = EntityDefinitionId("e-new"), name = "Customer")
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(newEntity))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isFalse()
  }

  @Test
  fun `computeVersionBump detects no breaking changes when only unit default granularity changes`() {
    val prop = Property(
      id = PropertyId("p-1"), name = "Distance", type = PropertyType.LONG,
      unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.METERS),
    )
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(prop))
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityDef))
    val draftProp = prop.copy(unit = prop.unit!!.copy(defaultGranularity = DistanceGranularity.KILOMETERS))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityDef.copy(properties = listOf(draftProp))))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isFalse()
    assertThat(result.getOrNull()!!.hasChanges).isTrue()
  }

  @ParameterizedTest(name = "computeVersionBump detects breaking change when {0}")
  @MethodSource("breakingChangeCases")
  fun `computeVersionBump detects breaking changes`(
    caseName: String,
    publishedEntities: List<EntityDefinition>,
    draftEntities: List<EntityDefinition>,
  ) {
    val pub = publishedVersion.copy(entityDefinitions = publishedEntities)
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = draftEntities)
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isTrue()
  }

  @Test
  fun `computeVersionBump reclassifies a breaking change as non-breaking when the compensating migration dry-run succeeds`() {
    val publishedProp = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = true)
    val draftProp = publishedProp.copy(nullable = false)
    val pub = publishedVersion.copy(entityDefinitions = listOf(EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(publishedProp))))
    val draftEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(draftProp), migrationScript = "it")
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT).copy(entityDefinitions = listOf(draftEntity))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isFalse()
    verify(exactly = 1) { appVersionMigration.dryRunMigration(AppId("app-1"), any()) }
  }

  @Test
  fun `computeVersionBump keeps a breaking change classified as breaking when the migration dry-run fails`() {
    val publishedProp = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = true)
    val draftProp = publishedProp.copy(nullable = false)
    val pub = publishedVersion.copy(entityDefinitions = listOf(EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(publishedProp))))
    val draftEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(draftProp), migrationScript = "it")
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT).copy(entityDefinitions = listOf(draftEntity))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)
    every { appVersionMigration.dryRunMigration(AppId("app-1"), any()) } returns
      AppVersionMigrationScriptFailedError("Order", "data-1", "", "boom").left()

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isTrue()
  }

  @Test
  fun `computeVersionBump keeps a breaking change classified as breaking without dry-running when draft has no compensating migration script`() {
    val publishedProp = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = true)
    val draftProp = publishedProp.copy(nullable = false)
    val pub = publishedVersion.copy(entityDefinitions = listOf(EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(publishedProp))))
    val draftEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(draftProp))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT).copy(entityDefinitions = listOf(draftEntity))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isTrue()
    verify(exactly = 0) { appVersionMigration.dryRunMigration(any(), any()) }
  }

  @Test
  fun `computeVersionBump fails when app not found`() {
    every { appRepository.findById(AppId("unknown")) } returns null

    val result = service.computeVersionBump("unknown", "ver-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.APP_NOT_FOUND)
  }

  @Test
  fun `computeVersionBump fails when version not found`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.computeVersionBump("app-1", "unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `computeVersionBump fails when version belongs to different app`() {
    val versionOfOtherApp = version(id = "ver-1", appId = "app-2")
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns versionOfOtherApp

    val result = service.computeVersionBump("app-1", "ver-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `computeVersionBump fails when version is not a draft`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-2")) } returns publishedVersion

    val result = service.computeVersionBump("app-1", "ver-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_IN_DRAFT)
  }

  // endregion

  // region addEntity

  @Test
  fun `addEntity adds entity to draft version`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
    justRun { appVersionRepository.save(any()) }

    val result = service.addEntity("app-1", "ver-1", "Order")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions).hasSize(1)
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.name).isEqualTo("Order")
  }

  @Test
  fun `addEntity fails when name is blank`() {
    val result = service.addEntity("app-1", "ver-1", "  ")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.BLANK_INPUT)
  }

  @Test
  fun `addEntity fails when entity name already exists`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addEntity("app-1", "ver-1", "Order")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NAME_ALREADY_EXISTS)
  }

  @Test
  fun `addEntity fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.addEntity("app-1", "unknown", "Order")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `addEntity fails when app is inactive`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp.copy(status = AppStatus.INACTIVE)

    val result = service.addEntity("app-1", "ver-1", "Order")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.APP_INACTIVE)
  }

  // endregion

  // region deleteEntity

  @Test
  fun `deleteEntity removes entity from draft version`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.deleteEntity("app-1", "ver-1", "e-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions).isEmpty()
  }

  @Test
  fun `deleteEntity fails when entity not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.deleteEntity("app-1", "ver-1", "unknown-entity")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `deleteEntity clears dangling target entity references on other entities`() {
    val targetEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Customer")
    val refProp = Property(id = PropertyId("p-1"), name = "Customer", type = PropertyType.REF, targetEntityId = EntityDefinitionId("e-1"))
    val sourceEntity = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Order", properties = listOf(refProp))
    val version = draftVersion.copy(entityDefinitions = listOf(targetEntity, sourceEntity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.deleteEntity("app-1", "ver-1", "e-1")

    assertThat(result.isRight()).isTrue()
    val remainingProp = result.getOrNull()?.entityDefinitions?.first { it.id.value == "e-2" }?.properties?.first { it.id.value == "p-1" }
    assertThat(remainingProp?.targetEntityId).isNull()
  }

  // endregion

  // region reorderEntities

  @Test
  fun `reorderEntities reorders entities in draft version`() {
    val entity1 = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val entity2 = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Product")
    val version = draftVersion.copy(entityDefinitions = listOf(entity1, entity2))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.reorderEntities("app-1", "ver-1", listOf("e-2", "e-1"))

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.map { it.id.value }).containsExactly("e-2", "e-1")
  }

  @Test
  fun `reorderEntities fails when entity IDs mismatch`() {
    val entity1 = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val entity2 = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Product")
    val version = draftVersion.copy(entityDefinitions = listOf(entity1, entity2))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.reorderEntities("app-1", "ver-1", listOf("e-1", "e-unknown"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_IDS_MISMATCH)
  }

  @Test
  fun `reorderEntities fails when entity IDs are missing`() {
    val entity1 = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val entity2 = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Product")
    val version = draftVersion.copy(entityDefinitions = listOf(entity1, entity2))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.reorderEntities("app-1", "ver-1", listOf("e-1"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_IDS_MISMATCH)
  }

  @Test
  fun `reorderEntities fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.reorderEntities("app-1", "unknown", listOf("e-1"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  // endregion

  // region updateEntitySortCriteria

  @Test
  fun `updateEntitySortCriteria updates sort criteria for entity`() {
    val prop1 = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = false)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop1))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val sortBy = listOf(SortCriteria(propertyId = "p-1", direction = SortDirection.DESC))
    val result = service.updateEntitySortCriteria("app-1", "ver-1", "e-1", sortBy)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.sortBy).hasSize(1)
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.sortBy?.first()?.propertyId).isEqualTo("p-1")
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.sortBy?.first()?.direction).isEqualTo(SortDirection.DESC)
  }

  @Test
  fun `updateEntitySortCriteria ignores unknown property IDs`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val sortBy = listOf(SortCriteria(propertyId = "unknown-prop", direction = SortDirection.ASC))
    val result = service.updateEntitySortCriteria("app-1", "ver-1", "e-1", sortBy)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.sortBy).isEmpty()
  }

  @Test
  fun `updateEntitySortCriteria fails when entity not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.updateEntitySortCriteria("app-1", "ver-1", "unknown-entity", emptyList())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  // endregion

  // region addProperty

  @Test
  fun `addProperty adds property to entity in draft version`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.addProperty("app-1", "ver-1", "e-1", "Amount", "LONG", true, null)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.properties).hasSize(1)
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.properties?.first()?.name).isEqualTo("Amount")
  }

  @Test
  fun `addProperty fails when property type is invalid`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addProperty("app-1", "ver-1", "e-1", "Amount", "INVALID_TYPE", true, null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.INVALID_PROPERTY_TYPE)
  }

  @Test
  fun `addProperty fails when entity not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.addProperty("app-1", "ver-1", "unknown-entity", "Amount", "LONG", true, null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `addProperty sets target entity on REF property`() {
    val sourceEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val targetEntity = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Customer")
    val version = draftVersion.copy(entityDefinitions = listOf(sourceEntity, targetEntity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.addProperty("app-1", "ver-1", "e-1", "Customer", "REF", true, "e-2")

    assertThat(result.isRight()).isTrue()
    val property = result.getOrNull()?.entityDefinitions?.first { it.id.value == "e-1" }?.properties?.first()
    assertThat(property?.targetEntityId).isEqualTo(EntityDefinitionId("e-2"))
  }

  @Test
  fun `addProperty fails when REF property has no target entity`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addProperty("app-1", "ver-1", "e-1", "Customer", "REF", true, null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.TARGET_ENTITY_REQUIRED)
  }

  @Test
  fun `addProperty fails when REF property target entity not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addProperty("app-1", "ver-1", "e-1", "Customer", "REF", true, "unknown-entity")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.TARGET_ENTITY_NOT_FOUND)
  }

  @Test
  fun `addProperty sets list item type on LIST property`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.addProperty("app-1", "ver-1", "e-1", "Tags", "LIST", true, null, "STRING")

    assertThat(result.isRight()).isTrue()
    val property = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(property?.listItemType).isEqualTo(PropertyType.STRING)
  }

  @Test
  fun `addProperty fails when LIST property has no list item type`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addProperty("app-1", "ver-1", "e-1", "Tags", "LIST", true, null, null)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.LIST_ITEM_TYPE_REQUIRED)
  }

  @Test
  fun `addProperty fails when LIST property has invalid list item type`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addProperty("app-1", "ver-1", "e-1", "Tags", "LIST", true, null, "INVALID_TYPE")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.LIST_ITEM_TYPE_INVALID)
  }

  @Test
  fun `addProperty fails when non-LIST property has a list item type`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addProperty("app-1", "ver-1", "e-1", "Amount", "LONG", true, null, "STRING")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.LIST_ITEM_TYPE_NOT_SUPPORTED)
  }

  @Test
  fun `addProperty sets target entity for LIST of REF property`() {
    val sourceEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val targetEntity = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Customer")
    val version = draftVersion.copy(entityDefinitions = listOf(sourceEntity, targetEntity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.addProperty("app-1", "ver-1", "e-1", "Customers", "LIST", true, "e-2", "REF")

    assertThat(result.isRight()).isTrue()
    val property = result.getOrNull()?.entityDefinitions?.first { it.id.value == "e-1" }?.properties?.first()
    assertThat(property?.listItemType).isEqualTo(PropertyType.REF)
    assertThat(property?.targetEntityId).isEqualTo(EntityDefinitionId("e-2"))
  }

  @Test
  fun `addProperty fails when LIST of REF property has no target entity`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addProperty("app-1", "ver-1", "e-1", "Customers", "LIST", true, null, "REF")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.TARGET_ENTITY_REQUIRED)
  }

  // endregion

  // region updateProperty

  @Test
  fun `updateProperty updates name, type and nullable of property in entity in draft version`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = true)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "Total", "DOUBLE", false)

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.name).isEqualTo("Total")
    assertThat(updatedProp?.type).isEqualTo(PropertyType.DOUBLE)
    assertThat(updatedProp?.nullable).isFalse()
  }

  @Test
  fun `updateProperty preserves constraints when type is unchanged`() {
    val property = Property(
      id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG,
      constraints = setOf(PropertyConstraint.MinLong(0L)),
    )
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "Count", "LONG", true)

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.constraints).containsExactly(PropertyConstraint.MinLong(0L))
  }

  @Test
  fun `updateProperty clears constraints when type changes`() {
    val property = Property(
      id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG,
      constraints = setOf(PropertyConstraint.MinLong(0L)),
    )
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "Amount", "STRING", true)

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.constraints).isEmpty()
  }

  @Test
  fun `updateProperty preserves target entity when type stays REF`() {
    val property = Property(id = PropertyId("p-1"), name = "Customer", type = PropertyType.REF, targetEntityId = EntityDefinitionId("e-2"))
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "Buyer", "REF", true)

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.targetEntityId).isEqualTo(EntityDefinitionId("e-2"))
  }

  @Test
  fun `updateProperty clears target entity when type changes away from REF`() {
    val property = Property(id = PropertyId("p-1"), name = "Customer", type = PropertyType.REF, targetEntityId = EntityDefinitionId("e-2"))
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "Customer", "STRING", true)

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.targetEntityId).isNull()
  }

  @Test
  fun `updateProperty fails when name is blank`() {
    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "  ", "LONG", true)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.BLANK_INPUT)
  }

  @Test
  fun `updateProperty fails when property type is invalid`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "Amount", "INVALID_TYPE", true)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.INVALID_PROPERTY_TYPE)
  }

  @Test
  fun `updateProperty fails when property name already exists on another property`() {
    val p1 = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
    val p2 = Property(id = PropertyId("p-2"), name = "Label", type = PropertyType.STRING)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(p1, p2))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "Label", "LONG", true)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_NAME_ALREADY_EXISTS)
  }

  @ParameterizedTest(name = "updateProperty fails when {0} not found")
  @EnumSource(NotFoundTarget::class)
  fun `updateProperty fails when target not found`(target: NotFoundTarget) {
    val versionId = if (target == NotFoundTarget.VERSION) "unknown" else "ver-1"
    val entityId = if (target == NotFoundTarget.ENTITY) "unknown-entity" else "e-1"
    val propertyId = if (target == NotFoundTarget.PROPERTY) "unknown-prop" else "p-1"
    val expectedError = when (target) {
      NotFoundTarget.VERSION -> {
        every { appVersionRepository.findById(AppVersionId("unknown")) } returns null
        AppVersionError.VERSION_NOT_FOUND
      }
      NotFoundTarget.ENTITY -> {
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
        AppVersionError.ENTITY_NOT_FOUND
      }
      NotFoundTarget.PROPERTY -> {
        val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion.copy(entityDefinitions = listOf(entity))
        AppVersionError.PROPERTY_NOT_FOUND
      }
    }

    val result = service.updateProperty("app-1", versionId, entityId, propertyId, "Amount", "LONG", true)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(expectedError)
  }

  // endregion

  // region setPropertyConstraints

  @Test
  fun `setPropertyConstraints sets constraints on property`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyConstraints(
      "app-1", "ver-1", "e-1", "p-1",
      setOf(PropertyConstraint.MinLong(0L), PropertyConstraint.MaxLong(100L)),
    )

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.constraints).containsExactlyInAnyOrder(
      PropertyConstraint.MinLong(0L),
      PropertyConstraint.MaxLong(100L),
    )
  }

  @Test
  fun `setPropertyConstraints ignores constraints not applicable to property type`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyConstraints(
      "app-1", "ver-1", "e-1", "p-1",
      setOf(PropertyConstraint.MinLong(0L), PropertyConstraint.MinLength(5)),
    )

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.constraints).containsExactly(PropertyConstraint.MinLong(0L))
  }

  @Test
  fun `setPropertyConstraints clears constraints when empty set provided`() {
    val property = Property(
      id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG,
      constraints = setOf(PropertyConstraint.MinLong(0L)),
    )
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyConstraints("app-1", "ver-1", "e-1", "p-1", emptySet())

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.constraints).isEmpty()
  }

  @ParameterizedTest(name = "setPropertyConstraints fails when {0} not found")
  @EnumSource(NotFoundTarget::class)
  fun `setPropertyConstraints fails when target not found`(target: NotFoundTarget) {
    val versionId = if (target == NotFoundTarget.VERSION) "unknown" else "ver-1"
    val entityId = if (target == NotFoundTarget.ENTITY) "unknown-entity" else "e-1"
    val propertyId = if (target == NotFoundTarget.PROPERTY) "unknown-prop" else "p-1"
    val expectedError = when (target) {
      NotFoundTarget.VERSION -> {
        every { appVersionRepository.findById(AppVersionId("unknown")) } returns null
        AppVersionError.VERSION_NOT_FOUND
      }
      NotFoundTarget.ENTITY -> {
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
        AppVersionError.ENTITY_NOT_FOUND
      }
      NotFoundTarget.PROPERTY -> {
        val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion.copy(entityDefinitions = listOf(entity))
        AppVersionError.PROPERTY_NOT_FOUND
      }
    }

    val result = service.setPropertyConstraints("app-1", versionId, entityId, propertyId, emptySet())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(expectedError)
  }

  // endregion

  // region setPropertyDefault

  @Test
  fun `setPropertyDefault sets default on property`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    every { propertyConstraintPort.checkValue(any(), any(), any()) } returns emptyList()
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", "42")

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.default).isEqualTo("42")
  }

  @Test
  fun `setPropertyDefault clears default when null provided`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, default = "10")
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", null)

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.default).isNull()
  }

  @Test
  fun `setPropertyDefault clears default when blank provided`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, default = "10")
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", "  ")

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.default).isNull()
  }

  @Test
  fun `setPropertyDefault fails when default violates constraint`() {
    val property = Property(
      id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG,
      constraints = setOf(PropertyConstraint.MinLong(10L)),
    )
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    every { propertyConstraintPort.checkValue(any(), any(), any()) } returns listOf(PropertyConstraintViolation.MinValueViolation(10L))

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", "5")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.DEFAULT_VALUE_INVALID)
  }

  @Test
  fun `setPropertyDefault fails when default is not a valid number for LONG type`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", "notanumber")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.DEFAULT_VALUE_INVALID)
  }

  @ParameterizedTest(name = "setPropertyDefault fails for {0} type")
  @EnumSource(value = PropertyType::class, names = ["LIST", "OBJECT", "REF"])
  fun `setPropertyDefault fails for types not supporting a default`(type: PropertyType) {
    val property = Property(id = PropertyId("p-1"), name = "Prop", type = type)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", "somevalue")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.DEFAULT_NOT_SUPPORTED)
  }

  @ParameterizedTest(name = "setPropertyDefault fails when {0} not found")
  @EnumSource(NotFoundTarget::class)
  fun `setPropertyDefault fails when target not found`(target: NotFoundTarget) {
    val versionId = if (target == NotFoundTarget.VERSION) "unknown" else "ver-1"
    val entityId = if (target == NotFoundTarget.ENTITY) "unknown-entity" else "e-1"
    val propertyId = if (target == NotFoundTarget.PROPERTY) "unknown-prop" else "p-1"
    val expectedError = when (target) {
      NotFoundTarget.VERSION -> {
        every { appVersionRepository.findById(AppVersionId("unknown")) } returns null
        AppVersionError.VERSION_NOT_FOUND
      }
      NotFoundTarget.ENTITY -> {
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
        AppVersionError.ENTITY_NOT_FOUND
      }
      NotFoundTarget.PROPERTY -> {
        val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion.copy(entityDefinitions = listOf(entity))
        AppVersionError.PROPERTY_NOT_FOUND
      }
    }

    val result = service.setPropertyDefault("app-1", versionId, entityId, propertyId, "42")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(expectedError)
  }

  @Test
  fun `setPropertyDefault fails when smart default is already set`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, smartDefault = "42L")
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", "10")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.BOTH_DEFAULTS_SET)
  }

  // endregion

  // region setPropertySmartDefault

  @Test
  fun `setPropertySmartDefault sets smart default on property`() {
    val property = Property(id = PropertyId("p-1"), name = "CreatedDate", type = PropertyType.DATE)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertySmartDefault("app-1", "ver-1", "e-1", "p-1", "now.toString()")

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.smartDefault).isEqualTo("now.toString()")
  }

  @Test
  fun `setPropertySmartDefault clears smart default when null provided`() {
    val property = Property(id = PropertyId("p-1"), name = "CreatedDate", type = PropertyType.DATE, smartDefault = "now.toString()")
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertySmartDefault("app-1", "ver-1", "e-1", "p-1", null)

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.smartDefault).isNull()
  }

  @Test
  fun `setPropertySmartDefault clears smart default when blank provided`() {
    val property = Property(id = PropertyId("p-1"), name = "CreatedDate", type = PropertyType.DATE, smartDefault = "now.toString()")
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertySmartDefault("app-1", "ver-1", "e-1", "p-1", "  ")

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.smartDefault).isNull()
  }

  @ParameterizedTest(name = "setPropertySmartDefault fails for {0} type when setting a script")
  @EnumSource(value = PropertyType::class, names = ["LIST", "OBJECT", "REF"])
  fun `setPropertySmartDefault fails for types not supporting a smart default`(type: PropertyType) {
    val property = Property(id = PropertyId("p-1"), name = "Prop", type = type)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertySmartDefault("app-1", "ver-1", "e-1", "p-1", "now.toString()")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.SMART_DEFAULT_NOT_SUPPORTED)
  }

  @Test
  fun `setPropertySmartDefault allows clearing smart default for non-supported types`() {
    val property = Property(id = PropertyId("p-1"), name = "RefProp", type = PropertyType.REF, smartDefault = "someScript")
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertySmartDefault("app-1", "ver-1", "e-1", "p-1", null)

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.smartDefault).isNull()
  }

  @ParameterizedTest(name = "setPropertySmartDefault fails when {0} not found")
  @EnumSource(NotFoundTarget::class)
  fun `setPropertySmartDefault fails when target not found`(target: NotFoundTarget) {
    val versionId = if (target == NotFoundTarget.VERSION) "unknown" else "ver-1"
    val entityId = if (target == NotFoundTarget.ENTITY) "unknown-entity" else "e-1"
    val propertyId = if (target == NotFoundTarget.PROPERTY) "unknown-prop" else "p-1"
    val expectedError = when (target) {
      NotFoundTarget.VERSION -> {
        every { appVersionRepository.findById(AppVersionId("unknown")) } returns null
        AppVersionError.VERSION_NOT_FOUND
      }
      NotFoundTarget.ENTITY -> {
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
        AppVersionError.ENTITY_NOT_FOUND
      }
      NotFoundTarget.PROPERTY -> {
        val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion.copy(entityDefinitions = listOf(entity))
        AppVersionError.PROPERTY_NOT_FOUND
      }
    }

    val result = service.setPropertySmartDefault("app-1", versionId, entityId, propertyId, "now.toString()")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(expectedError)
  }

  @Test
  fun `setPropertySmartDefault fails when static default is already set`() {
    val property = Property(id = PropertyId("p-1"), name = "CreatedDate", type = PropertyType.DATE, default = "2025-01-01")
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertySmartDefault("app-1", "ver-1", "e-1", "p-1", "now.toString()")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.BOTH_DEFAULTS_SET)
  }

  // endregion

  // region nested property editing via path

  @Test
  fun `addProperty adds a property nested inside an OBJECT property`() {
    val nestedExisting = Property(id = PropertyId("n-0"), name = "Existing", type = PropertyType.STRING)
    val property = Property(id = PropertyId("p-1"), name = "Meta", type = PropertyType.OBJECT, nestedProperties = listOf(nestedExisting))
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.addProperty("app-1", "ver-1", "e-1", "Nested", "STRING", false, null, null, listOf("p-1"))

    assertThat(result.isRight()).isTrue()
    val updatedNested = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()?.nestedProperties
    assertThat(updatedNested).extracting<String> { it.name }.containsExactly("Existing", "Nested")
  }

  @Test
  fun `addProperty fails when path does not resolve to an OBJECT property`() {
    val property = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addProperty("app-1", "ver-1", "e-1", "Nested", "STRING", false, null, null, listOf("p-1"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_NOT_FOUND)
  }

  // endregion

  // region reorderProperties

  @Test
  fun `reorderProperties reorders properties in entity`() {
    val p1 = Property(id = PropertyId("p-1"), name = "First", type = PropertyType.STRING)
    val p2 = Property(id = PropertyId("p-2"), name = "Second", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(p1, p2))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.reorderProperties("app-1", "ver-1", "e-1", listOf("p-2", "p-1"))

    assertThat(result.isRight()).isTrue()
    val reordered = result.getOrNull()?.entityDefinitions?.first()?.properties
    assertThat(reordered?.map { it.id.value }).containsExactly("p-2", "p-1")
  }

  @Test
  fun `reorderProperties fails when IDs do not match`() {
    val p1 = Property(id = PropertyId("p-1"), name = "First", type = PropertyType.STRING)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(p1))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.reorderProperties("app-1", "ver-1", "e-1", listOf("p-1", "p-unknown"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_IDS_MISMATCH)
  }

  @Test
  fun `reorderProperties fails when entity not found`() {
    val version = draftVersion.copy(entityDefinitions = emptyList())
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.reorderProperties("app-1", "ver-1", "unknown-entity", listOf("p-1"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `reorderProperties fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.reorderProperties("app-1", "unknown", "e-1", listOf("p-1"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  // endregion

  // region addReport

  @Test
  fun `addReport adds report to draft version`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
    justRun { appVersionRepository.save(any()) }

    val result = service.addReport("app-1", "ver-1", "Sales Report")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.reports).hasSize(1)
    assertThat(result.getOrNull()?.reports?.first()?.name).isEqualTo("Sales Report")
  }

  @Test
  fun `addReport fails when report name already exists`() {
    val report = Report(id = ReportId("r-1"), name = "Sales Report")
    val version = draftVersion.copy(reports = listOf(report))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addReport("app-1", "ver-1", "Sales Report")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.REPORT_NAME_ALREADY_EXISTS)
  }

  // endregion

  // region deleteReport

  @Test
  fun `deleteReport removes report from draft version`() {
    val report = Report(id = ReportId("r-1"), name = "Sales Report")
    val version = draftVersion.copy(reports = listOf(report))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.deleteReport("app-1", "ver-1", "r-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.reports).isEmpty()
  }

  @Test
  fun `deleteReport fails when report not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.deleteReport("app-1", "ver-1", "unknown-report")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.REPORT_NOT_FOUND)
  }

  // endregion

  // region updateReport

  @Test
  fun `updateReport updates html and script of report in draft version`() {
    val report = Report(id = ReportId("r-1"), name = "Sales Report")
    val version = draftVersion.copy(reports = listOf(report))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateReport("app-1", "ver-1", "r-1", "<h1>Sales</h1>", "console.log('hello')")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.reports?.first()?.html).isEqualTo("<h1>Sales</h1>")
    assertThat(result.getOrNull()?.reports?.first()?.script).isEqualTo("console.log('hello')")
  }

  @Test
  fun `updateReport fails when report not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.updateReport("app-1", "ver-1", "unknown-report", "<h1>x</h1>", "")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.REPORT_NOT_FOUND)
  }

  // endregion

  // region getVersionDiff

  @Test
  fun `getVersionDiff returns diff for published version with predecessor`() {
    val predecessor = version(id = "ver-old", appId = "app-1", versionNumber = "1.0.0", status = AppVersionStatus.PUBLISHED)
      .copy(createdAt = publishedVersion.createdAt.minusSeconds(100))
    every { appVersionRepository.findById(AppVersionId("ver-2")) } returns publishedVersion
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(predecessor, publishedVersion)

    val result = service.getVersionDiff("app-1", "ver-2")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.version).isEqualTo(publishedVersion)
    assertThat(result.getOrNull()!!.previousVersion).isEqualTo(predecessor)
  }

  @Test
  fun `getVersionDiff fails for published version when no predecessor exists`() {
    every { appVersionRepository.findById(AppVersionId("ver-2")) } returns publishedVersion
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(publishedVersion)

    val result = service.getVersionDiff("app-1", "ver-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.NO_PREDECESSOR_VERSION)
  }

  @Test
  fun `getVersionDiff returns diff for draft version against latest published version`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(publishedVersion, draftVersion)

    val result = service.getVersionDiff("app-1", "ver-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.version).isEqualTo(draftVersion)
    assertThat(result.getOrNull()!!.previousVersion).isEqualTo(publishedVersion)
  }

  @Test
  fun `getVersionDiff fails for draft version when no published version exists`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersion)

    val result = service.getVersionDiff("app-1", "ver-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.NO_PREDECESSOR_VERSION)
  }

  @Test
  fun `getVersionDiff fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.getVersionDiff("app-1", "unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `getVersionDiff fails when version does not belong to app`() {
    val otherAppVersion = version(id = "ver-2", appId = "other-app", versionNumber = "1.0.0", status = AppVersionStatus.PUBLISHED)
    every { appVersionRepository.findById(AppVersionId("ver-2")) } returns otherAppVersion

    val result = service.getVersionDiff("app-1", "ver-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @ParameterizedTest(name = "getVersionDiff shows {0} in diff lines")
  @MethodSource("versionDiffCases")
  fun `getVersionDiff shows changes in diff lines`(
    caseName: String,
    predecessorEntities: List<EntityDefinition>,
    currentEntities: List<EntityDefinition>,
    expectedLineSubstringGroups: List<List<String>>,
  ) {
    val predecessor = version(id = "ver-old", appId = "app-1", versionNumber = "1.0.0", status = AppVersionStatus.PUBLISHED)
      .copy(entityDefinitions = predecessorEntities, createdAt = publishedVersion.createdAt.minusSeconds(100))
    val currentVersion = version(id = "ver-new", appId = "app-1", versionNumber = "1.1.0", status = AppVersionStatus.PUBLISHED)
      .copy(entityDefinitions = currentEntities)
    every { appVersionRepository.findById(AppVersionId("ver-new")) } returns currentVersion
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(predecessor, currentVersion)

    val result = service.getVersionDiff("app-1", "ver-new")

    assertThat(result.isRight()).isTrue()
    val diff = result.getOrNull()!!
    assertThat(diff.entityDiffs).isNotEmpty()
    val lines = diff.entityDiffs.first().lines.map { it.text }
    expectedLineSubstringGroups.forEach { group ->
      assertThat(lines).anyMatch { line -> group.all { line.contains(it) } }
    }
  }

  // endregion

  // region deleteDraftVersion

  @Test
  fun `deleteDraftVersion deletes draft version`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion
    justRun { appVersionRepository.delete(AppVersionId("ver-1")) }

    val result = service.deleteDraftVersion("app-1", "ver-1")

    assertThat(result.isRight()).isTrue()
    verify { appVersionRepository.delete(AppVersionId("ver-1")) }
  }

  @Test
  fun `deleteDraftVersion fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.deleteDraftVersion("app-1", "unknown")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `deleteDraftVersion fails when version is not in draft status`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns publishedVersion

    val result = service.deleteDraftVersion("app-1", "ver-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `deleteDraftVersion fails when app is inactive`() {
    every { appRepository.findById(AppId("app-1")) } returns existingApp.copy(status = AppStatus.INACTIVE)

    val result = service.deleteDraftVersion("app-1", "ver-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.APP_INACTIVE)
  }

  // endregion

  // region updateEntityDisplayText

  @Test
  fun `updateEntityDisplayText sets display text using property names`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = false)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateEntityDisplayText("app-1", "ver-1", "e-1", "Order {Amount}")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.displayText).isEqualTo("Order {Amount}")
  }

  @Test
  fun `updateEntityDisplayText clears display text when blank`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", displayText = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateEntityDisplayText("app-1", "ver-1", "e-1", "  ")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.displayText).isNull()
  }

  @Test
  fun `updateEntityDisplayText fails when display text references nullable property name`() {
    val prop = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = true)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.updateEntityDisplayText("app-1", "ver-1", "e-1", "Order {Tag}")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.DISPLAY_TEXT_USES_NULLABLE_PROPERTY)
  }

  @Test
  fun `updateEntityDisplayText fails when display text references unknown property name`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.updateEntityDisplayText("app-1", "ver-1", "e-1", "Order {UnknownProp}")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.DISPLAY_TEXT_USES_NULLABLE_PROPERTY)
  }

  @Test
  fun `updateEntityDisplayText allows id token`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateEntityDisplayText("app-1", "ver-1", "e-1", "Order {id}")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.displayText).isEqualTo("Order {id}")
  }

  // endregion

  // region updateEntityMigrationScript

  @Test
  fun `updateEntityMigrationScript sets script`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateEntityMigrationScript("app-1", "ver-1", "e-1", "it + (\"newProp\" to \"default\")")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.migrationScript).isEqualTo("it + (\"newProp\" to \"default\")")
  }

  @Test
  fun `updateEntityMigrationScript clears script when blank`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", migrationScript = "it")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateEntityMigrationScript("app-1", "ver-1", "e-1", "   ")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.migrationScript).isNull()
  }

  @Test
  fun `updateEntityMigrationScript fails when entity not found`() {
    val version = draftVersion.copy(entityDefinitions = emptyList())
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.updateEntityMigrationScript("app-1", "ver-1", "unknown", "it")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  // endregion

  // region deleteProperty with display text

  @Test
  fun `deleteProperty removes property name token from display text`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = false)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop), displayText = "{Amount}")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.deleteProperty("app-1", "ver-1", "e-1", "p-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.displayText).isNull()
  }

  // endregion

  // region updateProperty with display text

  @Test
  fun `updateProperty removes old property name from display text when making property nullable`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = false)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop), displayText = "{Amount}")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "Amount", "LONG", true)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.displayText).isNull()
  }

  @Test
  fun `updateProperty does not modify display text when property stays non-nullable`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = false)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop), displayText = "Order {Amount}")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateProperty("app-1", "ver-1", "e-1", "p-1", "Amount", "LONG", false)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.displayText).isEqualTo("Order {Amount}")
  }

  // endregion

  // region publishVersion display text validation

  @Test
  fun `publishVersion fails when entity display text references unknown property name`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = false)
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop),
      displayText = "Order {OldName}",
    )
    val draft = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draft)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isInstanceOf(DisplayTextInvalidError::class.java)
    assertThat((result.leftOrNull() as DisplayTextInvalidError).entityNames).containsExactly("Order")
  }

  @Test
  fun `publishVersion fails when entity display text references nullable property name`() {
    val prop = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = true)
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop),
      displayText = "Order {Tag}",
    )
    val draft = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draft)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isInstanceOf(DisplayTextInvalidError::class.java)
    assertThat((result.leftOrNull() as DisplayTextInvalidError).entityNames).containsExactly("Order")
  }

  @Test
  fun `publishVersion collects all entities with invalid display text`() {
    val propA = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = false)
    val entityA = EntityDefinition(
      id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(propA),
      displayText = "{OldName}",
    )
    val propB = Property(id = PropertyId("p-2"), name = "Label", type = PropertyType.STRING, nullable = false)
    val entityB = EntityDefinition(
      id = EntityDefinitionId("e-2"), name = "Product", properties = listOf(propB),
      displayText = "{Gone}",
    )
    val draft = draftVersion.copy(entityDefinitions = listOf(entityA, entityB))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draft)

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isInstanceOf(DisplayTextInvalidError::class.java)
    assertThat((result.leftOrNull() as DisplayTextInvalidError).entityNames).containsExactlyInAnyOrder("Order", "Product")
  }

  @Test
  fun `publishVersion succeeds when all display text references are valid property names`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG, nullable = false)
    val entity = EntityDefinition(
      id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop),
      displayText = "Order {Amount}",
    )
    val draft = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draft)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `publishVersion succeeds when entity has no display text`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val draft = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draft)
    justRun { appVersionRepository.save(any()) }

    val result = service.publishVersion("app-1", "BUGFIX", releaseNotes)

    assertThat(result.isRight()).isTrue()
  }

  // endregion

  // region setPropertyValueProposals

  @Test
  fun `setPropertyValueProposals sets value proposals on STRING property`() {
    val prop1 = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING)
    val prop2 = Property(id = PropertyId("p-2"), name = "Group", type = PropertyType.STRING)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Item", properties = listOf(prop1, prop2))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyValueProposals("app-1", "ver-1", "e-1", "p-1", listOf("p-2"))

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first { it.id.value == "p-1" }
    assertThat(updatedProp?.valueProposals).containsExactly("p-2")
  }

  @Test
  fun `setPropertyValueProposals clears value proposals when empty list provided`() {
    val prop1 = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING, valueProposals = listOf("p-2"))
    val prop2 = Property(id = PropertyId("p-2"), name = "Group", type = PropertyType.STRING)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Item", properties = listOf(prop1, prop2))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyValueProposals("app-1", "ver-1", "e-1", "p-1", emptyList())

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first { it.id.value == "p-1" }
    assertThat(updatedProp?.valueProposals).isEmpty()
  }

  @Test
  fun `setPropertyValueProposals filters out invalid property IDs and self-reference`() {
    val prop1 = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING)
    val prop2 = Property(id = PropertyId("p-2"), name = "Group", type = PropertyType.STRING)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Item", properties = listOf(prop1, prop2))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyValueProposals("app-1", "ver-1", "e-1", "p-1", listOf("p-1", "p-2", "unknown-prop"))

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first { it.id.value == "p-1" }
    assertThat(updatedProp?.valueProposals).containsExactly("p-2")
  }

  @Test
  fun `setPropertyValueProposals fails for non-STRING property`() {
    val property = Property(id = PropertyId("p-1"), name = "Count", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Item", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyValueProposals("app-1", "ver-1", "e-1", "p-1", listOf("p-2"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VALUE_PROPOSALS_NOT_SUPPORTED)
  }

  @ParameterizedTest(name = "setPropertyValueProposals fails when {0} not found")
  @EnumSource(NotFoundTarget::class)
  fun `setPropertyValueProposals fails when target not found`(target: NotFoundTarget) {
    val entityId = if (target == NotFoundTarget.ENTITY) "unknown-entity" else "e-1"
    val propertyId = if (target == NotFoundTarget.PROPERTY) "unknown-prop" else "p-1"
    val expectedError = when (target) {
      NotFoundTarget.VERSION -> {
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns null
        AppVersionError.VERSION_NOT_FOUND
      }
      NotFoundTarget.ENTITY -> {
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion.copy(entityDefinitions = emptyList())
        AppVersionError.ENTITY_NOT_FOUND
      }
      NotFoundTarget.PROPERTY -> {
        val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Item")
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion.copy(entityDefinitions = listOf(entity))
        AppVersionError.PROPERTY_NOT_FOUND
      }
    }

    val result = service.setPropertyValueProposals("app-1", "ver-1", entityId, propertyId, emptyList())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(expectedError)
  }

  // endregion

  // region setPropertyTargetEntity

  @Test
  fun `setPropertyTargetEntity sets target entity on REF property`() {
    val refProp = Property(id = PropertyId("p-1"), name = "Customer", type = PropertyType.REF)
    val sourceEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(refProp))
    val targetEntity = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Customer")
    val version = draftVersion.copy(entityDefinitions = listOf(sourceEntity, targetEntity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyTargetEntity("app-1", "ver-1", "e-1", "p-1", "e-2")

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first { it.id.value == "e-1" }?.properties?.first { it.id.value == "p-1" }
    assertThat(updatedProp?.targetEntityId).isEqualTo(EntityDefinitionId("e-2"))
  }

  @Test
  fun `setPropertyTargetEntity fails when blank value provided for REF property`() {
    val refProp = Property(id = PropertyId("p-1"), name = "Customer", type = PropertyType.REF, targetEntityId = EntityDefinitionId("e-2"))
    val sourceEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(refProp))
    val targetEntity = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Customer")
    val version = draftVersion.copy(entityDefinitions = listOf(sourceEntity, targetEntity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyTargetEntity("app-1", "ver-1", "e-1", "p-1", "")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.TARGET_ENTITY_REQUIRED)
  }

  @Test
  fun `setPropertyTargetEntity allows self-referencing entity`() {
    val refProp = Property(id = PropertyId("p-1"), name = "Parent", type = PropertyType.REF)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Category", properties = listOf(refProp))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyTargetEntity("app-1", "ver-1", "e-1", "p-1", "e-1")

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first { it.id.value == "p-1" }
    assertThat(updatedProp?.targetEntityId).isEqualTo(EntityDefinitionId("e-1"))
  }

  @Test
  fun `setPropertyTargetEntity fails for non-REF property`() {
    val property = Property(id = PropertyId("p-1"), name = "Count", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Item", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyTargetEntity("app-1", "ver-1", "e-1", "p-1", "e-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.TARGET_ENTITY_NOT_SUPPORTED)
  }

  @Test
  fun `setPropertyTargetEntity fails when target entity not found`() {
    val refProp = Property(id = PropertyId("p-1"), name = "Customer", type = PropertyType.REF)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(refProp))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyTargetEntity("app-1", "ver-1", "e-1", "p-1", "unknown-entity")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.TARGET_ENTITY_NOT_FOUND)
  }

  @ParameterizedTest(name = "setPropertyTargetEntity fails when {0} not found")
  @EnumSource(NotFoundTarget::class)
  fun `setPropertyTargetEntity fails when target not found`(target: NotFoundTarget) {
    val entityId = if (target == NotFoundTarget.ENTITY) "unknown-entity" else "e-1"
    val propertyId = if (target == NotFoundTarget.PROPERTY) "unknown-prop" else "p-1"
    val expectedError = when (target) {
      NotFoundTarget.VERSION -> {
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns null
        AppVersionError.VERSION_NOT_FOUND
      }
      NotFoundTarget.ENTITY -> {
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion.copy(entityDefinitions = emptyList())
        AppVersionError.ENTITY_NOT_FOUND
      }
      NotFoundTarget.PROPERTY -> {
        val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Item")
        every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion.copy(entityDefinitions = listOf(entity))
        AppVersionError.PROPERTY_NOT_FOUND
      }
    }

    val result = service.setPropertyTargetEntity("app-1", "ver-1", entityId, propertyId, "e-2")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(expectedError)
  }

  // endregion

  // region setPropertyListItemType

  @Test
  fun `setPropertyListItemType sets list item type on LIST property`() {
    val property = Property(id = PropertyId("p-1"), name = "Tags", type = PropertyType.LIST)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyListItemType("app-1", "ver-1", "e-1", "p-1", "STRING")

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.listItemType).isEqualTo(PropertyType.STRING)
  }

  @Test
  fun `setPropertyListItemType fails for non-LIST property`() {
    val property = Property(id = PropertyId("p-1"), name = "Count", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyListItemType("app-1", "ver-1", "e-1", "p-1", "STRING")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.LIST_ITEM_TYPE_NOT_SUPPORTED)
  }

  @Test
  fun `setPropertyListItemType fails when blank value provided`() {
    val property = Property(id = PropertyId("p-1"), name = "Tags", type = PropertyType.LIST, listItemType = PropertyType.STRING)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyListItemType("app-1", "ver-1", "e-1", "p-1", "")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.LIST_ITEM_TYPE_REQUIRED)
  }

  @Test
  fun `setPropertyListItemType fails for invalid item type`() {
    val property = Property(id = PropertyId("p-1"), name = "Tags", type = PropertyType.LIST)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyListItemType("app-1", "ver-1", "e-1", "p-1", "INVALID_TYPE")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.LIST_ITEM_TYPE_INVALID)
  }

  @Test
  fun `setPropertyListItemType fails when property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyListItemType("app-1", "ver-1", "e-1", "unknown-prop", "STRING")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_NOT_FOUND)
  }

  // endregion

  // region setPropertyUnit

  @Test
  fun `setPropertyUnit sets unit on LONG property`() {
    val property = Property(id = PropertyId("p-1"), name = "Distance", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyUnit("app-1", "ver-1", "e-1", "p-1", "DISTANCE", "METERS", "KILOMETERS")

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.unit).isEqualTo(PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.KILOMETERS))
  }

  @Test
  fun `setPropertyUnit sets unit on DOUBLE property`() {
    val property = Property(id = PropertyId("p-1"), name = "Duration", type = PropertyType.DOUBLE)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyUnit("app-1", "ver-1", "e-1", "p-1", "TIME", "SECONDS", "MINUTES")

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.unit).isEqualTo(PropertyUnit(UnitFamily.TIME, TimeGranularity.SECONDS, TimeGranularity.MINUTES))
  }

  @Test
  fun `setPropertyUnit clears unit when family is blank`() {
    val existingUnit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.KILOMETERS)
    val property = Property(id = PropertyId("p-1"), name = "Distance", type = PropertyType.LONG, unit = existingUnit)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setPropertyUnit("app-1", "ver-1", "e-1", "p-1", null, null, null)

    assertThat(result.isRight()).isTrue()
    val updatedProp = result.getOrNull()?.entityDefinitions?.first()?.properties?.first()
    assertThat(updatedProp?.unit).isNull()
  }

  @Test
  fun `setPropertyUnit fails for type that does not support units`() {
    val property = Property(id = PropertyId("p-1"), name = "Name", type = PropertyType.STRING)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyUnit("app-1", "ver-1", "e-1", "p-1", "DISTANCE", "METERS", "KILOMETERS")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.UNIT_NOT_SUPPORTED)
  }

  @Test
  fun `setPropertyUnit fails for invalid family`() {
    val property = Property(id = PropertyId("p-1"), name = "Distance", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyUnit("app-1", "ver-1", "e-1", "p-1", "WEIGHT", "METERS", "KILOMETERS")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.UNIT_FAMILY_INVALID)
  }

  @Test
  fun `setPropertyUnit fails for invalid storage granularity`() {
    val property = Property(id = PropertyId("p-1"), name = "Distance", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyUnit("app-1", "ver-1", "e-1", "p-1", "DISTANCE", "SECONDS", "KILOMETERS")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.UNIT_GRANULARITY_INVALID)
  }

  @Test
  fun `setPropertyUnit fails for invalid default granularity`() {
    val property = Property(id = PropertyId("p-1"), name = "Distance", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyUnit("app-1", "ver-1", "e-1", "p-1", "DISTANCE", "METERS", "SECONDS")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.UNIT_GRANULARITY_INVALID)
  }

  @Test
  fun `setPropertyUnit fails when property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyUnit("app-1", "ver-1", "e-1", "unknown-prop", "DISTANCE", "METERS", "KILOMETERS")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_NOT_FOUND)
  }

  // endregion

  // region addComputedProperty

  @Test
  fun `addComputedProperty adds computed property to entity in draft version`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.addComputedProperty("app-1", "ver-1", "e-1", "Total", "LONG")

    assertThat(result.isRight()).isTrue()
    val updatedEntity = result.getOrNull()?.entityDefinitions?.first()
    assertThat(updatedEntity?.computedProperties).hasSize(1)
    assertThat(updatedEntity?.computedProperties?.first()?.name).isEqualTo("Total")
    assertThat(updatedEntity?.computedProperties?.first()?.type).isEqualTo(PropertyType.LONG)
  }

  @Test
  fun `addComputedProperty fails when type is invalid`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addComputedProperty("app-1", "ver-1", "e-1", "Total", "INVALID_TYPE")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.INVALID_PROPERTY_TYPE)
  }

  @Test
  fun `addComputedProperty fails when type does not support computed properties`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addComputedProperty("app-1", "ver-1", "e-1", "Total", "REF")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.COMPUTED_PROPERTY_TYPE_NOT_SUPPORTED)
  }

  @Test
  fun `addComputedProperty fails when name already exists`() {
    val existing = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", computedProperties = listOf(existing))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addComputedProperty("app-1", "ver-1", "e-1", "Total", "LONG")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.COMPUTED_PROPERTY_NAME_ALREADY_EXISTS)
  }

  @Test
  fun `addComputedProperty fails when name is blank`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addComputedProperty("app-1", "ver-1", "e-1", "  ", "LONG")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.BLANK_INPUT)
  }

  @Test
  fun `addComputedProperty fails when entity not found`() {
    val version = draftVersion.copy(entityDefinitions = emptyList())
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addComputedProperty("app-1", "ver-1", "unknown-entity", "Total", "LONG")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  // endregion

  // region updateComputedProperty

  @Test
  fun `updateComputedProperty updates name and type`() {
    val existing = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", computedProperties = listOf(existing))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.updateComputedProperty("app-1", "ver-1", "e-1", "cp-1", "GrandTotal", "DOUBLE")

    assertThat(result.isRight()).isTrue()
    val updated = result.getOrNull()?.entityDefinitions?.first()?.computedProperties?.first()
    assertThat(updated?.name).isEqualTo("GrandTotal")
    assertThat(updated?.type).isEqualTo(PropertyType.DOUBLE)
  }

  @Test
  fun `updateComputedProperty fails when computed property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.updateComputedProperty("app-1", "ver-1", "e-1", "unknown-cp", "Total", "LONG")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.COMPUTED_PROPERTY_NOT_FOUND)
  }

  // endregion

  // region setComputedPropertyScript

  @Test
  fun `setComputedPropertyScript sets script on computed property`() {
    val existing = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", computedProperties = listOf(existing))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setComputedPropertyScript("app-1", "ver-1", "e-1", "cp-1", "it[\"qty\"]?.toLongOrNull() ?: 0L")

    assertThat(result.isRight()).isTrue()
    val updated = result.getOrNull()?.entityDefinitions?.first()?.computedProperties?.first()
    assertThat(updated?.script).isEqualTo("it[\"qty\"]?.toLongOrNull() ?: 0L")
  }

  @Test
  fun `setComputedPropertyScript clears script when null provided`() {
    val existing = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG, script = "42L")
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", computedProperties = listOf(existing))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.setComputedPropertyScript("app-1", "ver-1", "e-1", "cp-1", null)

    assertThat(result.isRight()).isTrue()
    val updated = result.getOrNull()?.entityDefinitions?.first()?.computedProperties?.first()
    assertThat(updated?.script).isNull()
  }

  @Test
  fun `setComputedPropertyScript fails when computed property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setComputedPropertyScript("app-1", "ver-1", "e-1", "unknown-cp", "42L")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.COMPUTED_PROPERTY_NOT_FOUND)
  }

  // endregion

  // region reorderComputedProperties

  @Test
  fun `reorderComputedProperties reorders computed properties`() {
    val cp1 = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "First", type = PropertyType.STRING)
    val cp2 = ComputedProperty(id = ComputedPropertyId("cp-2"), name = "Second", type = PropertyType.STRING)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", computedProperties = listOf(cp1, cp2))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.reorderComputedProperties("app-1", "ver-1", "e-1", listOf("cp-2", "cp-1"))

    assertThat(result.isRight()).isTrue()
    val reordered = result.getOrNull()?.entityDefinitions?.first()?.computedProperties
    assertThat(reordered?.map { it.id.value }).containsExactly("cp-2", "cp-1")
  }

  @Test
  fun `reorderComputedProperties fails when IDs do not match`() {
    val cp1 = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "First", type = PropertyType.STRING)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", computedProperties = listOf(cp1))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.reorderComputedProperties("app-1", "ver-1", "e-1", listOf("cp-1", "cp-unknown"))

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_IDS_MISMATCH)
  }

  // endregion

  // region deleteComputedProperty

  @Test
  fun `deleteComputedProperty removes computed property from entity`() {
    val cp1 = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", computedProperties = listOf(cp1))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version
    justRun { appVersionRepository.save(any()) }

    val result = service.deleteComputedProperty("app-1", "ver-1", "e-1", "cp-1")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.computedProperties).isEmpty()
  }

  @Test
  fun `deleteComputedProperty fails when computed property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.deleteComputedProperty("app-1", "ver-1", "e-1", "unknown-cp")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.COMPUTED_PROPERTY_NOT_FOUND)
  }

  @Test
  fun `deleteComputedProperty fails when entity not found`() {
    val version = draftVersion.copy(entityDefinitions = emptyList())
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.deleteComputedProperty("app-1", "ver-1", "unknown-entity", "cp-1")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  // endregion

  // region handle(AutoUpgradeInstallation)

  @Test
  fun `handle AutoUpgradeInstallation migrates and advances the installation to the target version`() {
    val inst = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.1.0")
    every { installedAppRepository.findById(inst.id) } returns inst
    val savedSlot = slot<InstalledApp>()
    justRun { installedAppRepository.save(capture(savedSlot)) }

    val result = service.handle(DomainOutboxEvent.AutoUpgradeInstallation("inst-1", "app-1", "1.1.0", "1.2.0"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 1) { appVersionMigration.migrateInstallation(inst.id, AppId("app-1"), VersionNumber("1.1.0"), VersionNumber("1.2.0")) }
    assertThat(savedSlot.captured.installedVersionNumber).isEqualTo(VersionNumber("1.2.0"))
  }

  @Test
  fun `handle AutoUpgradeInstallation treats an already gone installation as already processed`() {
    every { installedAppRepository.findById(InstalledAppId("inst-1")) } returns null

    val result = service.handle(DomainOutboxEvent.AutoUpgradeInstallation("inst-1", "app-1", "1.1.0", "1.2.0"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appVersionMigration.migrateInstallation(any(), any(), any(), any()) }
    verify(exactly = 0) { installedAppRepository.save(any()) }
  }

  @Test
  fun `handle AutoUpgradeInstallation skips migration when the installation is no longer on the expected version`() {
    val inst = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.2.0")
    every { installedAppRepository.findById(inst.id) } returns inst

    val result = service.handle(DomainOutboxEvent.AutoUpgradeInstallation("inst-1", "app-1", "1.1.0", "1.2.0"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { appVersionMigration.migrateInstallation(any(), any(), any(), any()) }
    verify(exactly = 0) { installedAppRepository.save(any()) }
  }

  @Test
  fun `handle AutoUpgradeInstallation fails and leaves the installation unchanged when migration fails`() {
    val inst = installedApp(id = "inst-1", userId = "user-1", appId = "app-1", versionNumber = "1.1.0")
    every { installedAppRepository.findById(inst.id) } returns inst
    every {
      appVersionMigration.migrateInstallation(inst.id, AppId("app-1"), VersionNumber("1.1.0"), VersionNumber("1.2.0"))
    } returns AppVersionMigrationScriptFailedError("Order", "data-1", "1.2.0", "boom").left()

    val result = service.handle(DomainOutboxEvent.AutoUpgradeInstallation("inst-1", "app-1", "1.1.0", "1.2.0"))

    assertThat(result.isLeft()).isTrue()
    verify(exactly = 0) { installedAppRepository.save(any()) }
  }

  // endregion

  companion object {

    @JvmStatic
    fun breakingChangeCases(): Stream<Arguments> {
      val entityRemoved = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")

      val propRemovedProp = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
      val propRemovedWith = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(propRemovedProp))
      val propRemovedWithout = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = emptyList())

      val typeChangedPublishedProp = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
      val typeChangedDraftProp = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.STRING)
      val typeChangedPublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(typeChangedPublishedProp))
      val typeChangedDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(typeChangedDraftProp))

      val nonNullablePublishedProp = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = true)
      val nonNullableDraftProp = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = false)
      val nonNullablePublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(nonNullablePublishedProp))
      val nonNullableDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(nonNullableDraftProp))

      val constraintPublishedProp = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING)
      val constraintDraftProp = Property(
        id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING,
        constraints = setOf(PropertyConstraint.MaxLength(50)),
      )
      val constraintPublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(constraintPublishedProp))
      val constraintDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(constraintDraftProp))

      val unitAddedPublishedProp = Property(id = PropertyId("p-1"), name = "Distance", type = PropertyType.LONG)
      val unitAddedDraftProp = unitAddedPublishedProp.copy(unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.KILOMETERS))
      val unitAddedPublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(unitAddedPublishedProp))
      val unitAddedDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(unitAddedDraftProp))

      val unitWithUnitProp = Property(
        id = PropertyId("p-1"), name = "Distance", type = PropertyType.LONG,
        unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.KILOMETERS),
      )
      val unitRemovedPublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(unitWithUnitProp))
      val unitRemovedDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(unitWithUnitProp.copy(unit = null)))

      val unitFamilyChangedDraftProp = unitWithUnitProp.copy(unit = PropertyUnit(UnitFamily.TIME, TimeGranularity.SECONDS, TimeGranularity.MINUTES))
      val unitFamilyChangedPublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(unitWithUnitProp))
      val unitFamilyChangedDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(unitFamilyChangedDraftProp))

      val storageGranularityChangedDraftProp =
        unitWithUnitProp.copy(unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.CENTIMETERS, DistanceGranularity.KILOMETERS))
      val storageGranularityChangedPublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(unitWithUnitProp))
      val storageGranularityChangedDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(storageGranularityChangedDraftProp))

      return Stream.of(
        Arguments.of("entity definition removed", listOf(entityRemoved), emptyList<EntityDefinition>()),
        Arguments.of("property removed", listOf(propRemovedWith), listOf(propRemovedWithout)),
        Arguments.of("property type changed", listOf(typeChangedPublished), listOf(typeChangedDraft)),
        Arguments.of("property made non-nullable", listOf(nonNullablePublished), listOf(nonNullableDraft)),
        Arguments.of("type-specific constraint added to existing property", listOf(constraintPublished), listOf(constraintDraft)),
        Arguments.of("unit added to existing property", listOf(unitAddedPublished), listOf(unitAddedDraft)),
        Arguments.of("unit removed from existing property", listOf(unitRemovedPublished), listOf(unitRemovedDraft)),
        Arguments.of("unit family changed", listOf(unitFamilyChangedPublished), listOf(unitFamilyChangedDraft)),
        Arguments.of("unit storage granularity changed", listOf(storageGranularityChangedPublished), listOf(storageGranularityChangedDraft)),
      )
    }

    @JvmStatic
    fun versionDiffCases(): Stream<Arguments> {
      val defaultProp = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING, default = null)
      val defaultEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(defaultProp))
      val defaultUpdatedEntity = defaultEntity.copy(properties = listOf(defaultProp.copy(default = "Alpha")))

      val valueProposalsProp = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING, valueProposals = emptyList())
      val valueProposalsFilterProp = Property(id = PropertyId("p-2"), name = "Group", type = PropertyType.STRING)
      val valueProposalsEntity =
        EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(valueProposalsProp, valueProposalsFilterProp))
      val valueProposalsUpdatedEntity = valueProposalsEntity.copy(
        properties = listOf(valueProposalsProp.copy(valueProposals = listOf("p-2")), valueProposalsFilterProp),
      )

      val computedAddedEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
      val addedCp = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG, script = "42L")
      val computedAddedUpdatedEntity = computedAddedEntity.copy(computedProperties = listOf(addedCp))

      val removedCp = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG, script = "42L")
      val computedRemovedEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", computedProperties = listOf(removedCp))
      val computedRemovedUpdatedEntity = computedRemovedEntity.copy(computedProperties = emptyList())

      val scriptCp = ComputedProperty(id = ComputedPropertyId("cp-1"), name = "Total", type = PropertyType.LONG, script = "0L")
      val scriptEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", computedProperties = listOf(scriptCp))
      val scriptUpdatedEntity = scriptEntity.copy(computedProperties = listOf(scriptCp.copy(script = "42L")))

      val listItemProp = Property(id = PropertyId("p-1"), name = "Tags", type = PropertyType.LIST, listItemType = PropertyType.STRING)
      val listItemEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(listItemProp))
      val listItemUpdatedEntity = listItemEntity.copy(
        properties = listOf(listItemProp.copy(itemConstraints = setOf(PropertyConstraint.MaxLength(10)))),
      )

      val targetEntityRef = EntityDefinition(id = EntityDefinitionId("e-2"), name = "Customer")
      val targetProp = Property(id = PropertyId("p-1"), name = "Owner", type = PropertyType.REF)
      val targetEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(targetProp))
      val targetUpdatedEntity = targetEntity.copy(properties = listOf(targetProp.copy(targetEntityId = EntityDefinitionId("e-2"))))

      val nestedProp = Property(id = PropertyId("np-1"), name = "Street", type = PropertyType.STRING)
      val nestedParentProp = Property(id = PropertyId("p-1"), name = "Address", type = PropertyType.OBJECT, nestedProperties = listOf(nestedProp))
      val nestedEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(nestedParentProp))
      val nestedUpdatedEntity = nestedEntity.copy(
        properties = listOf(nestedParentProp.copy(nestedProperties = listOf(nestedProp.copy(nullable = false)))),
      )

      val unitProp = Property(id = PropertyId("p-1"), name = "Distance", type = PropertyType.LONG)
      val unitEntity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Trip", properties = listOf(unitProp))
      val unitUpdatedEntity = unitEntity.copy(
        properties = listOf(unitProp.copy(unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.KILOMETERS))),
      )

      return Stream.of(
        Arguments.of("default value", listOf(defaultEntity), listOf(defaultUpdatedEntity), listOf(listOf("default:"))),
        Arguments.of("value-proposals", listOf(valueProposalsEntity), listOf(valueProposalsUpdatedEntity), listOf(listOf("value-proposals:"))),
        Arguments.of(
          "added computed property", listOf(computedAddedEntity), listOf(computedAddedUpdatedEntity),
          listOf(listOf("computed Total: LONG"), listOf("script: 42L")),
        ),
        Arguments.of(
          "removed computed property", listOf(computedRemovedEntity), listOf(computedRemovedUpdatedEntity),
          listOf(listOf("computed Total: LONG")),
        ),
        Arguments.of(
          "changes to computed property script", listOf(scriptEntity), listOf(scriptUpdatedEntity),
          listOf(listOf("script: 42L"), listOf("script: 0L")),
        ),
        Arguments.of(
          "list item type and item constraints", listOf(listItemEntity), listOf(listItemUpdatedEntity),
          listOf(listOf("item-type: STRING", "max-length:10")),
        ),
        Arguments.of(
          "target entity name", listOf(targetEntity, targetEntityRef), listOf(targetUpdatedEntity, targetEntityRef),
          listOf(listOf("target-entity: Customer")),
        ),
        Arguments.of(
          "nested properties", listOf(nestedEntity), listOf(nestedUpdatedEntity),
          listOf(listOf("Street: STRING!")),
        ),
        Arguments.of(
          "property unit", listOf(unitEntity), listOf(unitUpdatedEntity),
          listOf(listOf("unit: DISTANCE", "granularity: METERS", "default: KILOMETERS")),
        ),
      )
    }
  }
}
