package de.chrgroth.james.api.restcrud

import io.ktor.http.*
import io.ktor.server.testing.*
import org.assertj.core.api.Assertions
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy

class AppControllerTest {

    // TODO MongoDBCOntainer tries to init a replica set after container started, but this fails due to auth! Does not seem to affect the test at all.
    private val mongoDbContainer = MongoDBContainer("mongo:4.4")
            .withCommand("--setParameter authenticationMechanisms=SCRAM-SHA-256 --auth")
            .withEnv("MONGO_INITDB_ROOT_USERNAME", "james")
            .withEnv("MONGO_INITDB_ROOT_PASSWORD", "semaj")
            .waitingFor(LogMessageWaitStrategy()
                    .withRegEx(".*Waiting for connections.*")
                    .withTimes(2)
            )

    @Before
    fun setup() {
        mongoDbContainer.apply {
            portBindings.add("27017:27017")
        }
        mongoDbContainer.start()
    }

    @Test
    fun testOk(): Unit {
        withTestApplication({ base(testing = true); restcrudRouting() }) {
            val listCall = handleRequest(HttpMethod.Get, "/api/apps")
            val response = listCall.response
            Assertions.assertThat(response).isNotNull
            Assertions.assertThat(response.status()).isEqualTo(HttpStatusCode.OK)
        }
    }

    @After
    fun teardown() {
        mongoDbContainer.stop()
    }
}