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

        val maybeResult = app.createDevelopmentVersion()
        assertThat(maybeResult).isInstanceOf(Maybe.Error::class.java)
        val errorCodeProvider = (maybeResult as Maybe.Error).code
        assertThat(errorCodeProvider).isEqualTo(AppErrorCodes.CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS)
    }

    @Test
    fun `createDevelopmentVersion without latest`() {
        val app = createApp().copy(versions = emptySet())

        val maybeResult = app.createDevelopmentVersion()
        assertThat(maybeResult).isInstanceOf(Maybe.Result::class.java)

        val developmentVersion = (maybeResult as Maybe.Result).value
        assertThat(developmentVersion.models).isEmpty()
        assertThat(developmentVersion.reports).isEmpty()
    }

    @Test
    fun `createDevelopmentVersion`() {
        val app = createApp()

        val maybeResult = app.createDevelopmentVersion()
        assertThat(maybeResult).isInstanceOf(Maybe.Result::class.java)

        val developmentVersion = (maybeResult as Maybe.Result).value
        assertThat(developmentVersion.models).containsExactly(AppModel(name = "modelOne", version = 1))
        assertThat(developmentVersion.reports).containsExactly(AppReport(name = "reportOne"))
    }

    // TODO releaseDevelopmentVersion
    // TODO releaseDevelopmentVersion without draft
    // TODO releaseDevelopmentVersion without latest

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
                    models = setOf(AppModel(name = "modelOne", version = 1)),
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
