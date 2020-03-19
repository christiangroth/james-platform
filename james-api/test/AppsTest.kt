import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import de.chrgroth.App
import de.chrgroth.AppVersion
import de.chrgroth.module
import de.chrgroth.semVerModule
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.server.testing.*
import io.ktor.util.pipeline.PipelineInterceptor
import net.swiftzer.semver.SemVer
import org.bson.types.ObjectId
import org.litote.kmongo.id.jackson.IdJacksonModule
import org.litote.kmongo.id.toId
import java.util.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

const val CONTENT_TYPE_JSON = "application/json"

// TODO maybe this should be a GenericCrudControllerTest
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
        assertHttpResponse(
            HttpMethod.Get,
            "/api/apps/${ObjectId().toId<App>()}",
            expectedStatus = HttpStatusCode.NotFound
        )
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

    // TODO break down into pieces
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
                "versions": [ 
                    { "version" : "0.1", "dummy": "dummy" }
                ]
            }""".trimMargin(),
            expectedStatus = HttpStatusCode.Created
        ) { createAppCall ->
            val returnedApp = createAppCall.response.content!!.asApp()
            assertNotNull(returnedApp._id)
            assertEquals("Test", returnedApp.name.values[Locale.GERMAN])
            assertEquals("Testing", returnedApp.name.values[Locale.ENGLISH])
            assertEquals("Testo Italiano", returnedApp.name.values[Locale.ITALIAN])
            assertEquals(1, returnedApp.versions.size)
            assertEquals("dummy", returnedApp.versionBySemVer(SemVer.parse("0.1"))?.dummy)

            assertHttpResponse(HttpMethod.Get, "/api/apps") { call ->
                val apps = call.response.content!!.asApps()
                assertEquals(apps.size, 1)
            }

            assertHttpResponse(HttpMethod.Get, "/api/apps/${returnedApp._id}") { call ->
                val detailApp = call.response.content!!.asApp()
                assertEquals(detailApp, returnedApp)
            }

            assertHttpResponse(HttpMethod.Get, "/api/apps/${returnedApp._id}/versions") { call ->
                val detailAppVersions = call.response.content!!.asAppVersions()
                assertEquals(detailAppVersions, returnedApp.versions)
            }

            assertHttpResponse(HttpMethod.Get, "/api/apps/${returnedApp._id}/versions/0.1") { call ->
                val detailAppVersion = call.response.content!!.asAppVersion()
                assertEquals("dummy", detailAppVersion.dummy)
            }

            assertHttpResponse(
                HttpMethod.Put, "/api/apps/${returnedApp._id}/versions/0.2",
                contentType = CONTENT_TYPE_JSON,
                body = """{ "version": "0.2", "dummy": "new" }""".trimMargin(),
                expectedStatus = HttpStatusCode.Created
            ) { createVersionCall ->
                val updatedAppVersion = createVersionCall.response.content!!.asAppVersion()
                assertEquals(SemVer.parse("0.2"), updatedAppVersion.version)
                assertEquals("new", updatedAppVersion.dummy)

                assertHttpResponse(
                    HttpMethod.Delete, "/api/apps/${returnedApp._id}/versions/0.2",
                    expectedStatus = HttpStatusCode.NoContent
                )

                assertHttpResponse(
                    HttpMethod.Get, "/api/apps/${returnedApp._id}/versions/0.2",
                    expectedStatus = HttpStatusCode.NotFound
                )
            }

            assertHttpResponse(
                HttpMethod.Put, "/api/apps/${returnedApp._id}/versions/0.1",
                contentType = CONTENT_TYPE_JSON,
                body = """{ "version": "0.1", "dummy": "overwritten" }""".trimMargin(),
                expectedStatus = HttpStatusCode.OK
            ) { createVersionCall ->
                val updatedAppVersion = createVersionCall.response.content!!.asAppVersion()
                assertEquals(SemVer.parse("0.1"), updatedAppVersion.version)
                assertEquals("overwritten", updatedAppVersion.dummy)
            }

            assertHttpResponse(
                HttpMethod.Get, "/api/apps/${returnedApp._id}/versions/invalidFormat",
                expectedStatus = HttpStatusCode.BadRequest
            )

            assertHttpResponse(
                HttpMethod.Put, "/api/apps/${returnedApp._id}/versions/invalidFormat",
                expectedStatus = HttpStatusCode.BadRequest
            )

            assertHttpResponse(
                HttpMethod.Delete, "/api/apps/${returnedApp._id}/versions/invalidFormat",
                expectedStatus = HttpStatusCode.BadRequest
            )

            assertHttpResponse(
                HttpMethod.Get, "/api/apps/${returnedApp._id}/versions/9.9.9",
                expectedStatus = HttpStatusCode.NotFound
            )

            assertHttpResponse(
                HttpMethod.Delete, "/api/apps/${returnedApp._id}/versions/9.9.9",
                expectedStatus = HttpStatusCode.NotFound
            )

            assertHttpResponse(
                HttpMethod.Put, "/api/apps/invalidId",
                contentType = CONTENT_TYPE_JSON,
                body = """ { "_id": "invalidId", "name" : { "values" : { "en" : "Inserted" } }, "versions": [ ] } """.trimIndent(),
                expectedStatus = HttpStatusCode.BadRequest
            )

            val newAppObjectId = ObjectId().toId<App>()
            assertHttpResponse(
                HttpMethod.Put, "/api/apps/$newAppObjectId",
                contentType = CONTENT_TYPE_JSON,
                body = """ { "_id": "$newAppObjectId", "name" : { "values" : { "en" : "Inserted" } }, "versions": [ ] } """.trimIndent(),
                expectedStatus = HttpStatusCode.Created
            ) { putAppCall ->
                val detailApp = putAppCall.response.content!!.asApp()
                assertEquals(newAppObjectId, detailApp._id)

                assertHttpResponse(HttpMethod.Get, "/api/apps/${detailApp._id}") { call ->
                    val doubleCheckApp = call.response.content!!.asApp()
                    assertEquals(doubleCheckApp, detailApp)
                }

                assertHttpResponse(
                    HttpMethod.Delete, "/api/apps/${detailApp._id}",
                    expectedStatus = HttpStatusCode.NoContent
                ) { _ ->

                    assertHttpResponse(
                        HttpMethod.Get, "/api/apps/${detailApp._id}",
                        expectedStatus = HttpStatusCode.NotFound
                    )

                    assertHttpResponse(HttpMethod.Get, "/api/apps") { call ->
                        val apps = call.response.content!!.asApps()
                        assertEquals(1, apps.size)
                        assertEquals(returnedApp._id, apps.first()._id)
                    }
                }
            }

            assertHttpResponse(
                HttpMethod.Put, "/api/apps/${returnedApp._id}",
                contentType = CONTENT_TYPE_JSON,
                body = """ { "_id": "$newAppObjectId", "name" : { "values" : { "en" : "Overwritten" } }, "versions": [ ] } """.trimIndent(),
                expectedStatus = HttpStatusCode.BadRequest
            )

            assertHttpResponse(
                HttpMethod.Put, "/api/apps/${returnedApp._id}",
                contentType = CONTENT_TYPE_JSON,
                body = """ { "name" : { "values" : { "en" : "Overwritten" } }, "versions": [ ] } """.trimIndent(),
                expectedStatus = HttpStatusCode.BadRequest
            )

            assertHttpResponse(
                HttpMethod.Put, "/api/apps/${returnedApp._id}",
                contentType = CONTENT_TYPE_JSON,
                body = """ { "_id": "${returnedApp._id}", "name" : { "values" : { "en" : "Overwritten Again" } }, "versions": [ ] } """.trimIndent(),
                expectedStatus = HttpStatusCode.OK
            ) { putAppCall ->
                val detailApp = putAppCall.response.content!!.asApp()
                assertNotNull(detailApp._id)

                assertHttpResponse(HttpMethod.Get, "/api/apps/${detailApp._id}") { call ->
                    val doubleCheckApp = call.response.content!!.asApp()
                    assertEquals(doubleCheckApp, detailApp)
                }
            }

            assertHttpResponse(
                HttpMethod.Delete, "/api/apps/invalidId",
                expectedStatus = HttpStatusCode.BadRequest
            )

            val nonExistentId = ObjectId().toId<App>()
            assertHttpResponse(
                HttpMethod.Delete, "/api/apps/$nonExistentId",
                expectedStatus = HttpStatusCode.NotFound
            )
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

    private fun String.asApps() = objectMapper().readValue<List<App>>(this)
    private fun String.asApp() = objectMapper().readValue<App>(this)
    private fun String.asAppVersions() = objectMapper().readValue<List<AppVersion>>(this)
    private fun String.asAppVersion() = objectMapper().readValue<AppVersion>(this)

    private fun objectMapper() = jacksonObjectMapper().registerModule(semVerModule).registerModule(IdJacksonModule())
}
