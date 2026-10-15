package de.chrgroth.james.platform.adapter.out.mongodb

import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import jakarta.enterprise.context.ApplicationScoped
import org.bson.Document
import org.eclipse.microprofile.health.HealthCheck
import org.eclipse.microprofile.health.HealthCheckResponse
import org.eclipse.microprofile.health.Readiness

// Replaces Quarkus' built-in MongoDB health check (disabled via quarkus.mongodb.health.enabled=false): the built-in one
// created a second (reactive) MongoDB client used only for pings, and its pings were not recorded in mongodb.query.
@Readiness
@ApplicationScoped
@Suppress("Unused")
class MongoHealthCheck(
  private val mongoClient: MongoClient,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : HealthCheck {

  override fun call(): HealthCheckResponse {
    val builder = HealthCheckResponse.named(NAME)
    return try {
      mongoQueryMetrics.timed("admin.ping") { mongoClient.getDatabase(ADMIN_DATABASE).runCommand(Document(PING_COMMAND, 1)) }
      builder.up().build()
    } catch (e: MongoException) {
      builder.down().withData("error", e.message ?: e.javaClass.simpleName).build()
    }
  }

  companion object {
    const val NAME = "MongoDB connection health check"
    private const val ADMIN_DATABASE = "admin"
    private const val PING_COMMAND = "ping"
  }
}
