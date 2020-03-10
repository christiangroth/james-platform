package de.chrgroth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.server.testing.*
import io.ktor.util.pipeline.PipelineInterceptor
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

const val CONTENT_TYPE_JSON = "application/json"

// TODO split tests and allow to start with a fresh and empty repository
internal class AppsTest2 {
    private val engine = TestApplicationEngine(createTestEnvironment())


    @BeforeTest
    fun setup() {
        engine.start(wait = false) // for now we can't eliminate it
        engine.application.module(true) // our main module function
    }
    @Test
    fun listApps() {
        println("oops")
    }
}
internal class AppsTest {

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
            assertTrue(apps.size >= 2)
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
        assertHttpResponse(HttpMethod.Get, "/api/apps/1/versions/6.6.6", expectedStatus = HttpStatusCode.NotFound)
    }

    @Test
    fun getAppNotExistent() {
        assertHttpResponse(HttpMethod.Get, "/api/apps/666", expectedStatus = HttpStatusCode.NotFound)
    }

    @Test
    fun createAppNoBody() {
        assertHttpResponse(
            HttpMethod.Post, "/api/apps",
            contentType = CONTENT_TYPE_JSON,
            expectedStatus = HttpStatusCode.BadRequest
        )
    }

    @Test
    fun createAppInvalidBody() {
        assertHttpResponse(
            HttpMethod.Post, "/api/apps",
            contentType = CONTENT_TYPE_JSON,
            body = """{
                "name" : {
                    "values" : {
                        "de" : "Test",
                        "en" : "Testing",
                        "it" : "Testo Italiano"
                    }
                }
            }""".trimMargin(),
            expectedStatus = HttpStatusCode.BadRequest
        )
    }

    @Test
    fun createAppValidBody() {
        assertHttpResponse(
            HttpMethod.Post, "/api/apps",
            contentType = CONTENT_TYPE_JSON,
            body = """{
                "name" : {
                    "values" : {
                        "de" : "Test",
                        "en" : "Testing",
                        "it" : "Testo Italiano"
                    }
                },
                "versions": { }
            }""".trimMargin(),
            expectedStatus = HttpStatusCode.Created
        ) { call ->
            val returnedApp = call.response.content!!.asApp()
            assertEquals(2, returnedApp.id)
            assertEquals("Test", returnedApp.name.values[Locale.GERMAN])
            assertEquals("Testing", returnedApp.name.values[Locale.ENGLISH])
            assertEquals("Testo Italiano", returnedApp.name.values[Locale.ITALIAN])
            assertEquals(0, returnedApp.versions.size)

            assertHttpResponse(HttpMethod.Get, "/api/apps/${returnedApp.id}") { call ->
                val detailApp = call.response.content!!.asApp()
                assertEquals(detailApp, returnedApp)
            }
        }
    }

    private fun assertHttpResponse(
        method: HttpMethod, uri: String,
        contentType: String? = null,
        body: String? = null,
        expectedStatus: HttpStatusCode = HttpStatusCode.OK,
        block: (TestApplicationCall) -> Unit = { }
    ) {
        with(engine) {
            handleRequest(method, uri) {
                if (contentType != null) {
                    addHeader(HttpHeaders.ContentType, contentType)
                }
                if (body != null) {
                    setBody(body)
                }
            }.apply {
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
