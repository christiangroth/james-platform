package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.model.infra.MongoQueryStats
import de.chrgroth.spotify.control.domain.port.out.infra.MongoStatsPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class MongoStatsAdapter(
  private val mongoClient: MongoClient,
  private val mongoQueryMetrics: MongoQueryMetrics,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : MongoStatsPort {

  override fun getCollectionStats(): List<MongoCollectionStats> {
    val database = mongoClient.getDatabase(databaseName)
    return database.listCollectionNames()
      .toList()
      .sorted()
      .mapNotNull { collectionName ->
        try {
          val stats = database.runCommand(Document("collStats", collectionName))
          MongoCollectionStats(
            name = collectionName,
            documentCount = (stats["count"] as? Number)?.toLong() ?: 0L,
            size = (stats["size"] as? Number)?.toLong() ?: 0L,
          )
        } catch (e: MongoException) {
          logger.warn(e) { "Failed to get stats for collection '$collectionName'" }
          null
        }
      }
  }

  override fun getQueryStats(): List<MongoQueryStats> = mongoQueryMetrics.getQueryStats()

  companion object : KLogging()
}
