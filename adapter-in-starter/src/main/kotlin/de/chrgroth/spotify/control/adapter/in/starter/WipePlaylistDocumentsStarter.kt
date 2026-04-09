package de.chrgroth.spotify.control.adapter.`in`.starter

import com.mongodb.client.MongoClient
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.bson.Document
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class WipePlaylistDocumentsStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "WipePlaylistDocumentsStarter-v1"

  override fun execute() {
    val result = mongoClient.getDatabase(databaseName)
      .getCollection(PLAYLIST_COLLECTION)
      .deleteMany(Document())
    logger.info { "Deleted ${result.deletedCount} legacy playlist documents" }
  }

  companion object : KLogging() {
    private const val PLAYLIST_COLLECTION = "spotify_playlist"
  }
}
