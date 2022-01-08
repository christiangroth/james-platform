package de.chrgroth.james.app

import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import io.mockk.MockKVerificationScope
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #25 maybe split into general, datatypes and reports

// TODO #25 test changeVersionReleaseNote
// TODO #25 test Datatype name pattern

class AppDomainTests {

    private val developerId = UUID.randomUUID()

    private val existingApp = App.create("Test App", developerId, " ").expectSuccess()
    private val existingAppId = existingApp.id

    private lateinit var queryPersistence: AppQueryPersistencePort
    private lateinit var commandPersistence: AppCommandPersistencePort
    private lateinit var port: AppCommandPort

    @BeforeEach
    internal fun initialize() {
        queryPersistence = mockk<AppQueryPersistencePort>().also {
            every { it.getOrError(existingAppId) } returns (Result(existingApp))
        }

        commandPersistence = mockk<AppCommandPersistencePort>().also {
            every { it.upsert(any()) } answers { Result(this.args[0] as App) }
        }

        port = AppCommandAdapter(queryPersistence, commandPersistence)
    }

    @Test
    fun `create valid app`() {
        fun App.assertions() {
            Assertions.assertThat(name).isEqualTo("Test App")
            Assertions.assertThat(developer).isEqualTo(developerId)
            Assertions.assertThat(description).isEqualTo("Fancy App")
            Assertions.assertThat(versions).isEmpty()
            Assertions.assertThat(latestVersion).isNull()
            Assertions.assertThat(developmentVersion).isNull()
            Assertions.assertThat(status).isEqualTo(AppStatus.DEVELOPMENT)
            Assertions.assertThat(discontinued).isFalse
        }

        port.create("Test App", developerId, "Fancy App").expectSuccess().assertions()
        verifyMocks {
            commandPersistence.upsert(withArg {
                (actual as App).assertions()
            })
        }
    }

    @Test
    fun `create invalid app`() {
        port.create(" ", developerId, "Fancy App").expectError(
            code = AppErrorCodes.NAME_BLANK,
            details = null,
        )
        verifyMocks()
    }

    /*

    TEST PLAN
    ---------

    fun prepareNextVersion(id: UUID): Maybe<AppVersionDraft>
    fun releaseNextVersion(id: UUID, changeType: AppVersionChangeType, note: String): Maybe<AppVersion>
    fun changeVersionReleaseNote(id: UUID, version: Semver, note: String): Maybe<AppVersion>
    fun discontinue(id: UUID): Maybe<App>
    fun delete(id: UUID): Maybe<Unit>

    fun createNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>
    fun renameNextVersionDatatype(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft>
    fun updateNextVersionDatatype(id: UUID, name: String, schemaContent: String, description: String?): Maybe<AppVersionDraft>
    fun removeNextVersionDatatype(id: UUID, datatypeName: String): Maybe<AppVersionDraft>

    fun createNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft>
    fun renameNextVersionReport(id: UUID, oldName: String, newName: String): Maybe<AppVersionDraft>
    fun updateNextVersionReport(id: UUID, name: String, source: String, description: String?): Maybe<AppVersionDraft>
    fun removeNextVersionReport(id: UUID, reportName: String): Maybe<AppVersionDraft>
     */

