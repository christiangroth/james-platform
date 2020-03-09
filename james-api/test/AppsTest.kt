package de.chrgroth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.server.testing.TestApplicationCall
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.createTestEnvironment
import io.ktor.server.testing.handleRequest
import io.ktor.util.pipeline.PipelineInterceptor
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppsTest {

    private fun createDelegatingNotFoundAwareInterceptor(delegate: PipelineInterceptor<Unit, ApplicationCall>)
            : PipelineInterceptor<Unit, ApplicationCall> = {
        delegate.invoke(this, Unit)
        if (call.response.status() == null) {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    private val engine = TestApplicationEngine(createTestEnvironment())

    @BeforeTest
    fun setup() {
        engine.start(wait = false) // for now we can't eliminate it
        engine.application.module(true) // our main module function
        engine.callInterceptor = createDelegatingNotFoundAwareInterceptor(engine.callInterceptor)
    }

    @Test
    fun listApps() {
        assertHttpResponse(HttpMethod.Get, "/api/apps") { call ->
            val apps = call.response.content!!.asApps()
            assertEquals(2, apps.size)
        }
    }

    @Test
    fun getWelcomeApp() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/0") { call ->
            val app = call.response.content!!.asApp()
            assertEquals(0, app.id)
            assertEquals(2, app.name.values.size)
            assertEquals("Willkommen", app.name.values[Locale.GERMAN])
            assertEquals("Welcome", app.name.values[Locale.ENGLISH])
            assertEquals(3, app.versions.size)
        }
    }

    @Test
    fun getWelcomeAppVersions() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/0/versions") { call ->
            val versions = call.response.content!!.asAppVersions()
            assertEquals(3, versions.size)
        }
    }

    @Test
    fun getWelcomeAppVersion01() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/0/versions/0.1.0") { call ->
            val version = call.response.content!!.asAppVersion()
            assertEquals("foo", version.dummy)
        }
    }

    @Test
    fun getWelcomeAppVersion011() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/0/versions/0.1.1") { call ->
            val version = call.response.content!!.asAppVersion()
            assertEquals("bar", version.dummy)
        }
    }

    @Test
    fun getWelcomeAppVersion02() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/0/versions/0.2.0") { call ->
            val version = call.response.content!!.asAppVersion()
            assertEquals("baz", version.dummy)
        }
    }

    @Test
    fun getSportsApp() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/1") { call ->
            val app = call.response.content!!.asApp()
            assertEquals(1, app.id)
            assertEquals(2, app.name.values.size)
            assertEquals("Sport", app.name.values[Locale.GERMAN])
            assertEquals("Sports", app.name.values[Locale.ENGLISH])
            assertEquals(1, app.versions.size)
        }
    }

    @Test
    fun getSportsAppVersions() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/1/versions") { call ->
            val versions = call.response.content!!.asAppVersions()
            assertEquals(1, versions.size)
        }
    }

    @Test
    fun getSportsAppVersion01() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/1/versions/0.1.0") { call ->
            val version = call.response.content!!.asAppVersion()
            assertEquals("Running only!", version.dummy)
        }
    }

    @Test
    fun getSportsAppVersionNotExistent() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/1/versions/6.6.6", HttpStatusCode.NotFound)
    }

    @Test
    fun getAppNotExistent() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/666", HttpStatusCode.NotFound)
    }

    private fun assertHttpResponse(
        method: HttpMethod, uri: String,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        block: (TestApplicationCall) -> Unit = { }
    ) {
        with(engine) {
            handleRequest(method, uri).apply {
                val responseStatus = response.status()
                println(response.content)
                assertEquals(
                    expectedStatus, responseStatus,
                    "Request to $uri did respond with $responseStatus, but expected $expectedStatus"
                )
                apply(block)
            }
        }
    }

    private fun String.asApps() = jacksonObjectMapper().readValue<List<App>>(this)
    private fun String.asApp() = jacksonObjectMapper().readValue<App>(this)
    private fun String.asAppVersions() = jacksonObjectMapper().readValue<Map<String, AppVersion>>(this)
    private fun String.asAppVersion() = jacksonObjectMapper().readValue<AppVersion>(this)
}
