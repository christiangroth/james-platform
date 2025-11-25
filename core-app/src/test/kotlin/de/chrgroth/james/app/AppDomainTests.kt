package de.chrgroth.james.app

import arrow.core.Validated
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainError
import de.chrgroth.james.DomainEvent
import de.chrgroth.james.EventBus
import de.chrgroth.james.expectDomainErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.typesystem.DataobjectFieldSpecFormat
import de.chrgroth.james.typesystem.DataobjectFieldSpecFormat.YAML
import de.chrgroth.james.typesystem.Datatype
import io.mockk.MockKVerificationScope
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.UUID

class AppLifecycleUseCasesTests {

  private val developerId = UUID.randomUUID()

  private val developmentApp = App.create(name = "Development App", developerId = developerId, description = " ").expectSuccess()
  private val developmentAppId = developmentApp.id

  private val activeApp = App.create(name = "Active App", developerId = developerId, description = " ").expectSuccess()
    .addNextVersionDraftDatatype("TestDatatype").expectSuccess()
    .addNextVersionDraftReport("TestReport").expectSuccess()
    .changeNextVersionReleaseNoteTitle("First Feature!").expectSuccess()
    .changeNextVersionReleaseNoteFeatures(listOf("Something really nasty implemented here")).expectSuccess()
    .releaseNextVersionDraft().expectSuccess()
  private val activeAppId = activeApp.id

  private val activeAppMultipleVersions = App.create(name = "Active App Multiple Versions", developerId = developerId, description = " ").expectSuccess()
    .addNextVersionDraftDatatype("TestDatatype").expectSuccess()
    .addNextVersionDraftReport("TestReport").expectSuccess()
    .changeNextVersionReleaseNoteTitle("First Feature!").expectSuccess()
    .changeNextVersionReleaseNoteFeatures(listOf("Something really nasty implemented here")).expectSuccess()
    .releaseNextVersionDraft().expectSuccess()
    .addNextVersionDraftDatatype("TestDatatypeTwo").expectSuccess()
    .changeNextVersionReleaseNoteTitle("Nothing changed?!?").expectSuccess()
    .changeNextVersionReleaseNoteBugfixes(listOf("Just fixed some bugs")).expectSuccess()
    .releaseNextVersionDraft().expectSuccess()
  private val activeAppMultipleVersionsId = activeAppMultipleVersions.id

  private val discontinuedApp = App.create(name = "Discontinued App", developerId = developerId, description = "").expectSuccess()
    .discontinue().expectSuccess()
  private val discontinuedAppId = discontinuedApp.id

  private lateinit var queryPersistence: AppQueryPersistencePort
  private lateinit var commandPersistence: AppCommandPersistencePort
  private lateinit var activeUsersCache: ActiveUsersCache
  private lateinit var appLifecycleUseCases: AppLifecycleUseCases

  @BeforeEach
  internal fun initialize() {
    queryPersistence = mockk<AppQueryPersistencePort>().also {
      every { it.getOrError(developmentAppId) } returns (Validated.validNel(developmentApp))
      every { it.getOrError(activeAppId) } returns (Validated.validNel(activeApp))
      every { it.getOrError(activeAppMultipleVersionsId) } returns (Validated.validNel(activeAppMultipleVersions))
      every { it.getOrError(discontinuedAppId) } returns (Validated.validNel(discontinuedApp))
    }

    commandPersistence = mockk<AppCommandPersistencePort>().also {
      every { it.upsert(any()) } answers { Validated.validNel(this.args[0] as App) }
      every { it.delete(any()) } answers { Validated.validNel(Unit) }
    }

    activeUsersCache = mockk<ActiveUsersCache>().also {
      every { it.contains(any()) } answers { false }
      every { it.contains(developerId) } answers { true }
    }

    appLifecycleUseCases = AppLifecycleUseCasesService(queryPersistence, commandPersistence, activeUsersCache)
  }

  @Test
  fun `create app`() {
    fun App.assertions() {
      assertThat(name).isEqualTo("Test App")
      assertThat(developerId).isEqualTo(developerId)
      assertThat(description).isEqualTo("Fancy App")
      assertThat(releasedVersions).isEmpty()
      assertThat(latestVersion).isNull()
      assertThat(nextVersionDraft).isNotNull
      assertThat(nextVersionDraft.datatypes).isEmpty()
      assertThat(nextVersionDraft.reports).isEmpty()
      assertThat(status).isEqualTo(AppStatus.DEVELOPMENT)
      assertThat(discontinued).isFalse
    }

    appLifecycleUseCases.create("Test App", developerId, "Fancy App").expectSuccess().assertions()
    verifyMocks {
      activeUsersCache.contains(withArg {
        assertThat(it).isEqualTo(developerId)
      })
      commandPersistence.upsert(withArg {
        it.assertions()
      })
    }
  }

