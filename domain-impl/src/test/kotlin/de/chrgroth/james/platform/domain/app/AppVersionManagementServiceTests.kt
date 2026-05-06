package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.app
import de.chrgroth.james.platform.domain.app.AppManagementServiceTests.Companion.version
import de.chrgroth.james.platform.domain.error.AppVersionError
import de.chrgroth.james.platform.domain.error.DisplayTextInvalidError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.AppVersionId
import de.chrgroth.james.platform.domain.model.app.AppVersionStatus
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.ReportId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import de.chrgroth.james.platform.domain.port.out.app.AppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.AppVersionRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AppVersionManagementServiceTests {

  private val appRepository: AppRepositoryPort = mockk()
  private val appVersionRepository: AppVersionRepositoryPort = mockk()
  private val propertyConstraintPort: PropertyConstraintPort = mockk()
  private val service: AppVersionManagementService = AppVersionManagementService(appRepository, appVersionRepository, propertyConstraintPort)

  private val existingApp = app(id = "app-1", name = "My App")
  private val draftVersion = version(id = "ver-1", appId = "app-1", versionNumber = null, status = AppVersionStatus.DRAFT)
  private val publishedVersion = version(id = "ver-2", appId = "app-1", versionNumber = "1.1.0", status = AppVersionStatus.PUBLISHED)
  private val draftVersionWithNewEntity = draftVersion.copy(entityDefinitions = listOf(EntityDefinition(id = EntityDefinitionId("e-new"), name = "NewEntity")))
  private val releaseNotes = "Initial release notes."

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

    val result = service.publishVersion("app-1", "FEATURE", releaseNotes)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.status).isEqualTo(AppVersionStatus.PUBLISHED)
    assertThat(result.getOrNull()?.versionNumber).isEqualTo(VersionNumber("1.2.0"))
  }

  @Test
  fun `publishVersion succeeds as bugfix bump`() {
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(draftVersionWithNewEntity, publishedVersion)
    justRun { appVersionRepository.save(any()) }

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
  fun `computeVersionBump detects breaking change when entity definition removed`() {
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityDef))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = emptyList())
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isTrue()
  }

  @Test
  fun `computeVersionBump detects breaking change when property removed`() {
    val prop = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
    val entityWithProp = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop))
    val entityWithoutProp = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = emptyList())
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityWithProp))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityWithoutProp))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isTrue()
  }

  @Test
  fun `computeVersionBump detects breaking change when property type changed`() {
    val propPublished = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.LONG)
    val propDraft = Property(id = PropertyId("p-1"), name = "Amount", type = PropertyType.STRING)
    val entityPublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(propPublished))
    val entityDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(propDraft))
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityPublished))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityDraft))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isTrue()
  }

  @Test
  fun `computeVersionBump detects breaking change when property made non-nullable`() {
    val propPublished = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = true)
    val propDraft = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, nullable = false)
    val entityPublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(propPublished))
    val entityDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(propDraft))
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityPublished))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityDraft))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isTrue()
  }

  @Test
  fun `computeVersionBump detects breaking change when type-specific constraint added to existing property`() {
    val propPublished = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING)
    val propDraft = Property(id = PropertyId("p-1"), name = "Tag", type = PropertyType.STRING, constraints = setOf(PropertyConstraint.MaxLength(50)))
    val entityPublished = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(propPublished))
    val entityDraft = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(propDraft))
    val pub = publishedVersion.copy(entityDefinitions = listOf(entityPublished))
    val draft = version(id = "ver-draft", appId = "app-1", versionNumber = "2.0.0", status = AppVersionStatus.DRAFT)
      .copy(entityDefinitions = listOf(entityDraft))
    every { appRepository.findById(AppId("app-1")) } returns existingApp
    every { appVersionRepository.findById(AppVersionId("ver-draft")) } returns draft
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(pub, draft)

    val result = service.computeVersionBump("app-1", "ver-draft")

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()!!.hasBreakingChanges).isTrue()
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

    val result = service.addProperty("app-1", "ver-1", "e-1", "Amount", "LONG", true)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.properties).hasSize(1)
    assertThat(result.getOrNull()?.entityDefinitions?.first()?.properties?.first()?.name).isEqualTo("Amount")
  }

  @Test
  fun `addProperty fails when property type is invalid`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.addProperty("app-1", "ver-1", "e-1", "Amount", "INVALID_TYPE", true)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.INVALID_PROPERTY_TYPE)
  }

  @Test
  fun `addProperty fails when entity not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.addProperty("app-1", "ver-1", "unknown-entity", "Amount", "LONG", true)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
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

  @Test
  fun `updateProperty fails when property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.updateProperty("app-1", "ver-1", "e-1", "unknown-prop", "Amount", "LONG", true)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_NOT_FOUND)
  }

  @Test
  fun `updateProperty fails when entity not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.updateProperty("app-1", "ver-1", "unknown-entity", "p-1", "Amount", "LONG", true)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `updateProperty fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.updateProperty("app-1", "unknown", "e-1", "p-1", "Amount", "LONG", true)

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
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

  @Test
  fun `setPropertyConstraints fails when property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyConstraints("app-1", "ver-1", "e-1", "unknown-prop", emptySet())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_NOT_FOUND)
  }

  @Test
  fun `setPropertyConstraints fails when entity not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.setPropertyConstraints("app-1", "ver-1", "unknown-entity", "p-1", emptySet())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `setPropertyConstraints fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.setPropertyConstraints("app-1", "unknown", "e-1", "p-1", emptySet())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
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

  @Test
  fun `setPropertyDefault fails for LIST type`() {
    val property = Property(id = PropertyId("p-1"), name = "Items", type = PropertyType.LIST)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", "somevalue")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.DEFAULT_NOT_SUPPORTED)
  }

  @Test
  fun `setPropertyDefault fails for OBJECT type`() {
    val property = Property(id = PropertyId("p-1"), name = "Meta", type = PropertyType.OBJECT)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", "somevalue")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.DEFAULT_NOT_SUPPORTED)
  }

  @Test
  fun `setPropertyDefault fails for REF type`() {
    val property = Property(id = PropertyId("p-1"), name = "RefProp", type = PropertyType.REF)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "p-1", "somevalue")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.DEFAULT_NOT_SUPPORTED)
  }

  @Test
  fun `setPropertyDefault fails when property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyDefault("app-1", "ver-1", "e-1", "unknown-prop", "42")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_NOT_FOUND)
  }

  @Test
  fun `setPropertyDefault fails when entity not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.setPropertyDefault("app-1", "ver-1", "unknown-entity", "p-1", "42")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `setPropertyDefault fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.setPropertyDefault("app-1", "unknown", "e-1", "p-1", "42")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
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

  @Test
  fun `setPropertySmartDefault fails for LIST type when setting a script`() {
    val property = Property(id = PropertyId("p-1"), name = "Items", type = PropertyType.LIST)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertySmartDefault("app-1", "ver-1", "e-1", "p-1", "now.toString()")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.SMART_DEFAULT_NOT_SUPPORTED)
  }

  @Test
  fun `setPropertySmartDefault fails for OBJECT type when setting a script`() {
    val property = Property(id = PropertyId("p-1"), name = "Meta", type = PropertyType.OBJECT)
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(property))
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertySmartDefault("app-1", "ver-1", "e-1", "p-1", "now.toString()")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.SMART_DEFAULT_NOT_SUPPORTED)
  }

  @Test
  fun `setPropertySmartDefault fails for REF type when setting a script`() {
    val property = Property(id = PropertyId("p-1"), name = "RefProp", type = PropertyType.REF)
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

  @Test
  fun `setPropertySmartDefault fails when property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertySmartDefault("app-1", "ver-1", "e-1", "unknown-prop", "now.toString()")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_NOT_FOUND)
  }

  @Test
  fun `setPropertySmartDefault fails when entity not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns draftVersion

    val result = service.setPropertySmartDefault("app-1", "ver-1", "unknown-entity", "p-1", "now.toString()")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `setPropertySmartDefault fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("unknown")) } returns null

    val result = service.setPropertySmartDefault("app-1", "unknown", "e-1", "p-1", "now.toString()")

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

  @Test
  fun `getVersionDiff shows default value in diff lines when changed`() {
    val prop = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING, default = null)
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop))
    val predecessor = version(id = "ver-old", appId = "app-1", versionNumber = "1.0.0", status = AppVersionStatus.PUBLISHED)
      .copy(entityDefinitions = listOf(entityDef), createdAt = publishedVersion.createdAt.minusSeconds(100))
    val updatedProp = prop.copy(default = "Alpha")
    val currentVersion = version(id = "ver-new", appId = "app-1", versionNumber = "1.1.0", status = AppVersionStatus.PUBLISHED)
      .copy(entityDefinitions = listOf(entityDef.copy(properties = listOf(updatedProp))))
    every { appVersionRepository.findById(AppVersionId("ver-new")) } returns currentVersion
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(predecessor, currentVersion)

    val result = service.getVersionDiff("app-1", "ver-new")

    assertThat(result.isRight()).isTrue()
    val diff = result.getOrNull()!!
    assertThat(diff.entityDiffs).isNotEmpty()
    val entityDiff = diff.entityDiffs.first()
    assertThat(entityDiff.lines.map { it.text }).anyMatch { it.contains("default:") }
  }

  @Test
  fun `getVersionDiff shows value-proposals in diff lines when changed`() {
    val prop = Property(id = PropertyId("p-1"), name = "Category", type = PropertyType.STRING, valueProposals = emptyList())
    val filterProp = Property(id = PropertyId("p-2"), name = "Group", type = PropertyType.STRING)
    val entityDef = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Order", properties = listOf(prop, filterProp))
    val predecessor = version(id = "ver-old", appId = "app-1", versionNumber = "1.0.0", status = AppVersionStatus.PUBLISHED)
      .copy(entityDefinitions = listOf(entityDef), createdAt = publishedVersion.createdAt.minusSeconds(100))
    val updatedProp = prop.copy(valueProposals = listOf("p-2"))
    val currentVersion = version(id = "ver-new", appId = "app-1", versionNumber = "1.1.0", status = AppVersionStatus.PUBLISHED)
      .copy(entityDefinitions = listOf(entityDef.copy(properties = listOf(updatedProp, filterProp))))
    every { appVersionRepository.findById(AppVersionId("ver-new")) } returns currentVersion
    every { appVersionRepository.findAllByAppId(AppId("app-1")) } returns listOf(predecessor, currentVersion)

    val result = service.getVersionDiff("app-1", "ver-new")

    assertThat(result.isRight()).isTrue()
    val diff = result.getOrNull()!!
    assertThat(diff.entityDiffs).isNotEmpty()
    val entityDiff = diff.entityDiffs.first()
    assertThat(entityDiff.lines.map { it.text }).anyMatch { it.contains("value-proposals:") }
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

  @Test
  fun `setPropertyValueProposals fails when version not found`() {
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns null

    val result = service.setPropertyValueProposals("app-1", "ver-1", "e-1", "p-1", emptyList())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.VERSION_NOT_FOUND)
  }

  @Test
  fun `setPropertyValueProposals fails when entity not found`() {
    val version = draftVersion.copy(entityDefinitions = emptyList())
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyValueProposals("app-1", "ver-1", "unknown-entity", "p-1", emptyList())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.ENTITY_NOT_FOUND)
  }

  @Test
  fun `setPropertyValueProposals fails when property not found`() {
    val entity = EntityDefinition(id = EntityDefinitionId("e-1"), name = "Item")
    val version = draftVersion.copy(entityDefinitions = listOf(entity))
    every { appVersionRepository.findById(AppVersionId("ver-1")) } returns version

    val result = service.setPropertyValueProposals("app-1", "ver-1", "e-1", "unknown-prop", emptyList())

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(AppVersionError.PROPERTY_NOT_FOUND)
  }

  // endregion
}
