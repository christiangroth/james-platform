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

// TODO #25 test Datatype name pattern -> model tests?

class AppDomainTests {

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

        appLifecycleUseCases = AppLifecycleUseCasesService(userQueryPersistence, queryPersistence, commandPersistence)
        appVersionDevelopmentUseCases = AppVersionDevelopmentUseCasesService(queryPersistence, commandPersistence)
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
    fun `assert status of development app`() {
        assertThat(developmentApp.status).isEqualTo(AppStatus.DEVELOPMENT)
    }

    @Test
    fun `assert status of active app`() {
        assertThat(activeApp.status).isEqualTo(AppStatus.ACTIVE)
    }

    @Test
    fun `does not resolve latest version if no versions are present`() {
        assertThat(developmentApp.latestVersion).isNull()
    }

    @Test
    fun `resolves latest version correctly for single version`() {
        assertThat(activeApp.latestVersion).isNotNull
        assertThat(activeApp.latestVersion!!.version).isEqualTo(Semver("0.1.0"))
        assertThat(activeApp.latestVersion!!.datatypes).isNotEmpty
        assertThat(activeApp.latestVersion!!.datatypes.first().schemaContent).isBlank
        assertThat(activeApp.latestVersion!!.datatypes.first().generateJsonSchemaContent()).isEqualTo(
            """{
  "title": "TestDatatype",
  "description": "",
  "type": "object",
  "additionalProperties": false,

}""".trimIndent()
        )
    }

    @Test
    fun `resolves latest version correctly for multiple version`() {
        assertThat(activeAppMultipleVersions.latestVersion).isNotNull
        assertThat(activeAppMultipleVersions.latestVersion!!.version).isEqualTo(Semver("0.1.1"))
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

    /* TODO #25 TEST PLAN

    fun createNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>
    fun renameNextVersionDatatype(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft>
    fun updateNextVersionDatatype(id: UUID, name: String, schemaContent: String, description: String?): Maybe<AppVersionDraft>
    fun removeNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>

    fun createNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft>
    fun renameNextVersionReport(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft>
    fun updateNextVersionReport(id: UUID, name: String, source: String, description: String?): Maybe<AppVersionDraft>
    fun removeNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft>
     */

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
