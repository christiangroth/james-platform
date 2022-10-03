package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.user.User
import de.chrgroth.james.user.UserErrorCodes
import de.chrgroth.james.user.UserQueryPersistencePort
import io.mockk.MockKVerificationScope
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class AppLifecycleUseCasesTests {

    private val developer = User.create(email = "foo@bar.com", name = "Fooby Bar").expectSuccess()
    private val developerId = developer.id

    private val developmentApp = App.create(name = "Development App", developerId = developerId, description = " ").expectSuccess()
    private val developmentAppId = developmentApp.id

    private val activeApp = App.create(name = "Active App", developerId = developerId, description = " ").expectSuccess()
        .addNextVersionDraftDatatype("TestDatatype").expectSuccess()
        .addNextVersionDraftReport("TestReport").expectSuccess()
        .releaseNextVersionDraft(AppVersionChangeType.FEATURE, "First feature!").expectSuccess()
    private val activeAppId = activeApp.id

    private val activeAppMultipleVersions = App.create(name = "Active App Multiple Versions", developerId = developerId, description = " ").expectSuccess()
        .addNextVersionDraftDatatype("TestDatatype").expectSuccess()
        .addNextVersionDraftReport("TestReport").expectSuccess()
        .releaseNextVersionDraft(AppVersionChangeType.FEATURE, "First feature!").expectSuccess()
        .releaseNextVersionDraft(AppVersionChangeType.BUGFIX, "Nothing changed?!?").expectSuccess()
    private val activeAppMultipleVersionsId = activeAppMultipleVersions.id

    private val discontinuedApp = App.create(name = "Discontinued App", developerId = developerId, description = "").expectSuccess()
        .discontinue().expectSuccess()
    private val discontinuedAppId = discontinuedApp.id

    private lateinit var userQueryPersistence: UserQueryPersistencePort
    private lateinit var queryPersistence: AppQueryPersistencePort
    private lateinit var commandPersistence: AppCommandPersistencePort
    private lateinit var appLifecycleUseCases: AppLifecycleUseCases

    @BeforeEach
    internal fun initialize() {
        userQueryPersistence = mockk<UserQueryPersistencePort>().also {
            every { it.getOrError(any()) } returns (Error(UserErrorCodes.NOT_FOUND, details = null))
            every { it.getOrError(developerId) } returns (Result(developer))
        }

        queryPersistence = mockk<AppQueryPersistencePort>().also {
            every { it.getOrError(developmentAppId) } returns (Result(developmentApp))
            every { it.getOrError(activeAppId) } returns (Result(activeApp))
            every { it.getOrError(activeAppMultipleVersionsId) } returns (Result(activeAppMultipleVersions))
            every { it.getOrError(discontinuedAppId) } returns (Result(discontinuedApp))
        }

        commandPersistence = mockk<AppCommandPersistencePort>().also {
            every { it.upsert(any()) } answers { Result(this.args[0] as App) }
            every { it.delete(any()) } answers { Result(Unit) }
        }

        appLifecycleUseCases = AppLifecycleUseCasesService(userQueryPersistence, queryPersistence, commandPersistence)
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
            commandPersistence.upsert(withArg {
                (actual as App).assertions()
            })
        }
    }

    @Test
    fun `create app with blank name missing`() {
        appLifecycleUseCases.create(" ", developerId, "Fancy App").expectError(
            code = AppErrorCodes.NAME_BLANK,
            details = null,
        )
        verifyMocks()
    }

    @Test
    fun `create app with unknown developer`() {
        appLifecycleUseCases.create("Fancy App", UUID.randomUUID(), "Fancy App").expectError(
            code = UserErrorCodes.NOT_FOUND,
            details = null,
        )
        verifyMocks()
    }

    @Test
    fun `change release note`() {
        appLifecycleUseCases.changeReleaseNote(activeAppMultipleVersionsId, Semver("0.1.0"), "New note!").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).releasedVersions.first { it.version == Semver("0.1.0") }.releaseNotes.note).isEqualTo("New note!")
            })
        }
    }

    @Test
    fun `change release note on unknown version`() {
        appLifecycleUseCases.changeReleaseNote(activeAppMultipleVersionsId, Semver("6.6.6"), "New note!").expectError(
            code = AppErrorCodes.RELEASE_VERSION_NOT_FOUND,
            details = "6.6.6",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `change release note on discontinued app`() {
        appLifecycleUseCases.changeReleaseNote(discontinuedAppId, Semver("0.1.0"), "New note!").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
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
                assertThat((actual as App).discontinued).isTrue()
            })
        }
    }

    @Test
    fun `discontinue app already discontinued`() {
        appLifecycleUseCases.discontinue(discontinuedAppId).expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `delete app still active`() {
        appLifecycleUseCases.delete(activeAppId).expectError(
            code = AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED,
            details = null,
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
    }
}

class AppVersionDevelopmentUseCasesTests {

    private val developer = User.create(email = "foo@bar.com", name = "Fooby Bar").expectSuccess()
    private val developerId = developer.id

    private val developmentApp = App.create(name = "Development App", developerId = developerId, description = " ").expectSuccess()
    private val developmentAppId = developmentApp.id

    private val activeApp = App.create(name = "Active App", developerId = developerId, description = " ").expectSuccess()
        .addNextVersionDraftDatatype("TestDatatype").expectSuccess()
        .addNextVersionDraftReport("TestReport").expectSuccess()
        .releaseNextVersionDraft(AppVersionChangeType.FEATURE, "First feature!").expectSuccess()
    private val activeAppId = activeApp.id

    private val activeAppMultipleVersions = App.create(name = "Active App Multiple Versions", developerId = developerId, description = " ").expectSuccess()
        .addNextVersionDraftDatatype("TestDatatype").expectSuccess()
        .addNextVersionDraftReport("TestReport").expectSuccess()
        .releaseNextVersionDraft(AppVersionChangeType.FEATURE, "First feature!").expectSuccess()
        .releaseNextVersionDraft(AppVersionChangeType.BUGFIX, "Nothing changed?!?").expectSuccess()
    private val activeAppMultipleVersionsId = activeAppMultipleVersions.id

    private val discontinuedApp = App.create(name = "Discontinued App", developerId = developerId, description = "").expectSuccess()
        .discontinue().expectSuccess()
    private val discontinuedAppId = discontinuedApp.id

    private lateinit var userQueryPersistence: UserQueryPersistencePort
    private lateinit var queryPersistence: AppQueryPersistencePort
    private lateinit var commandPersistence: AppCommandPersistencePort
    private lateinit var appVersionDevelopmentUseCases: AppVersionDevelopmentUseCases

    @BeforeEach
    internal fun initialize() {
        userQueryPersistence = mockk<UserQueryPersistencePort>().also {
            every { it.getOrError(any()) } returns (Error(UserErrorCodes.NOT_FOUND, details = null))
            every { it.getOrError(developerId) } returns (Result(developer))
        }

        queryPersistence = mockk<AppQueryPersistencePort>().also {
            every { it.getOrError(developmentAppId) } returns (Result(developmentApp))
            every { it.getOrError(activeAppId) } returns (Result(activeApp))
            every { it.getOrError(activeAppMultipleVersionsId) } returns (Result(activeAppMultipleVersions))
            every { it.getOrError(discontinuedAppId) } returns (Result(discontinuedApp))
        }

        commandPersistence = mockk<AppCommandPersistencePort>().also {
            every { it.upsert(any()) } answers { Result(this.args[0] as App) }
            every { it.delete(any()) } answers { Result(Unit) }
        }

        appVersionDevelopmentUseCases = AppVersionDevelopmentUseCasesService(queryPersistence, commandPersistence)
    }

    @Test
    fun `add datatype with blank name`() {
        appVersionDevelopmentUseCases.addDatatype(activeAppId, " ").expectError(
            code = AppErrorCodes.DATATYPE_NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `add datatype with invalid name`() {
        appVersionDevelopmentUseCases.addDatatype(activeAppId, "some Datatype").expectError(
            code = AppErrorCodes.DATATYPE_NAME_INVALID,
            details = "'some Datatype' does not match ([A-Z][a-z]*)+",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `add datatype with duplicate name`() {
        appVersionDevelopmentUseCases.addDatatype(activeAppId, "TestDatatype").expectError(
            code = AppErrorCodes.DATATYPE_NAME_DUPLICATE,
            details = "TestDatatype",
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
                assertThat((actual as App).nextVersionDraft.datatypes).contains(AppDatatypeDraft.create("NewDatatype", "", null).expectSuccess())
            })
        }
    }

    @Test
    fun `add datatype on discontinued app`() {
        appVersionDevelopmentUseCases.addDatatype(discontinuedAppId, " ").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `rename next version datatype to blank name`() {
        appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", "", null, " ").expectError(
            code = AppErrorCodes.DATATYPE_NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version datatype with invalid name`() {
        appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", "", null, "Test Datatype").expectError(
            code = AppErrorCodes.DATATYPE_NAME_INVALID,
            details = "'Test Datatype' does not match ([A-Z][a-z]*)+",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version datatype with unknown name`() {
        appVersionDevelopmentUseCases.changeDatatype(activeAppId, "Unknown", "", null, "Unknown Datatype").expectError(
            code = AppErrorCodes.DATATYPE_NOT_FOUND,
            details = "Unknown",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version datatype with duplicate name`() {
        appVersionDevelopmentUseCases.changeDatatype(activeAppId, "Unknown", "", null, "TestDatatype").expectError(
            code = AppErrorCodes.DATATYPE_NAME_DUPLICATE,
            details = "TestDatatype",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version datatype`() {
        val nextVersion = appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", "", null, "NewDatatype").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).nextVersionDraft.datatypes).hasSize(1)
                assertThat((actual as App).nextVersionDraft.datatypes.first().name).isEqualTo("NewDatatype")
            })
        }

        // renaming a datatype should be a breaking change, check that
        assertThat(isBreaking(activeApp.latestVersion!!, nextVersion)).isTrue
    }

    @Test
    fun `rename next version datatype on discontinued app`() {
        appVersionDevelopmentUseCases.changeDatatype(discontinuedAppId, "TestDatatype", "", null, "SomeOtherName").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `update next version datatype with invalid schema`() {
        appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", "NEW SCHEMA", null, null).expectError(
            code = AppErrorCodes.DATATYPE_SCHEMA_INVALID,
            details = "Expected a ':' after a key at 115 [character 1 line 7]",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `update next version datatype with unknown name`() {
        appVersionDevelopmentUseCases.changeDatatype(activeAppId, "Unknown", "", null, null).expectError(
            code = AppErrorCodes.DATATYPE_NOT_FOUND,
            details = "Unknown",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `update next version datatype`() {
        appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", "", "NEW DESC", null).expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).nextVersionDraft.datatypes).hasSize(1)
                assertThat((actual as App).nextVersionDraft.datatypes.first().schemaContent).isEqualTo("")
                assertThat((actual as App).nextVersionDraft.datatypes.first().description).isEqualTo("NEW DESC")
            })
        }
    }

    @Test
    fun `update next version datatype with same schema and description`() {
        appVersionDevelopmentUseCases.changeDatatype(activeAppId, "TestDatatype", "", null, null).expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).nextVersionDraft.datatypes).hasSize(1)
                assertThat((actual as App).nextVersionDraft.datatypes.first().schemaContent).isEqualTo("")
                assertThat((actual as App).nextVersionDraft.datatypes.first().description).isNull()
            })
        }
    }

    @Test
    fun `update next version datatype on discontinued app`() {
        appVersionDevelopmentUseCases.changeDatatype(discontinuedAppId, "TestDatatype", "", null, null).expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `remove next version unknown datatype`() {
        appVersionDevelopmentUseCases.removeDatatype(activeAppId, "Unknown").expectError(
            code = AppErrorCodes.DATATYPE_NOT_FOUND,
            details = "Unknown",
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
                assertThat((actual as App).nextVersionDraft.datatypes).isEmpty()
            })
        }

        // removing a datatype should be a breaking change, check that
        assertThat(isBreaking(activeApp.latestVersion!!, nextVersion)).isTrue
    }

    @Test
    fun `remove next version datatype on discontinued app`() {
        appVersionDevelopmentUseCases.removeDatatype(discontinuedAppId, "TestDatatype").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `add report with blank name`() {
        appVersionDevelopmentUseCases.addReport(activeAppId, " ").expectError(
            code = AppErrorCodes.REPORT_NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `add report with duplicate name`() {
        appVersionDevelopmentUseCases.addReport(activeAppId, "TestReport").expectError(
            code = AppErrorCodes.REPORT_NAME_DUPLICATE,
            details = "TestReport",
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
                assertThat((actual as App).nextVersionDraft.reports).contains(AppReport.create("NewReport", null, "").expectSuccess())
            })
        }
    }

    @Test
    fun `add report on discontinued app`() {
        appVersionDevelopmentUseCases.addReport(discontinuedAppId, " ").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `rename next version report with blank name`() {
        appVersionDevelopmentUseCases.changeReport(activeAppId, "TestReport", "", null, " ").expectError(
            code = AppErrorCodes.REPORT_NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version report with unknown name`() {
        appVersionDevelopmentUseCases.changeReport(activeAppId, "Unknown", "", null, "Unknown Report").expectError(
            code = AppErrorCodes.REPORT_NOT_FOUND,
            details = "Unknown",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version report with duplicate name`() {
        appVersionDevelopmentUseCases.changeReport(activeAppId, "Unknown", "", null, "TestReport").expectError(
            code = AppErrorCodes.REPORT_NAME_DUPLICATE,
            details = "TestReport",
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
                assertThat((actual as App).nextVersionDraft.reports).hasSize(1)
                assertThat((actual as App).nextVersionDraft.reports.first().name).isEqualTo("NewReport")
            })
        }
    }

    @Test
    fun `rename next version report on discontinued app`() {
        appVersionDevelopmentUseCases.changeReport(discontinuedAppId, "TestReport", "", null, "SomeOtherName").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `update next version report with unknown name`() {
        appVersionDevelopmentUseCases.changeReport(activeAppId, "Unknown", "", null, null).expectError(
            code = AppErrorCodes.REPORT_NOT_FOUND,
            details = "Unknown",
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
                assertThat((actual as App).nextVersionDraft.reports).hasSize(1)
                assertThat((actual as App).nextVersionDraft.reports.first().source).isEqualTo("NEW SRC")
                assertThat((actual as App).nextVersionDraft.reports.first().description).isEqualTo("NEW DESC")
            })
        }
    }

    @Test
    fun `update next version report with same source and description`() {
        appVersionDevelopmentUseCases.changeReport(activeAppId, "TestReport", "", null, null).expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).nextVersionDraft.reports).hasSize(1)
                assertThat((actual as App).nextVersionDraft.reports.first().source).isEqualTo("")
                assertThat((actual as App).nextVersionDraft.reports.first().description).isNull()
            })
        }
    }

    @Test
    fun `update next version report on discontinued app`() {
        appVersionDevelopmentUseCases.changeReport(discontinuedAppId, "TestReport", "", null, null).expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `remove next version unknown report`() {
        appVersionDevelopmentUseCases.removeReport(activeAppId, "Unknown").expectError(
            code = AppErrorCodes.REPORT_NOT_FOUND,
            details = "Unknown",
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
                assertThat((actual as App).nextVersionDraft.reports).isEmpty()
            })
        }
    }

    @Test
    fun `remove next version report on discontinued app`() {
        appVersionDevelopmentUseCases.removeReport(discontinuedAppId, "TestReport").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `release next version with blank note`() {
        appVersionDevelopmentUseCases.release(developmentAppId, AppVersionChangeType.FEATURE, " ").expectError(
            code = AppErrorCodes.VERSION_RELEASE_NOTE_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(developmentAppId)
        }
    }

    @Test
    fun `release next version`() {
        appVersionDevelopmentUseCases.release(developmentAppId, AppVersionChangeType.FEATURE, "Release it!").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(developmentAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).nextVersionDraft).isNotNull
                assertThat((actual as App).latestVersion).isNotNull
                assertThat((actual as App).latestVersion!!.version).isEqualTo(Semver("0.1.0"))
                assertThat((actual as App).latestVersion!!.releaseNotes.changeType).isEqualTo(AppVersionChangeType.FEATURE)
                assertThat((actual as App).latestVersion!!.releaseNotes.note).isEqualTo("Release it!")
            })
        }
    }

    @Test
    fun `release next version on discontinued app`() {
        appVersionDevelopmentUseCases.release(discontinuedAppId, AppVersionChangeType.FEATURE, "Note").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
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
    }
}
