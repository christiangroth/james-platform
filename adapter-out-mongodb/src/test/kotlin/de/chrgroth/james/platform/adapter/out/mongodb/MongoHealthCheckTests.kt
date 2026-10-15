package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.MongoTimeoutException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoDatabase
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.bson.conversions.Bson
import org.eclipse.microprofile.health.HealthCheckResponse
import org.junit.jupiter.api.Test

class MongoHealthCheckTests {

  private val mongoClient: MongoClient = mockk()
  private val adminDatabase: MongoDatabase = mockk()
  private val meterRegistry = SimpleMeterRegistry()
  private val healthCheck = MongoHealthCheck(mongoClient, MongoQueryMetrics(meterRegistry, SLOW_QUERY_THRESHOLD_MS))

  init {
    every { mongoClient.getDatabase("admin") } returns adminDatabase
  }

  @Test
  fun `reports up and records the ping as a mongodb query`() {
    every { adminDatabase.runCommand(any<Bson>()) } returns Document("ok", 1.0)

    val response = healthCheck.call()

    assertThat(response.name).isEqualTo(MongoHealthCheck.NAME)
    assertThat(response.status).isEqualTo(HealthCheckResponse.Status.UP)
    assertThat(meterRegistry.find("mongodb.query").tag("operation", "admin.ping").timer()?.count()).isEqualTo(1L)
  }

  @Test
  fun `reports down and still records the failed ping`() {
    every { adminDatabase.runCommand(any<Bson>()) } throws MongoTimeoutException("timeout")

    val response = healthCheck.call()

    assertThat(response.status).isEqualTo(HealthCheckResponse.Status.DOWN)
    assertThat(meterRegistry.find("mongodb.query").tag("operation", "admin.ping").timer()?.count()).isEqualTo(1L)
  }

  companion object {
    private const val SLOW_QUERY_THRESHOLD_MS = 100L
  }
}