  @Test
  fun `create app with blank name missing`() {
    appLifecycleUseCases.create(" ", developerId, "Fancy App").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.NAME_BLANK)
    )
    verifyMocks {
      activeUsersCache.contains(withArg {
        assertThat(it).isEqualTo(developerId)
      })
    }
  }

  @Test
  fun `create app with unknown developer`() {
    val unknownDeveloperId = UUID.randomUUID()
    appLifecycleUseCases.create("Fancy App", unknownDeveloperId, "Fancy App").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.APP_DEVELOPER_UNKNOWN)
    )
    verifyMocks {
      activeUsersCache.contains(withArg {
        assertThat(it).isEqualTo(unknownDeveloperId)
      })
    }
  }

  @Test
  fun `change release note title to empty`() {
    appLifecycleUseCases.changeReleaseNoteTitle(activeAppMultipleVersionsId, Semver("0.1.0"), "").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.VERSION_RELEASE_TITLE_BLANK)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppMultipleVersionsId)
    }
  }

  @Test
  fun `change release note title to blank`() {
    appLifecycleUseCases.changeReleaseNoteTitle(activeAppMultipleVersionsId, Semver("0.1.0"), " ").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.VERSION_RELEASE_TITLE_BLANK)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppMultipleVersionsId)
    }
  }

  @Test
  fun `change release note title`() {
    appLifecycleUseCases.changeReleaseNoteTitle(activeAppMultipleVersionsId, Semver("0.1.0"), "New title!").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppMultipleVersionsId)
      commandPersistence.upsert(withArg {
        assertThat(it.releasedVersions.first { it.version == Semver("0.1.0") }.releaseNotes.title).isEqualTo("New title!")
      })
    }
  }

  @Test
  fun `change release note notes`() {
    appLifecycleUseCases.changeReleaseNoteNotes(activeAppMultipleVersionsId, Semver("0.1.0"), "New notes").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppMultipleVersionsId)
      commandPersistence.upsert(withArg {
        assertThat(it.releasedVersions.first { it.version == Semver("0.1.0") }.releaseNotes.notes).isEqualTo("New notes")
      })
    }
  }

  @Test
  fun `change release note features`() {
    appLifecycleUseCases.changeReleaseNoteFeatures(activeAppMultipleVersionsId, Semver("0.1.0"), listOf("F1", "F2")).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppMultipleVersionsId)
      commandPersistence.upsert(withArg {
        assertThat(it.releasedVersions.first { it.version == Semver("0.1.0") }.releaseNotes.features).isEqualTo(listOf("F1", "F2"))
      })
    }
  }

  @Test
  fun `change release note bugfixes`() {
    appLifecycleUseCases.changeReleaseNoteBugfixes(activeAppMultipleVersionsId, Semver("0.1.0"), listOf("B1", "B2")).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppMultipleVersionsId)
      commandPersistence.upsert(withArg {
        assertThat(it.releasedVersions.first { it.version == Semver("0.1.0") }.releaseNotes.bugfixes).isEqualTo(listOf("B1", "B2"))
      })
    }
  }

  @Test
  fun `change release note misc`() {
    appLifecycleUseCases.changeReleaseNoteMisc(activeAppMultipleVersionsId, Semver("0.1.0"), listOf("M1", "M2")).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppMultipleVersionsId)
      commandPersistence.upsert(withArg {
        assertThat(it.releasedVersions.first { it.version == Semver("0.1.0") }.releaseNotes.misc).isEqualTo(listOf("M1", "M2"))
      })
    }
  }

  @Test
  fun `change release note data on unknown version`() {
    appLifecycleUseCases.changeReleaseNoteTitle(activeAppMultipleVersionsId, Semver("6.6.6"), "New title!").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.RELEASE_VERSION_NOT_FOUND)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppMultipleVersionsId)
    }
  }

  @Test
  fun `change release note data on discontinued app`() {
    appLifecycleUseCases.changeReleaseNoteTitle(discontinuedAppId, Semver("0.1.0"), "New title!").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  @Test
  fun `discontinue app`() {
    appLifecycleUseCases.discontinue(activeAppId).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.discontinued).isTrue()
      })
    }
  }

  @Test
  fun `discontinue app already discontinued`() {
    appLifecycleUseCases.discontinue(discontinuedAppId).expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  @Test
  fun `delete app still active`() {
    appLifecycleUseCases.delete(activeAppId).expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `delete app`() {
    appLifecycleUseCases.delete(discontinuedAppId).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
      commandPersistence.delete(discontinuedAppId)
    }
  }

  private fun verifyMocks(verifyBlock: (MockKVerificationScope.() -> Unit)? = null) {
    if (verifyBlock != null) {
      verifySequence(inverse = false, verifyBlock = verifyBlock)
    }
    confirmVerified(queryPersistence)
    confirmVerified(commandPersistence)
    confirmVerified(activeUsersCache)
  }
}