    /*

    OLD TEST
    --------
    class AppDevelopmentTests {

    @Test
    fun `check app values trimmed`() {
        val app = App.create(
            name = " Fancy name ",
            developerId = UUID.randomUUID(),
            description = "Some description. ",
        ).expectSuccess()
        assertThat(app.name).isEqualTo("Fancy name")
        assertThat(app.description).isEqualTo("Some description.")
    }

    @Test
    fun `check app description trimmed to null if blank`() {
        val app = App.create(
            name = " Fancy name ",
            developerId = UUID.randomUUID(),
            description = " ",
        ).expectSuccess()
        assertThat(app.name).isEqualTo("Fancy name")
        assertThat(app.description).isNull()
    }

    @Test
    fun `does not resolves latest version if no versions are present`() {
        val app = createApp().copy(versions = emptyList())
        assertThat(app.latestVersion).isNull()
    }

    @Test
    fun `resolves latest version correctly`() {
        val app = createApp()
        assertThat(app.latestVersion).isNotNull
        assertThat(app.latestVersion!!.version).isEqualTo(Semver(0, 2, 0))
    }

    @Test
    fun `createDevelopmentVersion on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        app.createDevelopmentVersion().expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
    }

    @Test
    fun `createDevelopmentVersion with already existing draft`() {
        val app = createApp().copy(developmentVersion = AppVersionDraft.create().expectSuccess())
        app.createDevelopmentVersion().expectError(
            code = AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS,
            details = null,
        )
    }

    @Test
    fun `createDevelopmentVersion without latest`() {
        val app = createApp().copy(versions = emptyList())
        val updatedApp = app.createDevelopmentVersion().expectSuccess()
        assertThat(updatedApp.developmentVersion!!.datatypes).isEmpty()
        assertThat(updatedApp.developmentVersion!!.reports).isEmpty()
    }

    @Test
    fun createDevelopmentVersion() {
        val app = createApp()
        val updatedApp = app.createDevelopmentVersion().expectSuccess()
        assertThat(updatedApp.developmentVersion!!.datatypes).containsExactly(AppDatatypeDraft.create(name = "ModelOne",
            schemaContent = "".toStringProperty(),
            description = null).expectSuccess())
        assertThat(updatedApp.developmentVersion!!.reports).containsExactly(AppReport.create(name = "Report One", source = "", description = null)
            .expectSuccess())
    }

    @Test
    fun `updateDevelopmentVersion with datatype on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        app.updateDevelopmentVersionDatatype(name = "Foos", schemaContent = "", description = null).expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null
        )
    }

    @Test
    fun `updateDevelopmentVersion with datatype without draft`() {
        val app = createApp()
        app.updateDevelopmentVersionDatatype(name = "Foos", schemaContent = "", description = null).expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
    }

    @Test
    fun `updateDevelopmentVersion with datatype`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val updatedApp = app
            .createDevelopmentVersionDatatype(datatypeName = "Foos").expectSuccess()
            .updateDevelopmentVersionDatatype(name = "Foos", schemaContent = "", description = "Tadaa").expectSuccess()
        val developmentVersion = updatedApp.developmentVersion!!
        assertThat(developmentVersion.datatypes).contains(AppDatatypeDraft.create(name = "Foos", schemaContent = "", description = "Tadaa").expectSuccess())
    }

    @Test
    fun `removeDevelopmentVersionDatatype on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        app.removeDevelopmentVersionDatatype("Foos").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
    }

    @Test
    fun `removeDevelopmentVersionDatatype without draft`() {
        val app = createApp()
        app.removeDevelopmentVersionDatatype("Foos").expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
    }

    @Test
    fun `removeDevelopmentVersionDatatype with non existent datatype`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        app.removeDevelopmentVersionDatatype("Not-Existent").expectError(
            code = AppErrorCodes.DATATYPE_NOT_FOUND,
            details = null,
        )
    }

    @Test
    fun removeDevelopmentVersionDatatype() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val updatedApp = app.removeDevelopmentVersionDatatype("ModelOne").expectSuccess()
        val developmentVersion = updatedApp.developmentVersion!!
        assertThat(developmentVersion.datatypes).isEmpty()
    }

    @Test
    fun `updateDevelopmentVersion with report on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        app.updateDevelopmentVersionReport(name = "Foos Report", source = "", description = null).expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null
        )
    }

    @Test
    fun `updateDevelopmentVersion with report without draft`() {
        val app = createApp()
        app.updateDevelopmentVersionReport(name = "Foos AppReport", source = "", description = null).expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
    }

    @Test
    fun `updateDevelopmentVersion with report`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val updatedApp = app
            .createDevelopmentVersionReport(reportName = "Foos AppReport").expectSuccess()
            .updateDevelopmentVersionReport(name = "Foos AppReport", source = "", description = "Tadaa").expectSuccess()
        val developmentVersion = updatedApp.developmentVersion!!
        assertThat(developmentVersion.reports).contains(AppReport.create(name = "Foos AppReport", source = "", description = "Tadaa").expectSuccess())
    }

    @Test
    fun `removeDevelopmentVersionReport on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        app.removeDevelopmentVersionReport("Foos Report").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
    }

    @Test
    fun `removeDevelopmentVersionReport without draft`() {
        val app = createApp()
        app.removeDevelopmentVersionReport("Foos Report").expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
    }

    @Test
    fun `removeDevelopmentVersionReport with non existent datatype`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        app.removeDevelopmentVersionReport("Not-Existent").expectError(
            code = AppErrorCodes.REPORT_NOT_FOUND,
            details = null,
        )
    }

    @Test
    fun removeDevelopmentVersionReport() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val updatedApp = app.removeDevelopmentVersionReport("Report One").expectSuccess()
        val developmentVersion = updatedApp.developmentVersion!!
        assertThat(developmentVersion.reports).isEmpty()
    }

    @Test
    fun `releaseDevelopmentVersion on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        app.releaseDevelopmentVersion(AppVersionChangeType.FEATURE, "Some notes").expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
    }

    @Test
    fun `releaseDevelopmentVersion without draft`() {
        val app = createApp()
        app.releaseDevelopmentVersion(AppVersionChangeType.FEATURE, "Some notes").expectError(
            code = AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
    }

    @Test
    fun `releaseDevelopmentVersion with blank notes`() {
        val app = createApp().copy(versions = emptyList()).createDevelopmentVersion().expectSuccess()
        app.releaseDevelopmentVersion(AppVersionChangeType.FEATURE, " ").expectError(
            code = AppErrorCodes.VERSION_RELEASE_NOTE_BLANK,
            details = null,
        )
    }

    @Test
    fun `releaseDevelopmentVersion without latest version`() {
        val app = createApp().copy(versions = emptyList()).createDevelopmentVersion().expectSuccess()
        val releaseNotes = AppVersionReleaseNotes.create(AppVersionChangeType.FEATURE, "Some notes").expectSuccess()
        val updatedApp = app.releaseDevelopmentVersion(releaseNotes.changeType, releaseNotes.note).expectSuccess()
        assertThat(updatedApp.latestVersion).isNotNull
        assertThat(updatedApp.latestVersion!!.releaseNotes).isEqualTo(releaseNotes)
        assertThat(updatedApp.latestVersion!!.datatypes).isEmpty()
        assertThat(updatedApp.latestVersion!!.reports).isEmpty()
    }

    @Test
    fun releaseDevelopmentVersion() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val releaseNotes = AppVersionReleaseNotes.create(AppVersionChangeType.FEATURE, "Some notes").expectSuccess()
        val updatedApp = app.releaseDevelopmentVersion(releaseNotes.changeType, releaseNotes.note).expectSuccess()
        assertThat(updatedApp.latestVersion).isNotNull
        assertThat(updatedApp.latestVersion!!.releaseNotes).isEqualTo(releaseNotes)
        assertThat(updatedApp.latestVersion!!.datatypes).hasSize(1)
        assertThat(updatedApp.latestVersion!!.datatypes.first().name).isEqualTo("ModelOne")
        assertThat(updatedApp.latestVersion!!.datatypes.first().version).isEqualTo(1)
        assertThat(updatedApp.latestVersion!!.datatypes.first().schemaContent).isEqualTo("".toStringProperty())
        assertThat(updatedApp.latestVersion!!.datatypes.first().description).isNull()
        assertThat(updatedApp.latestVersion!!.reports).containsExactly(AppReport.create(name = "Report One", source = "", description = null).expectSuccess())
    }

    @Test
    fun `releaseDevelopmentVersion prepends in version list`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val releaseNotes = AppVersionReleaseNotes.create(AppVersionChangeType.FEATURE, "Some notes").expectSuccess()
        val updatedApp = app
            .releaseDevelopmentVersion(releaseNotes.changeType, releaseNotes.note).expectSuccess()
            .createDevelopmentVersion().expectSuccess()
            .releaseDevelopmentVersion(releaseNotes.changeType, releaseNotes.note).expectSuccess()
        assertThat(updatedApp.versions).hasSize(5)
        assertThat(updatedApp.latestVersion).isEqualTo(updatedApp.versions[0])
    }

    @Test
    fun `discontinue on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        app.discontinue().expectError(
            code = AppErrorCodes.DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
    }

    @Test
    fun discontinue() {
        val app = createApp()
        val updatedApp = app.discontinue().expectSuccess()
        assertThat(updatedApp.discontinued).isTrue
    }

    @Test
    fun `delete on not discontinued app`() {
        val app = createApp()
        app.verifyDeletion().expectError(
            code = AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED,
            details = null,
        )
    }

    @Test
    fun delete() {
        val app = createApp().copy(discontinued = true)
        app.verifyDeletion().expectSuccess()
    }

    private fun createApp() =
        App.create(
            name = UUID.randomUUID().toString(),
            developerId = UUID.randomUUID(),
            description = null,
        ).expectSuccess().let { app ->

            val version010 = AppVersion.create(
                releaseNotes = AppVersionReleaseNotes.create(
                    changeType = AppVersionChangeType.FEATURE,
                    note = "First feature!"
                ).expectSuccess(),
                developmentVersion = AppVersionDraft.create().expectSuccess(),
                latestVersion = null
            ).expectSuccess()

            val version011 = AppVersion.create(
                releaseNotes = AppVersionReleaseNotes.create(
                    changeType = AppVersionChangeType.BUGFIX,
                    note = "Something was wrong :("
                ).expectSuccess(),
                developmentVersion = AppVersionDraft.create().expectSuccess(),
                latestVersion = version010
            ).expectSuccess()

            val version020 = AppVersion.create(
                releaseNotes = AppVersionReleaseNotes.create(
                    changeType = AppVersionChangeType.FEATURE,
                    note = "Everything is new"
                ).expectSuccess(),
                developmentVersion = AppVersionDraft.create(
                    datatypes = setOf(
                        AppDatatypeDraft.create(name = "ModelOne", schemaContent = "".toStringProperty(), description = null).expectSuccess()
                    ),
                    reports = setOf(AppReport.create(name = "Report One", source = "", description = null).expectSuccess())
                ).expectSuccess(),
                latestVersion = version011
            ).expectSuccess()

            app.copy(versions = listOf(version020, version011, version010))
        }
}

class AppVersionDraftTests {

    @Test
    fun `create datatype name blank`() {
        AppDatatypeDraft.create(name = "", schemaContent = "", description = null).expectError(
            code = AppErrorCodes.DATATYPE_NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `create datatype name contains non letters`() {
        AppDatatypeDraft.create(name = "Foo Bar 27", schemaContent = "", description = null).expectError(
            code = AppErrorCodes.DATATYPE_NAME_INVALID,
            details = "'Foo Bar 27' does not match ([A-Z][a-z]*)+",
        )
    }

    @Test
    fun `create datatype name not starting uppercase`() {
        AppDatatypeDraft.create(name = "fooBar", schemaContent = "", description = null).expectError(
            code = AppErrorCodes.DATATYPE_NAME_INVALID,
            details = "'fooBar' does not match ([A-Z][a-z]*)+",
        )
    }

    @Test
    fun `upsert datatype`() {
        val updatedDraft = createEmptyDraft().upsertDatatype(
            AppDatatypeDraft.create(name = "FooBar", schemaContent = "", description = null).expectSuccess()
        ).expectSuccess()
        assertThat(updatedDraft.datatypes).contains(AppDatatypeDraft.create(name = "FooBar", schemaContent = "", description = null).expectSuccess())
    }

    @Test
    fun `upsert report name blank`() {
        AppReport.create(name = "", source = "", description = null).expectError(
            code = AppErrorCodes.REPORT_NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `upsert report`() {
        val updatedDraft = createEmptyDraft().upsertReport(
            AppReport.create(name = "Foos Report", source = "", description = null).expectSuccess()
        ).expectSuccess()
        assertThat(updatedDraft.reports).contains(AppReport.create(name = "Foos Report", source = "", description = null).expectSuccess())
    }

    private fun createEmptyDraft() = AppVersionDraft.create().expectSuccess()
}

class AppVersionReleaseNotesTests {

    @Test
    fun `first version as bugfix is 0-1-0`() {
        assertThat(createBugfix().computeVersion(null, createEmptyDraft())).isEqualTo(Semver(0, 1, 0))
    }

    @Test
    fun `first version as feature is 0-1-0`() {
        assertThat(createFeature().computeVersion(null, createEmptyDraft())).isEqualTo(Semver(0, 1, 0))
    }

    @Test
    fun `removing a datatype is breaking`() {
        val current = createAppVersion()
        val next = createEmptyDraft()
        assertThat(createFeature().isBreaking(current, next)).isTrue
    }

    @Test
    fun `renaming a datatype is breaking`() {
        val current = createAppVersion()
        val next = createEmptyDraft().copy(datatypes = current.datatypes.map {
            AppDatatypeDraft.create(
                name = it.name.reversed().replaceFirstChar { char -> char.uppercase() },
                schemaContent = it.schemaContent,
                description = it.description,
            ).expectSuccess()
        }.toSet())
        assertThat(createFeature().isBreaking(current, next)).isTrue
    }

    @Test
    fun `breaking schema change is delegated correctly`() {
        val current = createAppVersion()
        val next = createEmptyDraft().copy(datatypes = current.datatypes.map {
            AppDatatypeDraft.create(
                name = it.name,
                schemaContent = "",
                description = it.description,
            ).expectSuccess()
        }.toSet())
        assertThat(createFeature().isBreaking(current, next)).isTrue
    }

    private fun createBugfix() = AppVersionReleaseNotes.create(
        changeType = AppVersionChangeType.BUGFIX,
        note = UUID.randomUUID().toString()
    ).expectSuccess()

    private fun createFeature() = AppVersionReleaseNotes.create(
        changeType = AppVersionChangeType.FEATURE,
        note = UUID.randomUUID().toString()
    ).expectSuccess()

    private fun createAppVersion() =
        AppVersion.create(
            releaseNotes = AppVersionReleaseNotes.create(
                changeType = AppVersionChangeType.FEATURE,
                note = "Everything is new"
            ).expectSuccess(),
            developmentVersion = AppVersionDraft.create(
                datatypes = setOf(AppDatatypeDraft.create(
                    name = "ModelOne",
                    schemaContent = """ "properties": { "testPropertyName": { "type": "string" } } """.trimIndent(),
                    description = null
                ).expectSuccess()),
                reports = setOf(AppReport.create(name = "reportOne", source = "", description = null).expectSuccess()),
            ).expectSuccess(),
            latestVersion = null,
        ).expectSuccess()

    private fun createEmptyDraft() = AppVersionDraft.create().expectSuccess()
}
     */

    private fun verifyMocks(verifyBlock: (MockKVerificationScope.() -> Unit)? = null) {
        if (verifyBlock != null) {
            verifySequence(inverse = false, verifyBlock = verifyBlock)
        }
        confirmVerified(queryPersistence)
        confirmVerified(commandPersistence)
    }
}
