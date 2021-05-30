package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #17 assert error details
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
        val result = app.createDevelopmentVersion()
        assertThat(result).isInstanceOf(Error::class.java)

        assertThat((result as Error).code).isEqualTo(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
    }

    @Test
    fun `createDevelopmentVersion with already existing draft`() {
        val app = createApp().copy(developmentVersion = AppVersionDraft())
        val result = app.createDevelopmentVersion()
        assertThat(result).isInstanceOf(Error::class.java)

        val errorCodeProvider = (result as Error).code
        assertThat(errorCodeProvider).isEqualTo(AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS)
    }

    @Test
    fun `createDevelopmentVersion without latest`() {
        val app = createApp().copy(versions = emptySet())
        val result = app.createDevelopmentVersion()
        assertThat(result).isInstanceOf(Result::class.java)

        val updatedApp = (result as Result).value
        assertThat(updatedApp.developmentVersion!!.datatypes).isEmpty()
        assertThat(updatedApp.developmentVersion!!.reports).isEmpty()
    }

    @Test
    fun `createDevelopmentVersion`() {
        val app = createApp()
        val result = app.createDevelopmentVersion()
        assertThat(result).isInstanceOf(Result::class.java)

        val updatedApp = (result as Result).value
        assertThat(updatedApp.developmentVersion!!.datatypes).containsExactly(AppDatatypeDraft(name = "modelOne"))
        assertThat(updatedApp.developmentVersion!!.reports).containsExactly(AppReport(name = "reportOne"))
    }

    @Test
    fun `updateDevelopmentVersion with datatype on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        val result = app.updateDevelopmentVersionDatatype(AppDatatypeDraft("Foos"))
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
    }

    @Test
    fun `updateDevelopmentVersion with datatype without draft`() {
        val app = createApp()
        val result = app.updateDevelopmentVersionDatatype(AppDatatypeDraft("Foos"))
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)
    }

    @Test
    fun `updateDevelopmentVersion with datatype`() {
        val app = (createApp().createDevelopmentVersion() as Result).value
        val result = app.updateDevelopmentVersionDatatype(AppDatatypeDraft("Foos"))
        assertThat(result).isInstanceOf(Result::class.java)
        val developmentVersion = (result as Result).value.developmentVersion!!
        assertThat(developmentVersion.datatypes).contains(AppDatatypeDraft("Foos"))
    }

    @Test
    fun `removeDevelopmentVersionDatatype on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        val result = app.removeDevelopmentVersionDatatype("Foos")
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
    }

    @Test
    fun `removeDevelopmentVersionDatatype without draft`() {
        val app = createApp()
        val result = app.removeDevelopmentVersionDatatype("Foos")
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)
    }

    @Test
    fun `removeDevelopmentVersionDatatype with non existent datatype`() {
        val app = (createApp().createDevelopmentVersion() as Result).value
        val result = app.removeDevelopmentVersionDatatype("Not-Existent")
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_DATATYPE_NOT_FOUND)
    }

    @Test
    fun `removeDevelopmentVersionDatatype`() {
        val app = (createApp().createDevelopmentVersion() as Result).value
        val result = app.removeDevelopmentVersionDatatype("modelOne")
        assertThat(result).isInstanceOf(Result::class.java)
        val developmentVersion = (result as Result).value.developmentVersion!!
        assertThat(developmentVersion.datatypes).isEmpty()
    }

    @Test
    fun `updateDevelopmentVersion with report on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        val result = app.updateDevelopmentVersionReport(AppReport("Foos Report"))
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
    }

    @Test
    fun `updateDevelopmentVersion with report without draft`() {
        val app = createApp()
        val result = app.updateDevelopmentVersionReport(AppReport("Foos AppReport"))
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)
    }

    @Test
    fun `updateDevelopmentVersion with report`() {
        val app = (createApp().createDevelopmentVersion() as Result).value
        val result = app.updateDevelopmentVersionReport(AppReport("Foos AppReport"))
        assertThat(result).isInstanceOf(Result::class.java)
        val developmentVersion = (result as Result).value.developmentVersion!!
        assertThat(developmentVersion.reports).contains(AppReport("Foos AppReport"))
    }

    @Test
    fun `removeDevelopmentVersionReport on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        val result = app.removeDevelopmentVersionReport("Foos Report")
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
    }

    @Test
    fun `removeDevelopmentVersionReport without draft`() {
        val app = createApp()
        val result = app.removeDevelopmentVersionReport("Foos Report")
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING)
    }

    @Test
    fun `removeDevelopmentVersionReport with non existent datatype`() {
        val app = (createApp().createDevelopmentVersion() as Result).value
        val result = app.removeDevelopmentVersionReport("Not-Existent")
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_REMOVE_REPORT_NOT_FOUND)
    }

    @Test
    fun `removeDevelopmentVersionReport`() {
        val app = (createApp().createDevelopmentVersion() as Result).value
        val result = app.removeDevelopmentVersionReport("reportOne")
        assertThat(result).isInstanceOf(Result::class.java)
        val developmentVersion = (result as Result).value.developmentVersion!!
        assertThat(developmentVersion.reports).isEmpty()
    }

    @Test
    fun `releaseDevelopmentVersion on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "Some notes")
        val result = app.releaseDevelopmentVersion(releaseNotes)
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
    }

    @Test
    fun `releaseDevelopmentVersion without draft`() {
        val app = createApp()
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "Some notes")
        val result = app.releaseDevelopmentVersion(releaseNotes)
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING)
    }

    @Test
    fun `releaseDevelopmentVersion with blank notes`() {
        val app = (createApp().copy(versions = emptySet()).createDevelopmentVersion() as Result).value
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, " ")
        val result = app.releaseDevelopmentVersion(releaseNotes)
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_RELEASE_NOTES_BLANK)
    }

    @Test
    fun `releaseDevelopmentVersion without latest version`() {
        val app = (createApp().copy(versions = emptySet()).createDevelopmentVersion() as Result).value
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "Some notes")
        val resultRelease = app.releaseDevelopmentVersion(releaseNotes)
        assertThat(resultRelease).isInstanceOf(Result::class.java)

        val updatedApp = (resultRelease as Result).value
        assertThat(updatedApp.latestVersion).isNotNull
        assertThat(updatedApp.latestVersion!!.releaseNotes).isEqualTo(releaseNotes)
        assertThat(updatedApp.latestVersion!!.datatypes).isEmpty()
        assertThat(updatedApp.latestVersion!!.reports).isEmpty()
    }

    @Test
    fun `releaseDevelopmentVersion`() {
        val app = (createApp().createDevelopmentVersion() as Result).value
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "Some notes")
        val resultRelease = app.releaseDevelopmentVersion(releaseNotes)
        assertThat(resultRelease).isInstanceOf(Result::class.java)

        val updatedApp = (resultRelease as Result).value
        assertThat(updatedApp.latestVersion).isNotNull
        assertThat(updatedApp.latestVersion!!.releaseNotes).isEqualTo(releaseNotes)
        assertThat(updatedApp.latestVersion!!.datatypes).containsExactly(AppDatatype(name = "modelOne", version = 1))
        assertThat(updatedApp.latestVersion!!.reports).containsExactly(AppReport(name = "reportOne"))
    }

    @Test
    fun `discontinue on discontinued app`() {
        val app = createApp().copy(discontinued = true)
        val result = app.discontinue()
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.APP_DISCONTINUED_NO_CHANGES_ALLOWED)
    }

    @Test
    fun `discontinue`() {
        val app = createApp()
        val result = app.discontinue()
        assertThat(result).isInstanceOf(Result::class.java)
        assertThat((result as Result).value.discontinued).isTrue
    }

    @Test
    fun `delete on not discontinued app`() {
        val app = createApp()
        val result = app.canBeDeleted()
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.DELETE_STATUS_IS_NOT_DISCONTINUED)
    }

    @Test
    fun `delete`() {
        val app = createApp().copy(discontinued = true)
        val result = app.canBeDeleted()
        assertThat(result).isInstanceOf(Result::class.java)
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
                    datatypes = setOf(AppDatatype(name = "modelOne", version = 1)),
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
        val result = createDraft().upsertDatatype(AppDatatypeDraft(name = ""))
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_BLANK)
    }

    @Test
    fun `upsert datatype name conains non letters`() {
        val result = createDraft().upsertDatatype(AppDatatypeDraft(name = "Foo Bar"))
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_NAME_LETTERS_ONLY)
    }

    @Test
    fun `upsert datatype`() {
        val result = createDraft().upsertDatatype(AppDatatypeDraft(name = "Foos"))
        assertThat(result).isInstanceOf(Result::class.java)
        assertThat((result as Result).value.datatypes).contains(AppDatatypeDraft(name = "Foos"))
    }

    @Test
    fun `upsert report name blank`() {
        val result = createDraft().upsertReport(AppReport(name = ""))
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_REPORT_NAME_BLANK)
    }

    @Test
    fun `upsert report`() {
        val result = createDraft().upsertReport(AppReport(name = "Foos Report"))
        assertThat(result).isInstanceOf(Result::class.java)
        assertThat((result as Result).value.reports).contains(AppReport(name = "Foos Report"))
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

    private fun createBugfix() = AppVersionReleaseNotes(
        changeType = AppVersionChangeType.BUGFIX,
        note = UUID.randomUUID().toString()
    )

    private fun createFeature() = AppVersionReleaseNotes(
        changeType = AppVersionChangeType.FEATURE,
        note = UUID.randomUUID().toString()
    )

    private fun createDraft() = AppVersionDraft()
}