@Suppress("LargeClass")
class AppVersionDevelopmentUseCasesTests {

  private val developerId = UUID.randomUUID()

  private val developmentApp = App.create(name = "Development App", developerId = developerId, description = " ").expectSuccess()
    .addNextVersionDraftDatatype("SomeChanges").expectSuccess()
    .changeNextVersionReleaseNoteTitle("First Release!").expectSuccess()
    .changeNextVersionReleaseNoteFeatures(listOf("great new feature")).expectSuccess()
  private val developmentAppId = developmentApp.id

  private val developmentAppNoReleaseNotesTitle = App.create(name = "Development App", developerId = developerId, description = " ").expectSuccess()
    .addNextVersionDraftDatatype("SomeChanges").expectSuccess()
    .changeNextVersionReleaseNoteFeatures(listOf("great new feature")).expectSuccess()
  private val developmentAppNoReleaseNotesTitleId = developmentAppNoReleaseNotesTitle.id

  private val developmentAppNoReleaseNotesFeaturesOrBugfixes = App.create(name = "Development App", developerId = developerId, description = " ").expectSuccess()
    .addNextVersionDraftDatatype("SomeChanges").expectSuccess()
    .changeNextVersionReleaseNoteTitle("First Release!").expectSuccess()
  private val developmentAppNoReleaseNotesFeaturesOrBugfixesId = developmentAppNoReleaseNotesFeaturesOrBugfixes.id

  private val developmentAppNoChanges = App.create(name = "Development App unchanged", developerId = developerId, description = " ").expectSuccess()
  private val developmentAppNoChangesId = developmentAppNoChanges.id

  private val activeApp = App.create(name = "Active App", developerId = developerId, description = " ").expectSuccess()
    .addNextVersionDraftDatatype("TestDatatype").expectSuccess()
    .addNextVersionDraftReport("TestReport").expectSuccess()
    .changeNextVersionReleaseNoteTitle("First Feature!").expectSuccess()
    .changeNextVersionReleaseNoteFeatures(listOf("Something really nasty implemented here")).expectSuccess()
    .releaseNextVersionDraft().expectSuccess()
  private val activeAppId = activeApp.id

  private val activeAppMultipleVersions = App.create(name = "Active App Multiple Versions", developerId = developerId, description = " ").expectSuccess()
    .addNextVersionDraftDatatype("TestDatatype").expectSuccess()
    .addNextVersionDraftReport("TestReport").expectSuccess()
    .changeNextVersionReleaseNoteTitle("First Feature!").expectSuccess()
    .changeNextVersionReleaseNoteFeatures(listOf("Something really nasty implemented here")).expectSuccess()
    .releaseNextVersionDraft().expectSuccess()
    .addNextVersionDraftDatatype("TestDatatypeTwo").expectSuccess()
    .changeNextVersionReleaseNoteTitle("Nothing changed?!?").expectSuccess()
    .changeNextVersionReleaseNoteBugfixes(listOf("Just fixed some bugs")).expectSuccess()
    .releaseNextVersionDraft().expectSuccess()
  private val activeAppMultipleVersionsId = activeAppMultipleVersions.id

  private val discontinuedApp = App.create(name = "Discontinued App", developerId = developerId, description = "").expectSuccess()
    .discontinue().expectSuccess()
  private val discontinuedAppId = discontinuedApp.id

