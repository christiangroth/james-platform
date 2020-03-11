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

const val CONTENT_TYPE_JSON = "application/json"

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
                "versions": { 
                    "0.1": { "dummy": "dummy" }
                }
            }""".trimMargin(),
            expectedStatus = HttpStatusCode.Created
        ) { createAppCall ->
            val returnedApp = createAppCall.response.content!!.asApp()
            assertEquals(0, returnedApp.id)
            assertEquals("Test", returnedApp.name.values[Locale.GERMAN])
            assertEquals("Testing", returnedApp.name.values[Locale.ENGLISH])
            assertEquals("Testo Italiano", returnedApp.name.values[Locale.ITALIAN])
            assertEquals(1, returnedApp.versions.size)
            assertEquals("dummy", returnedApp.versions["0.1"]?.dummy)

            assertHttpResponse(HttpMethod.Get, "/api/apps") { call ->
                val apps = call.response.content!!.asApps()
                assertEquals(apps.size, 1)
            }

            assertHttpResponse(HttpMethod.Get, "/api/apps/${returnedApp.id}") { call ->
                val detailApp = call.response.content!!.asApp()
                assertEquals(detailApp, returnedApp)
            }

            assertHttpResponse(HttpMethod.Get, "/api/apps/${returnedApp.id}/versions") { call ->
                val detailAppVersions = call.response.content!!.asAppVersions()
                assertEquals(detailAppVersions, returnedApp.versions)
            }

            assertHttpResponse(HttpMethod.Get, "/api/apps/${returnedApp.id}/versions/0.1") { call ->
                val detailAppVersion = call.response.content!!.asAppVersion()
                assertEquals("dummy", detailAppVersion.dummy)
            }

            assertHttpResponse(
                HttpMethod.Put, "/api/apps/${returnedApp.id}/versions/0.2",
                contentType = CONTENT_TYPE_JSON,
                body = """{ "dummy": "new" }""".trimMargin(),
                expectedStatus = HttpStatusCode.Created
            ) { createVersionCall ->
                val updatedApp = createVersionCall.response.content!!.asApp()
                assertEquals(returnedApp.id, updatedApp.id)
                assertEquals(returnedApp.name, updatedApp.name)
                assertEquals(2, updatedApp.versions.size)
                assertEquals("dummy", updatedApp.versions["0.1"]?.dummy)
                assertEquals("new", updatedApp.versions["0.2"]?.dummy)

                assertHttpResponse(
                    HttpMethod.Delete, "/api/apps/${returnedApp.id}/versions/0.2",
                    expectedStatus = HttpStatusCode.NoContent
                ) { deleteVersionCall ->
                    val updatedApp = deleteVersionCall.response.content!!.asApp()
                    assertEquals(returnedApp.id, updatedApp.id)
                    assertEquals(returnedApp.name, updatedApp.name)
                    assertEquals(1, updatedApp.versions.size)
                    assertEquals("dummy", updatedApp.versions["0.1"]?.dummy)
                }
            }

            assertHttpResponse(
                    HttpMethod.Put, "/api/apps/${returnedApp.id}/versions/0.1",
            contentType = CONTENT_TYPE_JSON,
            body = """{ "dummy": "overwritten" }""".trimMargin(),
            expectedStatus = HttpStatusCode.Created
            ) { createVersionCall ->
                val updatedApp = createVersionCall.response.content!!.asApp()
                assertEquals(returnedApp.id, updatedApp.id)
                assertEquals(returnedApp.name, updatedApp.name)
                assertEquals(1, updatedApp.versions.size)
                assertEquals("overwritten", updatedApp.versions["0.1"]?.dummy)
            }

            assertHttpResponse(
                HttpMethod.Get, "/api/apps/${returnedApp.id}/versions/nonExistent",
                expectedStatus = HttpStatusCode.NotFound
            )

            assertHttpResponse(
                HttpMethod.Delete, "/api/apps/${returnedApp.id}/versions/nonExistent",
                expectedStatus = HttpStatusCode.NoContent
            ) { deleteVersionCall ->
                val updatedApp = deleteVersionCall.response.content!!.asApp()
                assertEquals(returnedApp.id, updatedApp.id)
                assertEquals(returnedApp.name, updatedApp.name)
                assertEquals(1, updatedApp.versions.size)
                assertEquals("overwritten", updatedApp.versions["0.1"]?.dummy)
            }

            // TODO overwrite nonexistent
            // TODO overwrite existent
            // TODO overwrite existent non matching ids
            // TODO overwrite existent missing id
            // TODO delete nonexistent
            // TODO delete
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
