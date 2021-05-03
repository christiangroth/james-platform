package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Maybe
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
    fun `createDevelopmentVersion with already existing draft`() {
        val app = createApp().copy(developmentVersion = AppVersionDraft())
        val developmentResult = app.createDevelopmentVersion()
        assertThat(developmentResult).isInstanceOf(Maybe.Error::class.java)

        val errorCodeProvider = (developmentResult as Maybe.Error).code
        assertThat(errorCodeProvider).isEqualTo(AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS)
    }

    @Test
    fun `createDevelopmentVersion without latest`() {
        val app = createApp().copy(versions = emptySet())
        val developmentResult = app.createDevelopmentVersion()
        assertThat(developmentResult).isInstanceOf(Maybe.Result::class.java)

        val developmentVersion = (developmentResult as Maybe.Result).value
        assertThat(developmentVersion.datatypes).isEmpty()
        assertThat(developmentVersion.reports).isEmpty()
    }

    @Test
    fun `createDevelopmentVersion`() {
        val app = createApp()
        val developmentResult = app.createDevelopmentVersion()
        assertThat(developmentResult).isInstanceOf(Maybe.Result::class.java)

        val developmentVersion = (developmentResult as Maybe.Result).value
        assertThat(developmentVersion.datatypes).containsExactly(AppDatatype(name = "modelOne", version = 1))
        assertThat(developmentVersion.reports).containsExactly(AppReport(name = "reportOne"))
    }

    @Test
    fun `releaseDevelopmentVersion without draft`() {
        val app = createApp()
        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "")
        val releaseResult = app.releaseDevelopmentVersion(releaseNotes)
        assertThat(releaseResult).isInstanceOf(Maybe.Error::class.java)

        assertThat((releaseResult as Maybe.Error).code).isEqualTo(AppErrorCodes.RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING)
    }

    @Test
    fun `releaseDevelopmentVersion without latest version`() {
        val rawApp = createApp().copy(versions = emptySet())
        val developmentResult = rawApp.createDevelopmentVersion() as Maybe.Result
        val app = rawApp.copy(developmentVersion = developmentResult.value)

        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "")
        val releaseResult = app.releaseDevelopmentVersion(releaseNotes)
        assertThat(releaseResult).isInstanceOf(Maybe.Result::class.java)

        val nextVersion = (releaseResult as Maybe.Result).value
        assertThat(nextVersion.releaseNotes).isEqualTo(releaseNotes)
        assertThat(nextVersion.datatypes).isEmpty()
        assertThat(nextVersion.reports).isEmpty()
    }

    @Test
    fun `releaseDevelopmentVersion`() {
        val rawApp = createApp()
        val developmentResult = rawApp.createDevelopmentVersion() as Maybe.Result
        val app = rawApp.copy(developmentVersion = developmentResult.value)

        val releaseNotes = AppVersionReleaseNotes(AppVersionChangeType.FEATURE, "")
        val releaseResult = app.releaseDevelopmentVersion(releaseNotes)
        assertThat(releaseResult).isInstanceOf(Maybe.Result::class.java)

        val nextVersion = (releaseResult as Maybe.Result).value
        assertThat(nextVersion.releaseNotes).isEqualTo(releaseNotes)
        assertThat(nextVersion.datatypes).containsExactly(AppDatatype(name = "modelOne", version = 1))
        assertThat(nextVersion.reports).containsExactly(AppReport(name = "reportOne"))
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

class AppVersionReleaseNotesTests {

    @Test
    fun `first version as bugfix is 0-1-0`() {
        assertThat(createBugfix().computeVersion(null, createDraft())).isEqualTo(Semver(0, 1, 0))
    }

    @Test
    fun `first version as feature is 0-1-0`() {
        assertThat(createFeature().computeVersion(null, createDraft())).isEqualTo(Semver(0, 1, 0))
    }

    // TODO tests for isBreaking()

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