  private lateinit var queryPersistence: AppQueryPersistencePort
  private lateinit var commandPersistence: AppCommandPersistencePort
  private lateinit var eventBus: EventBus
  private lateinit var appVersionDevelopmentUseCases: AppVersionDevelopmentUseCases

  @BeforeEach
  internal fun initialize() {
    queryPersistence = mockk<AppQueryPersistencePort>().also {
      every { it.getOrError(developmentAppId) } returns (Validated.validNel(developmentApp))
      every { it.getOrError(developmentAppNoReleaseNotesTitleId) } returns (Validated.validNel(developmentAppNoReleaseNotesTitle))
      every { it.getOrError(developmentAppNoReleaseNotesFeaturesOrBugfixesId) } returns (Validated.validNel(developmentAppNoReleaseNotesFeaturesOrBugfixes))
      every { it.getOrError(developmentAppNoChangesId) } returns (Validated.validNel(developmentAppNoChanges))
      every { it.getOrError(activeAppId) } returns (Validated.validNel(activeApp))
      every { it.getOrError(activeAppMultipleVersionsId) } returns (Validated.validNel(activeAppMultipleVersions))
      every { it.getOrError(discontinuedAppId) } returns (Validated.validNel(discontinuedApp))
    }

    commandPersistence = mockk<AppCommandPersistencePort>().also {
      every { it.upsert(any()) } answers { Validated.validNel(this.args[0] as App) }
      every { it.delete(any()) } answers { Validated.validNel(Unit) }
    }

    eventBus = mockk<EventBus>().also {
      every { it.publish(any()) } answers { Unit }
    }

    appVersionDevelopmentUseCases = AppVersionDevelopmentUseCasesService(queryPersistence, commandPersistence, eventBus)
  }

