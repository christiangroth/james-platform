package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toNumberProperty
import de.chrgroth.james.toStringProperty
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

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
        App(
            id = UUID.randomUUID(),
            name = UUID.randomUUID().toString(),
            discontinued = discontinued,
            versions = if (includeVersion) {
                setOf(
                    AppVersion(
                        version = Semver(0, 1, 0),
                        releaseNotes = AppVersionReleaseNotes(
                            changeType = AppVersionChangeType.FEATURE,
                            note = "First feature!"
                        )
                    )
                )
            } else {
                emptySet()
            }
        )
}

class AppDevelopmentTests {

    @Test
    fun `does not resolves latest version if no versions are present`() {
        val app = createApp().copy(versions = emptySet())
        assertThat(app.latestVersion).isNull()
    }

    @Test
    fun `resolves latest version correctly`() {
        val app = createApp()
        assertThat(app.latestVersion).isNotNull
        assertThat(app.latestVersion!!.version).isEqualTo(Semver(1, 0, 0))
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
        val app = createApp().copy(developmentVersion = AppVersionDraft())
        app.createDevelopmentVersion().expectError(
            code = AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS,
            details = null,
        )
    }

    @Test
    fun `createDevelopmentVersion without latest`() {
        val app = createApp().copy(versions = emptySet())
        val updatedApp = app.createDevelopmentVersion().expectSuccess()
        assertThat(updatedApp.developmentVersion!!.datatypes).isEmpty()
        assertThat(updatedApp.developmentVersion!!.reports).isEmpty()
    }

    @Test
    fun `createDevelopmentVersion`() {
        val app = createApp()
        val updatedApp = app.createDevelopmentVersion().expectSuccess()
        assertThat(updatedApp.developmentVersion!!.datatypes).containsExactly(AppDatatypeDraft(name = "modelOne", schemaContent = ""))
        assertThat(updatedApp.developmentVersion!!.reports).containsExactly(AppReport(name = "reportOne"))
    }

