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

    private val developer = User.create("foo@bar.com", "Fooby Bar").expectSuccess()
    private val developerId = developer.id

    private val developmentApp = App.create("Development App", developerId, " ").expectSuccess()
    private val developmentAppId = developmentApp.id

    private val activeApp = App.create("Active App", developerId, " ").expectSuccess()
        .createDevelopmentVersionDatatype("TestDatatype").expectSuccess()
        .createDevelopmentVersionReport("TestReport").expectSuccess()
        .releaseDevelopmentVersion(AppVersionChangeType.FEATURE, "First feature!").expectSuccess()
        .createDevelopmentVersion().expectSuccess()
    private val activeAppId = activeApp.id

    private val activeAppMultipleVersions = App.create("Active App Multiple Versions", developerId, " ").expectSuccess()
        .createDevelopmentVersionDatatype("TestDatatype").expectSuccess()
        .createDevelopmentVersionReport("TestReport").expectSuccess()
        .releaseDevelopmentVersion(AppVersionChangeType.FEATURE, "First feature!").expectSuccess()
        .createDevelopmentVersion().expectSuccess()
        .releaseDevelopmentVersion(AppVersionChangeType.BUGFIX, "Nothing changed?!?").expectSuccess()
    private val activeAppMultipleVersionsId = activeAppMultipleVersions.id

    private val discontinuedApp = App.create("Discontinued App", developerId, "").expectSuccess()
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
    fun `create valid app`() {
        fun App.assertions() {
            assertThat(name).isEqualTo("Test App")
            assertThat(developer).isEqualTo(developerId)
            assertThat(description).isEqualTo("Fancy App")
            assertThat(versions).isEmpty()
            assertThat(latestVersion).isNull()
            assertThat(developmentVersion).isNotNull
            assertThat(developmentVersion!!.datatypes).isEmpty()
            assertThat(developmentVersion!!.reports).isEmpty()
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
    fun `create invalid app name missing`() {
        appLifecycleUseCases.create(" ", developerId, "Fancy App").expectError(
            code = AppErrorCodes.NAME_BLANK,
            details = null,
        )
        verifyMocks()
    }

    @Test
    fun `create invalid app developer unknown`() {
        appLifecycleUseCases.create("Fancy App", UUID.randomUUID(), "Fancy App").expectError(
            code = UserErrorCodes.NOT_FOUND,
            details = null,
        )
        verifyMocks()
    }

    @Test
    fun `prepare next version with existing development version`() {
        appLifecycleUseCases.prepareNextVersion(developmentAppId).expectError(
            code = AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(developmentAppId)
        }
    }

    @Test
    fun `prepare next version without existing development version`() {
        appLifecycleUseCases.prepareNextVersion(activeAppMultipleVersionsId).expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion).isNotNull
            })
        }
    }

    @Test
    fun `prepare next version on discontinued app`() {
        appLifecycleUseCases.prepareNextVersion(discontinuedAppId).expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `release next version with existing development version but blank note`() {
        appLifecycleUseCases.releaseNextVersion(developmentAppId, AppVersionChangeType.FEATURE, " ").expectError(
            code = AppErrorCodes.VERSION_RELEASE_NOTE_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(developmentAppId)
        }
    }

    @Test
    fun `release next version with existing development version`() {
        appLifecycleUseCases.releaseNextVersion(developmentAppId, AppVersionChangeType.FEATURE, "Release it!").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(developmentAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion).isNull()
                assertThat((actual as App).latestVersion).isNotNull
                assertThat((actual as App).latestVersion!!.version).isEqualTo(Semver("0.1.0"))
                assertThat((actual as App).latestVersion!!.releaseNotes.changeType).isEqualTo(AppVersionChangeType.FEATURE)
                assertThat((actual as App).latestVersion!!.releaseNotes.note).isEqualTo("Release it!")
            })
        }
    }

    @Test
    fun `release next version without existing development version`() {
        appLifecycleUseCases.releaseNextVersion(activeAppMultipleVersionsId, AppVersionChangeType.FEATURE, "Note").expectError(
            code = AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `release next version on discontinued app`() {
        appLifecycleUseCases.releaseNextVersion(discontinuedAppId, AppVersionChangeType.FEATURE, "Note").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `update release note`() {
        appLifecycleUseCases.changeVersionReleaseNote(activeAppMultipleVersionsId, Semver("0.1.0"), "New note!").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).versions.first { it.version == Semver("0.1.0") }.releaseNotes.note).isEqualTo("New note!")
            })
        }
    }

    @Test
    fun `update release note on unknown version`() {
        appLifecycleUseCases.changeVersionReleaseNote(activeAppMultipleVersionsId, Semver("6.6.6"), "New note!").expectError(
            code = AppErrorCodes.VERSION_NOT_FOUND,
            details = "6.6.6",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `update release note on discontinued app`() {
        appLifecycleUseCases.changeVersionReleaseNote(discontinuedAppId, Semver("0.1.0"), "New note!").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `discontinue active app`() {
        appLifecycleUseCases.discontinue(activeAppId).expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).discontinued).isTrue()
            })
        }
    }

    @Test
    fun `discontinue discontinued app`() {
        appLifecycleUseCases.discontinue(discontinuedAppId).expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `delete active app`() {
        appLifecycleUseCases.delete(activeAppId).expectError(
            code = AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `delete discontinued app`() {
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

    private val developer = User.create("foo@bar.com", "Fooby Bar").expectSuccess()
    private val developerId = developer.id

    private val developmentApp = App.create("Development App", developerId, " ").expectSuccess()
    private val developmentAppId = developmentApp.id

    private val activeApp = App.create("Active App", developerId, " ").expectSuccess()
        .createDevelopmentVersionDatatype("TestDatatype").expectSuccess()
        .createDevelopmentVersionReport("TestReport").expectSuccess()
        .releaseDevelopmentVersion(AppVersionChangeType.FEATURE, "First feature!").expectSuccess()
        .createDevelopmentVersion().expectSuccess()
    private val activeAppId = activeApp.id

    private val activeAppMultipleVersions = App.create("Active App Multiple Versions", developerId, " ").expectSuccess()
        .createDevelopmentVersionDatatype("TestDatatype").expectSuccess()
        .createDevelopmentVersionReport("TestReport").expectSuccess()
        .releaseDevelopmentVersion(AppVersionChangeType.FEATURE, "First feature!").expectSuccess()
        .createDevelopmentVersion().expectSuccess()
        .releaseDevelopmentVersion(AppVersionChangeType.BUGFIX, "Nothing changed?!?").expectSuccess()
    private val activeAppMultipleVersionsId = activeAppMultipleVersions.id

    private val discontinuedApp = App.create("Discontinued App", developerId, "").expectSuccess()
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
    fun `create next version datatype with blank name`() {
        appVersionDevelopmentUseCases.createNextVersionDatatype(activeAppId, " ").expectError(
            code = AppErrorCodes.DATATYPE_NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `create next version datatype with invalid name`() {
        appVersionDevelopmentUseCases.createNextVersionDatatype(activeAppId, "some Datatype").expectError(
            code = AppErrorCodes.DATATYPE_NAME_INVALID,
            details = "'some Datatype' does not match ([A-Z][a-z]*)+",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `create next version datatype with duplicate name`() {
        appVersionDevelopmentUseCases.createNextVersionDatatype(activeAppId, "TestDatatype").expectError(
            code = AppErrorCodes.DATATYPE_NAME_DUPLICATE,
            details = "TestDatatype",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `create next version datatype`() {
        appVersionDevelopmentUseCases.createNextVersionDatatype(activeAppId, "NewDatatype").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.datatypes).contains(AppDatatypeDraft.create("NewDatatype", "", null).expectSuccess())
            })
        }
    }

    @Test
    fun `create next version datatype without draft version`() {
        appVersionDevelopmentUseCases.createNextVersionDatatype(activeAppMultipleVersionsId, " ").expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `create next version datatype on discontinued app`() {
        appVersionDevelopmentUseCases.createNextVersionDatatype(discontinuedAppId, " ").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `rename next version datatype with blank name`() {
        appVersionDevelopmentUseCases.renameNextVersionDatatype(activeAppId, "TestDatatype", " ").expectError(
            code = AppErrorCodes.DATATYPE_NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version datatype with invalid name`() {
        appVersionDevelopmentUseCases.renameNextVersionDatatype(activeAppId, "TestDatatype", "Test Datatype").expectError(
            code = AppErrorCodes.DATATYPE_NAME_INVALID,
            details = "'Test Datatype' does not match ([A-Z][a-z]*)+",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version datatype with unknown name`() {
        appVersionDevelopmentUseCases.renameNextVersionDatatype(activeAppId, "Unknown", "Unknown Datatype").expectError(
            code = AppErrorCodes.DATATYPE_NOT_FOUND,
            details = "Unknown",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version datatype with duplicate name`() {
        appVersionDevelopmentUseCases.renameNextVersionDatatype(activeAppId, "Unknown", "TestDatatype").expectError(
            code = AppErrorCodes.DATATYPE_NAME_DUPLICATE,
            details = "TestDatatype",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version datatype`() {
        appVersionDevelopmentUseCases.renameNextVersionDatatype(activeAppId, "TestDatatype", "NewDatatype").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.datatypes).hasSize(1)
                assertThat((actual as App).developmentVersion!!.datatypes.first().name).isEqualTo("NewDatatype")
            })
        }
    }

    @Test
    fun `rename next version datatype without draft version`() {
        appVersionDevelopmentUseCases.renameNextVersionDatatype(activeAppMultipleVersionsId, "TestDatatype", "SomeOtherName").expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `rename next version datatype on discontinued app`() {
        appVersionDevelopmentUseCases.renameNextVersionDatatype(discontinuedAppId, "TestDatatype", "SomeOtherName").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `update next version datatype with invalid schema`() {
        appVersionDevelopmentUseCases.updateNextVersionDatatype(activeAppId, "TestDatatype", "NEW SCHEMA", null).expectError(
            code = AppErrorCodes.DATATYPE_SCHEMA_INVALID,
            details = "Expected a ':' after a key at 115 [character 1 line 7]",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `update next version datatype with unknown name`() {
        appVersionDevelopmentUseCases.updateNextVersionDatatype(activeAppId, "Unknown", "", null).expectError(
            code = AppErrorCodes.DATATYPE_NOT_FOUND,
            details = "Unknown",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `update next version datatype`() {
        appVersionDevelopmentUseCases.updateNextVersionDatatype(activeAppId, "TestDatatype", "", "NEW DESC").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.datatypes).hasSize(1)
                assertThat((actual as App).developmentVersion!!.datatypes.first().schemaContent).isEqualTo("")
                assertThat((actual as App).developmentVersion!!.datatypes.first().description).isEqualTo("NEW DESC")
            })
        }
    }

    @Test
    fun `update next version datatype with same schema and description`() {
        appVersionDevelopmentUseCases.updateNextVersionDatatype(activeAppId, "TestDatatype", "", null).expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.datatypes).hasSize(1)
                assertThat((actual as App).developmentVersion!!.datatypes.first().schemaContent).isEqualTo("")
                assertThat((actual as App).developmentVersion!!.datatypes.first().description).isNull()
            })
        }
    }

    @Test
    fun `update next version datatype without draft version`() {
        appVersionDevelopmentUseCases.updateNextVersionDatatype(activeAppMultipleVersionsId, "TestDatatype", "", null).expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `update next version datatype on discontinued app`() {
        appVersionDevelopmentUseCases.updateNextVersionDatatype(discontinuedAppId, "TestDatatype", "", null).expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `remove next version unknown datatype`() {
        appVersionDevelopmentUseCases.removeNextVersionDatatype(activeAppId, "Unknown").expectError(
            code = AppErrorCodes.DATATYPE_NOT_FOUND,
            details = "Unknown",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `remove next version datatype`() {
        appVersionDevelopmentUseCases.removeNextVersionDatatype(activeAppId, "TestDatatype").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.datatypes).isEmpty()
            })
        }
    }

    @Test
    fun `remove next version datatype without draft version`() {
        appVersionDevelopmentUseCases.removeNextVersionDatatype(activeAppMultipleVersionsId, "TestDatatype").expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `remove next version datatype on discontinued app`() {
        appVersionDevelopmentUseCases.removeNextVersionDatatype(discontinuedAppId, "TestDatatype").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `create next version report with blank name`() {
        appVersionDevelopmentUseCases.createNextVersionReport(activeAppId, " ").expectError(
            code = AppErrorCodes.REPORT_NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `create next version report with duplicate name`() {
        appVersionDevelopmentUseCases.createNextVersionReport(activeAppId, "TestReport").expectError(
            code = AppErrorCodes.REPORT_NAME_DUPLICATE,
            details = "TestReport",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `create next version report`() {
        appVersionDevelopmentUseCases.createNextVersionReport(activeAppId, "NewReport").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.reports).contains(AppReport.create("NewReport", null, "").expectSuccess())
            })
        }
    }

    @Test
    fun `create next version report without draft version`() {
        appVersionDevelopmentUseCases.createNextVersionReport(activeAppMultipleVersionsId, " ").expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `create next version report on discontinued app`() {
        appVersionDevelopmentUseCases.createNextVersionReport(discontinuedAppId, " ").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `rename next version report with blank name`() {
        appVersionDevelopmentUseCases.renameNextVersionReport(activeAppId, "TestReport", " ").expectError(
            code = AppErrorCodes.REPORT_NAME_BLANK,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version report with unknown name`() {
        appVersionDevelopmentUseCases.renameNextVersionReport(activeAppId, "Unknown", "Unknown Report").expectError(
            code = AppErrorCodes.REPORT_NOT_FOUND,
            details = "Unknown",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version report with duplicate name`() {
        appVersionDevelopmentUseCases.renameNextVersionReport(activeAppId, "Unknown", "TestReport").expectError(
            code = AppErrorCodes.REPORT_NAME_DUPLICATE,
            details = "TestReport",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `rename next version report`() {
        appVersionDevelopmentUseCases.renameNextVersionReport(activeAppId, "TestReport", "NewReport").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.reports).hasSize(1)
                assertThat((actual as App).developmentVersion!!.reports.first().name).isEqualTo("NewReport")
            })
        }
    }

    @Test
    fun `rename next version report without draft version`() {
        appVersionDevelopmentUseCases.renameNextVersionReport(activeAppMultipleVersionsId, "TestReport", "SomeOtherName").expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `rename next version report on discontinued app`() {
        appVersionDevelopmentUseCases.renameNextVersionReport(discontinuedAppId, "TestReport", "SomeOtherName").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `update next version report with unknown name`() {
        appVersionDevelopmentUseCases.updateNextVersionReport(activeAppId, "Unknown", "", null).expectError(
            code = AppErrorCodes.REPORT_NOT_FOUND,
            details = "Unknown",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `update next version report`() {
        appVersionDevelopmentUseCases.updateNextVersionReport(activeAppId, "TestReport", "NEW SRC", "NEW DESC").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.reports).hasSize(1)
                assertThat((actual as App).developmentVersion!!.reports.first().source).isEqualTo("NEW SRC")
                assertThat((actual as App).developmentVersion!!.reports.first().description).isEqualTo("NEW DESC")
            })
        }
    }

    @Test
    fun `update next version report with same source and description`() {
        appVersionDevelopmentUseCases.updateNextVersionReport(activeAppId, "TestReport", "", null).expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.reports).hasSize(1)
                assertThat((actual as App).developmentVersion!!.reports.first().source).isEqualTo("")
                assertThat((actual as App).developmentVersion!!.reports.first().description).isNull()
            })
        }
    }

    @Test
    fun `update next version report without draft version`() {
        appVersionDevelopmentUseCases.updateNextVersionReport(activeAppMultipleVersionsId, "TestReport", "", null).expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `update next version report on discontinued app`() {
        appVersionDevelopmentUseCases.updateNextVersionReport(discontinuedAppId, "TestReport", "", null).expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(discontinuedAppId)
        }
    }

    @Test
    fun `remove next version unknown report`() {
        appVersionDevelopmentUseCases.removeNextVersionReport(activeAppId, "Unknown").expectError(
            code = AppErrorCodes.REPORT_NOT_FOUND,
            details = "Unknown",
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
        }
    }

    @Test
    fun `remove next version report`() {
        appVersionDevelopmentUseCases.removeNextVersionReport(activeAppId, "TestReport").expectSuccess()
        verifyMocks {
            queryPersistence.getOrError(activeAppId)
            commandPersistence.upsert(withArg {
                assertThat((actual as App).developmentVersion!!.reports).isEmpty()
            })
        }
    }

    @Test
    fun `remove next version report without draft version`() {
        appVersionDevelopmentUseCases.removeNextVersionReport(activeAppMultipleVersionsId, "RestReport").expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
        verifyMocks {
            queryPersistence.getOrError(activeAppMultipleVersionsId)
        }
    }

    @Test
    fun `remove next version report on discontinued app`() {
        appVersionDevelopmentUseCases.removeNextVersionReport(discontinuedAppId, "TestReport").expectError(
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
