package de.chrgroth.james.app

import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainError
import de.chrgroth.james.expectDomainErrors
import de.chrgroth.james.expectSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class AppModelTests {

  @Test
  fun `app with at least one version is in active state`() {
    assertStatus(discontinued = false, releaseDevelopmentVersion = true, expectedStatus = AppStatus.ACTIVE)
  }

  @Test
  fun `app without versions is in development state`() {
    assertStatus(discontinued = false, releaseDevelopmentVersion = false, expectedStatus = AppStatus.DEVELOPMENT)
  }

  @Test
  fun `discontinued app with at least one version is in discontinued state`() {
    assertStatus(discontinued = true, releaseDevelopmentVersion = true, expectedStatus = AppStatus.DISCONTINUED)
  }

  @Test
  fun `discontinued app without versions is in discontinued state`() {
    assertStatus(discontinued = true, releaseDevelopmentVersion = false, expectedStatus = AppStatus.DISCONTINUED)
  }

  private fun assertStatus(discontinued: Boolean, releaseDevelopmentVersion: Boolean, expectedStatus: AppStatus) {
    assertThat(createApp(discontinued, releaseDevelopmentVersion).status).isEqualTo(expectedStatus)
  }

  @Test
  fun `does not resolve latest version if no versions are present`() {
    assertThat(createApp(discontinued = false, releaseDevelopmentVersion = false).latestVersion).isNull()
  }

  @Test
  fun `resolves latest version correctly for single version`() {
    assertThat(createApp(discontinued = false, releaseDevelopmentVersion = true).latestVersion).isNotNull
    assertThat(createApp(discontinued = false, releaseDevelopmentVersion = true).latestVersion!!.version).isEqualTo(Semver("0.1.0"))
  }

  private fun createApp(discontinued: Boolean, releaseDevelopmentVersion: Boolean) =
    App.create(
      name = UUID.randomUUID().toString(),
      developerId = UUID.randomUUID(),
      description = null,
    ).expectSuccess().let { app ->
      if (releaseDevelopmentVersion) {
        app
          .addNextVersionDraftDatatype("SomeChange").expectSuccess()
          .changeNextVersionReleaseNoteTitle("Some Release").expectSuccess()
          .changeNextVersionReleaseNoteFeatures(listOf("Some feature")).expectSuccess()
          .releaseNextVersionDraft().expectSuccess()
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

class AppVersionReleaseNotesModelTests {

  @Test
  fun `release notes containing feature information only resolve to type FEATURE`() {
    val releaseNotes = AppVersionReleaseNotes.create(
      title = "Title",
      notes = null,
      features = listOf(element = "feature one"),
      bugfixes = emptyList(),
      misc = emptyList()
    ).expectSuccess()
    assertThat(releaseNotes.changeType).isEqualTo(AppVersionChangeType.FEATURE)
  }

  @Test
  fun `release notes containing bugfix information only resolve to type BUGFIX`() {
    val releaseNotes = AppVersionReleaseNotes.create(
      title = "Title",
      notes = null,
      features = emptyList(),
      bugfixes = listOf("bugfix one"),
      misc = emptyList()
    ).expectSuccess()
    assertThat(releaseNotes.changeType).isEqualTo(AppVersionChangeType.BUGFIX)
  }

  @Test
  fun `release notes containing feature and bugfix information resolve to type FEATURE`() {
    val releaseNotes = AppVersionReleaseNotes.create(
      title = "Title",
      notes = null,
      features = listOf("feature one"),
      bugfixes = listOf("bugfix one"),
      misc = emptyList()
    ).expectSuccess()
    assertThat(releaseNotes.changeType).isEqualTo(AppVersionChangeType.FEATURE)
  }

  @Test
  fun `release notes containing neither feature nor bugfix information cannot be created`() {
    AppVersionReleaseNotes.create(
      title = "Title",
      notes = null,
      features = emptyList(),
      bugfixes = emptyList(),
      misc = emptyList()
    ).expectDomainErrors(
      DomainError(code = AppDomainErrorCodes.VERSION_RELEASE_NOTE_FEATURES_OR_BUGFIXES)
    )
  }
}
