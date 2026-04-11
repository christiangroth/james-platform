package de.chrgroth.james.platform.adapter.`in`.starter

import com.mongodb.client.MongoClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.james.platform.domain.model.playlist.PlaylistType
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty

@ApplicationScoped
@Suppress("Unused")
class MigrateSingularityPlaylistTypeStarter(
  private val mongoClient: MongoClient,
  @param:ConfigProperty(name = "quarkus.mongodb.database")
  private val databaseName: String,
) : Starter {

  override val id = "MigrateSingularityPlaylistTypeStarter-v1"

  override fun execute() {
    val result = mongoClient.getDatabase(databaseName)
      .getCollection(PLAYLIST_METADATA_COLLECTION)
      .updateMany(
        Filters.and(
          Filters.eq(NAME_FIELD, PlaylistType.SINGULARITY_PLAYLIST_NAME),
          Filters.eq(TYPE_FIELD, OLD_TYPE_VALUE),
        ),
        Updates.set(TYPE_FIELD, NEW_TYPE_VALUE),
      )
    logger.info { "Migrated ${result.modifiedCount} playlist(s) of type '$OLD_TYPE_VALUE' named '${PlaylistType.SINGULARITY_PLAYLIST_NAME}' to type '$NEW_TYPE_VALUE'" }
  }

  companion object : KLogging() {
    private const val PLAYLIST_METADATA_COLLECTION = "spotify_playlist_metadata"
    private const val NAME_FIELD = "name"
    private const val TYPE_FIELD = "type"
    private const val OLD_TYPE_VALUE = "UNKNOWN"
    private const val NEW_TYPE_VALUE = "SINGULARITY"
  }
}
