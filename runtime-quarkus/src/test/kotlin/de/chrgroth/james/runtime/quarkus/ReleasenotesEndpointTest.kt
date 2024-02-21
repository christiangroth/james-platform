package de.chrgroth.james.runtime.quarkus

import io.quarkus.test.common.http.TestHTTPEndpoint
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.`when`
import org.apache.http.HttpStatus
import org.junit.jupiter.api.Test


@QuarkusTest
@TestHTTPEndpoint(ReleasenotesEndpoint::class)
class GreetingResourceTest {

    @Test
    fun `verify response is successful, to we know releasenotes have been parsed`() {
        `when`().get().then().statusCode(HttpStatus.SC_OK)
    }
}
