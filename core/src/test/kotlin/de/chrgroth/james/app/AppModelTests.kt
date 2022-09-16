package de.chrgroth.james.app

import de.chrgroth.james.expectSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class AppStatusTests {

    @Test
    fun `app with at least one version is in active state`() {
        assertStatus(discontinued = false, includeVersion = true, expectedStatus = AppStatus.ACTIVE)
    }

    @Test
    fun `app without versions is in development state`() {
        assertStatus(discontinued = false, includeVersion = false, expectedStatus = AppStatus.DEVELOPMENT)
    }

    @Test
    fun `discontinued app with at least one version is in discontinued state`() {
        assertStatus(discontinued = true, includeVersion = true, expectedStatus = AppStatus.DISCONTINUED)
    }

    @Test
    fun `discontinued app without versions is in discontinued state`() {
        assertStatus(discontinued = true, includeVersion = false, expectedStatus = AppStatus.DISCONTINUED)
    }

    private fun assertStatus(discontinued: Boolean, includeVersion: Boolean, expectedStatus: AppStatus) {
        assertThat(createApp(discontinued, includeVersion).status).isEqualTo(expectedStatus)
    }

    private fun createApp(discontinued: Boolean, includeVersion: Boolean) =
        App.create(
            name = UUID.randomUUID().toString(),
            developerId = UUID.randomUUID(),
            description = null,
        ).expectSuccess().let { app ->
            if (includeVersion) {
                app.releaseDevelopmentVersion(AppVersionChangeType.FEATURE, "Some Release").expectSuccess()
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
