package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toStringProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #25 switch to testing of Command

// TODO #25 test exceptions on constructor invocation with invalid data

// TODO #25 test changeVersionReleaseNote
// TODO #25 test Datatype name pattern

class AppStatusTests {

    @Test
    fun `not discontinued app with at least one version is in active state`() {
        val app = createApp(discontinued = false, includeVersion = true)
        assertThat(app.status).isEqualTo(AppStatus.ACTIVE)
    }

    @Test
    fun `not discontinued app without versions is in development state`() {
        val app = createApp(discontinued = false, includeVersion = false)
        assertThat(app.status).isEqualTo(AppStatus.DEVELOPMENT)
    }

    @Test
    fun `discontinued app with at least one version is in discontinued state`() {
        val app = createApp(discontinued = true, includeVersion = true)
        assertThat(app.status).isEqualTo(AppStatus.DISCONTINUED)
    }

    @Test
    fun `discontinued app without versions is in discontinued state`() {
        val app = createApp(discontinued = true, includeVersion = false)
        assertThat(app.status).isEqualTo(AppStatus.DISCONTINUED)
    }

    private fun createApp(discontinued: Boolean, includeVersion: Boolean) =
        App.create(
            name = UUID.randomUUID().toString(),
            developerId = UUID.randomUUID(),
            description = null,
        ).expectSuccess().let { app ->
            if (includeVersion) {
                app.copy(versions = listOf(
                    AppVersion.create(
                        releaseNotes = AppVersionReleaseNotes.create(
                            changeType = AppVersionChangeType.FEATURE,
                            note = "First feature!"
                        ).expectSuccess(),
                        developmentVersion = AppVersionDraft.create().expectSuccess(),
                        latestVersion = null,
                    ).expectSuccess()
                ))
            } else {
                app
            }
        }.let { app ->
            if (discontinued) {
                app.discontinue().expectSuccess()
            } else {
                app
            }
        }
}

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
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
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
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
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
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
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
            code = AppErrorCodes.APP_DATATYPE_NOT_FOUND,
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
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
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
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
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
            code = AppErrorCodes.APP_REPORT_NOT_FOUND,
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
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
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
            code = AppErrorCodes.APP_VERSION_RELEASE_NOTE_BLANK,
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
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
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
            code = AppErrorCodes.APP_DATATYPE_NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `create datatype name contains non letters`() {
        AppDatatypeDraft.create(name = "Foo Bar 27", schemaContent = "", description = null).expectError(
            code = AppErrorCodes.APP_DATATYPE_NAME_INVALID,
            details = "'Foo Bar 27' does not match ([A-Z][a-z]*)+",
        )
    }

    @Test
    fun `create datatype name not starting uppercase`() {
        AppDatatypeDraft.create(name = "fooBar", schemaContent = "", description = null).expectError(
            code = AppErrorCodes.APP_DATATYPE_NAME_INVALID,
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
            code = AppErrorCodes.APP_REPORT_NAME_BLANK,
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
