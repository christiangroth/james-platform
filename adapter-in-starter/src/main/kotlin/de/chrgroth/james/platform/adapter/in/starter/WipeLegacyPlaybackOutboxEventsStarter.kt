package de.chrgroth.james.platform.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import de.chrgroth.quarkus.starters.domain.Starter
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class WipeLegacyPlaybackOutboxEventsStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "WipeLegacyPlaybackOutboxEventsStarter-v1"

  override fun execute() {
    val result = mongoClient.getDatabase(databaseName)
      .getCollection(OUTBOX_COLLECTION)
      .deleteMany(Filters.`in`(EVENT_TYPE_FIELD, LEGACY_EVENT_TYPES))
    logger.info { "Deleted ${result.deletedCount} legacy playback outbox event(s) (FetchCurrentlyPlaying, FetchRecentlyPlayed)" }
  }

  companion object : KLogging() {
    private const val OUTBOX_COLLECTION = "outbox"
    private const val EVENT_TYPE_FIELD = "eventType"
    private val LEGACY_EVENT_TYPES = listOf("FetchCurrentlyPlaying", "FetchRecentlyPlayed")
  }
}