  @Test
  fun `change release note empty title`() {
    appVersionDevelopmentUseCases.changeReleaseNoteTitle(activeAppId, "").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.releaseNotes.title).isEqualTo("")
      })
    }
  }

  @Test
  fun `change release note title`() {
    appVersionDevelopmentUseCases.changeReleaseNoteTitle(activeAppId, "Release!").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.releaseNotes.title).isEqualTo("Release!")
      })
    }
  }

  @Test
  fun `change release note notes`() {
    appVersionDevelopmentUseCases.changeReleaseNoteNotes(activeAppId, "Some nice notes...").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.releaseNotes.notes).isEqualTo("Some nice notes...")
      })
    }
  }

  @Test
  fun `change release note features`() {
    appVersionDevelopmentUseCases.changeReleaseNoteFeatures(activeAppId, listOf("F1", "F2")).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.releaseNotes.features).isEqualTo(listOf("F1", "F2"))
      })
    }
  }

  @Test
  fun `change release note bugfixes`() {
    appVersionDevelopmentUseCases.changeReleaseNoteBugfixes(activeAppId, listOf("B1", "B2")).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.releaseNotes.bugfixes).isEqualTo(listOf("B1", "B2"))
      })
    }
  }

  @Test
  fun `change release note misc`() {
    appVersionDevelopmentUseCases.changeReleaseNoteMisc(activeAppId, listOf("M1", "M2")).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.releaseNotes.misc).isEqualTo(listOf("M1", "M2"))
      })
    }
  }

  @Test
  @Disabled
  fun `add datatype with blank name`() {
    appVersionDevelopmentUseCases.addDatatype(activeAppId, " ").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DATATYPE_NAME_BLANK)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  @Disabled
  fun `add datatype with invalid name`() {
    appVersionDevelopmentUseCases.addDatatype(activeAppId, "some Datatype").expectDomainErrors(
      DomainError(
        code = AppDomainErrorCodes.DATATYPE_NAME_INVALID,
        errorMessage = "'some Datatype' does not match ([A-Z][a-z]*)+",
      )
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `add datatype with duplicate name`() {
    appVersionDevelopmentUseCases.addDatatype(activeAppId, "TestDatatype").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DATATYPE_NAME_DUPLICATE)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `add datatype`() {
    appVersionDevelopmentUseCases.addDatatype(activeAppId, "NewDatatype").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.datatypes).contains(Datatype.create("NewDatatype", "NewDatatype"))
      })
    }
  }

  @Test
  fun `add datatype on discontinued app`() {
    appVersionDevelopmentUseCases.addDatatype(discontinuedAppId, " ").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  @Test
  @Disabled
  fun `rename next version datatype to blank name`() {
    appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", " ", "TestDatatype", "", YAML, "").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DATATYPE_NAME_BLANK)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  @Disabled
  fun `rename next version datatype with invalid name`() {
    appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", "Test Datatype", "TestDatatype", "", YAML, "").expectDomainErrors(
      DomainError(
        code = AppDomainErrorCodes.DATATYPE_NAME_INVALID,
        errorMessage = "'Test Datatype' does not match ([A-Z][a-z]*)+",
      )
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `rename next version datatype with unknown name`() {
    appVersionDevelopmentUseCases.changeDatatype(activeAppId, "Unknown", "Unknown", "Unknown", "", YAML, "").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DATATYPE_NOT_FOUND)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `rename next version datatype with duplicate name`() {
    appVersionDevelopmentUseCases.changeDatatype(activeAppId, "Unknown", "TestDatatype", "Unknown", "", YAML, "").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DATATYPE_NAME_DUPLICATE)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `rename next version datatype`() {
    val nextVersion = appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", "NewDatatype", "Unknown", "", YAML, "NewDatatype").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.datatypes).hasSize(1)
        assertThat(it.nextVersionDraft.datatypes.first().name).isEqualTo("NewDatatype")
      })
    }

    // renaming a datatype should be a breaking change, check that
    assertThat(isBreaking(activeApp.latestVersion!!, nextVersion)).isTrue
  }

  @Test
  fun `rename next version datatype on discontinued app`() {
    appVersionDevelopmentUseCases.changeDatatype(discontinuedAppId, "TestDatatype", "TestDatatype", "TestDatatype", "", YAML, "").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  @Test
  fun `update next version datatype`() {
    appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", "NewDatatype", "DisplayName", "", YAML, "New description").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.datatypes).hasSize(1)
        assertThat(it.nextVersionDraft.datatypes.first().name).isEqualTo("NewDatatype")
        assertThat(it.nextVersionDraft.datatypes.first().displayName).isEqualTo("DisplayName")
        assertThat(it.nextVersionDraft.datatypes.first().dataobjectDefinition.description).isEqualTo("New description")
      })
    }
  }

  @Test
  fun `remove next version unknown datatype`() {
    appVersionDevelopmentUseCases.removeDatatype(activeAppId, "Unknown").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DATATYPE_NOT_FOUND)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `remove next version datatype`() {
    val nextVersion = appVersionDevelopmentUseCases.removeDatatype(activeAppId, "TestDatatype").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.datatypes).isEmpty()
      })
    }

    // removing a datatype should be a breaking change, check that
    assertThat(isBreaking(activeApp.latestVersion!!, nextVersion)).isTrue
  }

  @Test
  fun `remove next version datatype on discontinued app`() {
    appVersionDevelopmentUseCases.removeDatatype(discontinuedAppId, "TestDatatype").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  @Test
  fun `add report with blank name`() {
    appVersionDevelopmentUseCases.addReport(activeAppId, " ").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.REPORT_NAME_BLANK)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `add report with duplicate name`() {
    appVersionDevelopmentUseCases.addReport(activeAppId, "TestReport").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.REPORT_NAME_DUPLICATE)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `add report`() {
    appVersionDevelopmentUseCases.addReport(activeAppId, "NewReport").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.reports).contains(AppReport.create("NewReport", null, "").expectSuccess())
      })
    }
  }

  @Test
  fun `add report on discontinued app`() {
    appVersionDevelopmentUseCases.addReport(discontinuedAppId, " ").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  @Test
  fun `rename next version report with blank name`() {
    appVersionDevelopmentUseCases.changeReport(activeAppId, "TestReport", "", null, " ").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.REPORT_NAME_BLANK)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `rename next version report with unknown name`() {
    appVersionDevelopmentUseCases.changeReport(activeAppId, "Unknown", "", null, "Unknown Report").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.REPORT_NOT_FOUND)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `rename next version report with duplicate name`() {
    appVersionDevelopmentUseCases.changeReport(activeAppId, "Unknown", "", null, "TestReport").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.REPORT_NAME_DUPLICATE)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `rename next version report`() {
    appVersionDevelopmentUseCases.changeReport(activeAppId, "TestReport", "", null, "NewReport").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.reports).hasSize(1)
        assertThat(it.nextVersionDraft.reports.first().name).isEqualTo("NewReport")
      })
    }
  }

  @Test
  fun `rename next version report on discontinued app`() {
    appVersionDevelopmentUseCases.changeReport(discontinuedAppId, "TestReport", "", null, "SomeOtherName").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  @Test
  fun `update next version report with unknown name`() {
    appVersionDevelopmentUseCases.changeReport(activeAppId, "Unknown", "", null, null).expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.REPORT_NOT_FOUND)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `update next version report`() {
    appVersionDevelopmentUseCases.changeReport(activeAppId, "TestReport", "NEW SRC", "NEW DESC", null).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.reports).hasSize(1)
        assertThat(it.nextVersionDraft.reports.first().source).isEqualTo("NEW SRC")
        assertThat(it.nextVersionDraft.reports.first().description).isEqualTo("NEW DESC")
      })
    }
  }

  @Test
  fun `update next version report with same source and description`() {
    appVersionDevelopmentUseCases.changeReport(activeAppId, "TestReport", "", null, null).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.reports).hasSize(1)
        assertThat(it.nextVersionDraft.reports.first().source).isEqualTo("")
        assertThat(it.nextVersionDraft.reports.first().description).isNull()
      })
    }
  }

  @Test
  fun `update next version report on discontinued app`() {
    appVersionDevelopmentUseCases.changeReport(discontinuedAppId, "TestReport", "", null, null).expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  @Test
  fun `remove next version unknown report`() {
    appVersionDevelopmentUseCases.removeReport(activeAppId, "Unknown").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.REPORT_NOT_FOUND)
    )
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
    }
  }

  @Test
  fun `remove next version report`() {
    appVersionDevelopmentUseCases.removeReport(activeAppId, "TestReport").expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(activeAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft.reports).isEmpty()
      })
    }
  }

  @Test
  fun `remove next version report on discontinued app`() {
    appVersionDevelopmentUseCases.removeReport(discontinuedAppId, "TestReport").expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  @Test
  fun `release next version without changes`() {
    appVersionDevelopmentUseCases.release(developmentAppNoChangesId).expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.VERSION_RELEASE_NO_CHANGES)
    )
    verifyMocks {
      queryPersistence.getOrError(developmentAppNoChangesId)
    }
  }

  @Test
  fun `release next version with blank note`() {
    appVersionDevelopmentUseCases.release(developmentAppNoReleaseNotesTitleId).expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.VERSION_RELEASE_TITLE_BLANK)
    )
    verifyMocks {
      queryPersistence.getOrError(developmentAppNoReleaseNotesTitleId)
    }
  }

  @Test
  fun `release next version without features or bugfixes information`() {
    appVersionDevelopmentUseCases.release(developmentAppNoReleaseNotesFeaturesOrBugfixesId).expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.VERSION_RELEASE_NOTE_FEATURES_OR_BUGFIXES)
    )
    verifyMocks {
      queryPersistence.getOrError(developmentAppNoReleaseNotesFeaturesOrBugfixesId)
    }
  }

  @Test
  fun `release next version`() {
    appVersionDevelopmentUseCases.release(developmentAppId).expectSuccess()
    verifyMocks {
      queryPersistence.getOrError(developmentAppId)
      commandPersistence.upsert(withArg {
        assertThat(it.nextVersionDraft).isNotNull
        assertThat(it.latestVersion).isNotNull
        assertThat(it.latestVersion!!.version).isEqualTo(Semver("0.1.0"))
      })
      eventBus.publish(withArg {
        assertThat(it).isInstanceOf(DomainEvent.AppVersionReleased::class.java)
        val appVersionReleased = it as DomainEvent.AppVersionReleased
        assertThat(appVersionReleased.appId).isEqualTo(developmentAppId)
        assertThat(appVersionReleased.version).isEqualTo(Semver("0.1.0"))
      })
    }
  }

  @Test
  fun `release next version on discontinued app`() {
    appVersionDevelopmentUseCases.release(discontinuedAppId).expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED)
    )
    verifyMocks {
      queryPersistence.getOrError(discontinuedAppId)
    }
  }

  private fun verifyMocks(verifyBlock: (MockKVerificationScope.() -> Unit)? = null) {
    if (verifyBlock != null) {
      verifySequence(inverse = false, verifyBlock = verifyBlock)
    }
    confirmVerified(queryPersistence)
    confirmVerified(commandPersistence)
    confirmVerified(eventBus)
  }
}
