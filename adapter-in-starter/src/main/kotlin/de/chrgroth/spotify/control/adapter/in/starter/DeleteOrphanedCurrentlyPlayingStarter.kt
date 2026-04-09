package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class DeleteOrphanedCurrentlyPlayingStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "DeleteOrphanedCurrentlyPlayingStarter-v1"

  override fun execute() {
    val result = mongoClient.getDatabase(databaseName)
      .getCollection(CURRENTLY_PLAYING_COLLECTION)
      .deleteMany(Document())
    logger.info { "Deleted ${result.deletedCount} orphaned currently playing documents" }
  }

  companion object : KLogging() {
    private const val CURRENTLY_PLAYING_COLLECTION = "spotify_currently_playing"
  }
}