    @Test
    fun `updateDevelopmentVersion with datatype on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        app.updateDevelopmentVersionDatatype(AppDatatypeDraft(name = "Foos", schemaContent = "")).expectError(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null
        )
    }

    @Test
    fun `updateDevelopmentVersion with datatype without draft`() {
        val app = createApp()
        app.updateDevelopmentVersionDatatype(AppDatatypeDraft(name = "Foos", schemaContent = "")).expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
    }

    @Test
    fun `updateDevelopmentVersion with datatype`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val updatedApp = app.updateDevelopmentVersionDatatype(AppDatatypeDraft(name = "Foos", schemaContent = "")).expectSuccess()
        val developmentVersion = updatedApp.developmentVersion!!
        assertThat(developmentVersion.datatypes).contains(AppDatatypeDraft(name = "Foos", schemaContent = ""))
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
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_DATATYPE_NOT_FOUND,
            details = null,
        )
    }

    @Test
    fun `removeDevelopmentVersionDatatype`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val updatedApp = app.removeDevelopmentVersionDatatype("modelOne").expectSuccess()
        val developmentVersion = updatedApp.developmentVersion!!
        assertThat(developmentVersion.datatypes).isEmpty()
    }

    @Test
    fun `updateDevelopmentVersion with report on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        app.updateDevelopmentVersionReport(AppReport("Foos Report")).expectError(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null
        )
    }

    @Test
    fun `updateDevelopmentVersion with report without draft`() {
        val app = createApp()
        app.updateDevelopmentVersionReport(AppReport("Foos AppReport")).expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
    }

    @Test
    fun `updateDevelopmentVersion with report`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val updatedApp = app.updateDevelopmentVersionReport(AppReport("Foos AppReport")).expectSuccess()
        val developmentVersion = updatedApp.developmentVersion!!
        assertThat(developmentVersion.reports).contains(AppReport("Foos AppReport"))
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
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_REPORT_NOT_FOUND,
            details = null,
        )
    }

    @Test
    fun `removeDevelopmentVersionReport`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val updatedApp = app.removeDevelopmentVersionReport("reportOne").expectSuccess()
        val developmentVersion = updatedApp.developmentVersion!!
        assertThat(developmentVersion.reports).isEmpty()
    }

    @Test
    fun `releaseDevelopmentVersion on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "Some notes")
        app.releaseDevelopmentVersion(releaseNotes).expectError(
            code = AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED,
            details = null,
        )
    }

    @Test
    fun `releaseDevelopmentVersion without draft`() {
        val app = createApp()
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "Some notes")
        app.releaseDevelopmentVersion(releaseNotes).expectError(
            code = AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING,
            details = null,
        )
    }

    @Test
    fun `releaseDevelopmentVersion with blank notes`() {
        val app = createApp().copy(versions = emptySet()).createDevelopmentVersion().expectSuccess()
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, " ")
        app.releaseDevelopmentVersion(releaseNotes).expectError(
            code = AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_RELEASE_NOTES_BLANK,
            details = null,
        )
    }

    @Test
    fun `releaseDevelopmentVersion without latest version`() {
        val app = createApp().copy(versions = emptySet()).createDevelopmentVersion().expectSuccess()
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "Some notes")
        val updatedApp = app.releaseDevelopmentVersion(releaseNotes).expectSuccess()
        assertThat(updatedApp.latestVersion).isNotNull
        assertThat(updatedApp.latestVersion!!.releaseNotes).isEqualTo(releaseNotes)
        assertThat(updatedApp.latestVersion!!.datatypes).isEmpty()
        assertThat(updatedApp.latestVersion!!.reports).isEmpty()
    }

    @Test
    fun `releaseDevelopmentVersion`() {
        val app = createApp().createDevelopmentVersion().expectSuccess()
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "Some notes")
        val updatedApp = app.releaseDevelopmentVersion(releaseNotes).expectSuccess()
        assertThat(updatedApp.latestVersion).isNotNull
        assertThat(updatedApp.latestVersion!!.releaseNotes).isEqualTo(releaseNotes)
        assertThat(updatedApp.latestVersion!!.datatypes).containsExactly(AppDatatype(name = "modelOne", version = 1, schemaContent = ""))
        assertThat(updatedApp.latestVersion!!.reports).containsExactly(AppReport(name = "reportOne"))
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
    fun `discontinue`() {
        val app = createApp()
        val updatedApp = app.discontinue().expectSuccess()
        assertThat(updatedApp.discontinued).isTrue
    }

    @Test
    fun `delete on not discontinued app`() {
        val app = createApp()
        app.canBeDeleted().expectError(
            code = AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED,
            details = null,
        )
    }

    @Test
    fun `delete`() {
        val app = createApp().copy(discontinued = true)
        app.canBeDeleted().expectSuccess()
    }

    private fun createApp() =
        App(
            id = UUID.randomUUID(),
            name = UUID.randomUUID().toString(),
            discontinued = false,
            versions = setOf(
                AppVersion(
                    version = Semver(1, 0, 0),
                    releaseNotes = AppVersionReleaseNotes(
                        changeType = AppVersionChangeType.FEATURE,
                        note = "Everything is new"
                    ),
                    datatypes = setOf(AppDatatype(name = "modelOne", version = 1, schemaContent = "")),
                    reports = setOf(AppReport(name = "reportOne")),
                ),
                AppVersion(
                    version = Semver(0, 1, 1),
                    releaseNotes = AppVersionReleaseNotes(
                        changeType = AppVersionChangeType.BUGFIX,
                        note = "Something was wrong :("
                    )
                ),
                AppVersion(
                    version = Semver(0, 1, 0),
                    releaseNotes = AppVersionReleaseNotes(
                        changeType = AppVersionChangeType.FEATURE,
                        note = "First feature!"
                    )
                ),
            )
        )
}

class AppVersionDraftTests {

    @Test
    fun `upsert datatype name blank`() {
        createDraft().upsertDatatype(AppDatatypeDraft(name = "", schemaContent = "")).expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `upsert datatype name conains non letters`() {
        createDraft().upsertDatatype(AppDatatypeDraft(name = "Foo Bar", schemaContent = "")).expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_LETTERS_ONLY,
            details = null,
        )
    }

    @Test
    fun `upsert datatype`() {
        val updatedDraft = createDraft().upsertDatatype(AppDatatypeDraft(name = "Foos", schemaContent = "")).expectSuccess()
        assertThat(updatedDraft.datatypes).contains(AppDatatypeDraft(name = "Foos", schemaContent = ""))
    }

    @Test
    fun `upsert report name blank`() {
        createDraft().upsertReport(AppReport(name = "")).expectError(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_REPORT_NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `upsert report`() {
        val updatedDraft = createDraft().upsertReport(AppReport(name = "Foos Report")).expectSuccess()
        assertThat(updatedDraft.reports).contains(AppReport(name = "Foos Report"))
    }

    private fun createDraft() = AppVersionDraft()
}

class AppVersionReleaseNotesTests {

    @Test
    fun `first version as bugfix is 0-1-0`() {
        assertThat(createBugfix().computeVersion(null, createDraft())).isEqualTo(Semver(0, 1, 0))
    }

    @Test
    fun `first version as feature is 0-1-0`() {
        assertThat(createFeature().computeVersion(null, createDraft())).isEqualTo(Semver(0, 1, 0))
    }

    @Test
    fun `removing a datatype is breaking`() {
        val current = createAppVersion()
        val next = createDraft()
        assertThat(createFeature().isBreaking(current, next)).isTrue
    }

    @Test
    fun `renaming a datatype is breaking`() {
        val current = createAppVersion()
        val next = createDraft().copy(datatypes = current.datatypes.map { AppDatatypeDraft(
            name = it.name.reversed(),
            schemaContent = it.schemaContent,
            description = it.description,
        )}.toSet() )
        assertThat(createFeature().isBreaking(current, next)).isTrue
    }

    @Test
    fun `breaking schema change is delegated correctly`() {
        val current = createAppVersion()
        val next = createDraft().copy(datatypes = current.datatypes.map { AppDatatypeDraft(
            name = it.name,
            schemaContent = "",
            description = it.description,
        )}.toSet() )
        assertThat(createFeature().isBreaking(current, next)).isTrue
    }

    private fun createBugfix() = AppVersionReleaseNotes(
        changeType = AppVersionChangeType.BUGFIX,
        note = UUID.randomUUID().toString()
    )

    private fun createFeature() = AppVersionReleaseNotes(
        changeType = AppVersionChangeType.FEATURE,
        note = UUID.randomUUID().toString()
    )

    private fun createAppVersion() =
        AppVersion(
            version = Semver(0, 1, 0),
            releaseNotes = AppVersionReleaseNotes(
                changeType = AppVersionChangeType.FEATURE,
                note = "Everything is new"
            ),
            datatypes = setOf(AppDatatype(
                name = "modelOne",
                version = 1,
                schemaContent = """ "properties": { "testPropertyName": { "type": "string" } } """.trimIndent()
            )),
            reports = setOf(AppReport(name = "reportOne")),
        )
    private fun createDraft() = AppVersionDraft()
}
