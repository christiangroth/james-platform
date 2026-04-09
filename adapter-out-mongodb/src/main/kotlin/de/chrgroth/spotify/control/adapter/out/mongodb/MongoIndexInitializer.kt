package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.IndexOptions
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import mu.KLogging
import org.bson.Document

@ApplicationScoped
@Suppress("UnusedParameter")
class MongoIndexInitializer(
  private val recentlyPlayedDocumentRepository: RecentlyPlayedDocumentRepository,
  private val currentlyPlayingDocumentRepository: CurrentlyPlayingDocumentRepository,
  private val recentlyPartialPlayedDocumentRepository: RecentlyPartialPlayedDocumentRepository,
  private val appPlaybackDocumentRepository: AppPlaybackDocumentRepository,
  private val playbackAggregationDocumentRepository: PlaybackAggregationDocumentRepository,
  private val appArtistDocumentRepository: AppArtistDocumentRepository,
  private val appAlbumDocumentRepository: AppAlbumDocumentRepository,
  private val appTrackDocumentRepository: AppTrackDocumentRepository,
  private val playlistMetadataDocumentRepository: PlaylistMetadataDocumentRepository,
  private val appPlaylistCheckDocumentRepository: AppPlaylistCheckDocumentRepository,
) {

  fun onStartup(@Observes event: StartupEvent) {
    logger.info { "Ensuring MongoDB indexes..." }
    ensurePlaybackCollectionIndexes()
    ensurePlaybackAggregationCollectionIndexes()
    ensureCatalogCollectionIndexes()
    ensurePlaylistCollectionIndexes()
    logger.info { "MongoDB indexes ready." }
  }

  private fun ensurePlaybackCollectionIndexes() {
    recentlyPlayedDocumentRepository.mongoCollection().createIndex(
      Document(AppPlaybackRepositoryAdapter.SPOTIFY_USER_ID_FIELD, 1).append(AppPlaybackRepositoryAdapter.PLAYED_AT_FIELD, 1),
      IndexOptions().name("spotifyUserId_1_playedAt_1"),
    )
    currentlyPlayingDocumentRepository.mongoCollection().createIndex(
      Document(CurrentlyPlayingRepositoryAdapter.SPOTIFY_USER_ID_FIELD, 1)
        .append(CurrentlyPlayingRepositoryAdapter.TRACK_ID_FIELD, 1)
        .append(CurrentlyPlayingRepositoryAdapter.OBSERVED_AT_FIELD, 1),
      IndexOptions().name("spotifyUserId_1_trackId_1_observedAt_1"),
    )
    recentlyPartialPlayedDocumentRepository.mongoCollection().createIndex(
      Document(AppPlaybackRepositoryAdapter.SPOTIFY_USER_ID_FIELD, 1).append(AppPlaybackRepositoryAdapter.PLAYED_AT_FIELD, 1),
      IndexOptions().name("rpp_spotifyUserId_1_playedAt_1"),
    )
    appPlaybackDocumentRepository.mongoCollection().createIndex(
      Document(AppPlaybackRepositoryAdapter.SPOTIFY_USER_ID_FIELD, 1).append(AppPlaybackRepositoryAdapter.PLAYED_AT_FIELD, 1),
      IndexOptions().name("app_playback_spotifyUserId_1_playedAt_1"),
    )
    appPlaybackDocumentRepository.mongoCollection().createIndex(
      Document(AppPlaybackRepositoryAdapter.TRACK_ID_FIELD, 1),
      IndexOptions().name("app_playback_trackId_1"),
    )
    appPlaybackDocumentRepository.mongoCollection().createIndex(
      Document(AppPlaybackRepositoryAdapter.SPOTIFY_USER_ID_FIELD, 1)
        .append(AppPlaybackRepositoryAdapter.PLAYED_AT_FIELD, 1)
        .append(AppPlaybackRepositoryAdapter.TRACK_ID_FIELD, 1)
        .append(AppPlaybackRepositoryAdapter.SECONDS_PLAYED_FIELD, 1),
      IndexOptions().name("app_playback_spotifyUserId_1_playedAt_1_trackId_1_secondsPlayed_1"),
    )
  }

  private fun ensurePlaybackAggregationCollectionIndexes() {
    playbackAggregationDocumentRepository.mongoCollection().createIndex(
      Document(PlaybackAggregationRepositoryAdapter.SPOTIFY_USER_ID_FIELD, 1)
        .append(PlaybackAggregationRepositoryAdapter.TYPE_FIELD, 1)
        .append(PlaybackAggregationRepositoryAdapter.PERIOD_START_FIELD, 1),
      IndexOptions().name("app_playback_aggregation_spotifyUserId_1_type_1_periodStart_1"),
    )
  }

  private fun ensureCatalogCollectionIndexes() {
    appAlbumDocumentRepository.mongoCollection().createIndex(
      Document(AppAlbumRepositoryAdapter.ARTIST_ID_FIELD, 1),
      IndexOptions().name("app_album_artistId_1"),
    )
    appTrackDocumentRepository.mongoCollection().createIndex(
      Document(AppTrackRepositoryAdapter.ARTIST_ID_FIELD, 1),
      IndexOptions().name("app_track_artistId_1"),
    )
    appTrackDocumentRepository.mongoCollection().createIndex(
      Document(AppTrackRepositoryAdapter.ALBUM_ID_FIELD, 1),
      IndexOptions().name("app_track_albumId_1"),
    )
  }

  private fun ensurePlaylistCollectionIndexes() {
    playlistMetadataDocumentRepository.mongoCollection().createIndex(
      Document(AppPlaybackRepositoryAdapter.SPOTIFY_USER_ID_FIELD, 1),
      IndexOptions().name("spotify_playlist_metadata_spotifyUserId_1"),
    )
    playlistMetadataDocumentRepository.mongoCollection().createIndex(
      Document(PlaylistRepositoryAdapter.SYNC_STATUS_FIELD, 1),
      IndexOptions().name("spotify_playlist_metadata_syncStatus_1"),
    )
    appPlaylistCheckDocumentRepository.mongoCollection().createIndex(
      Document("playlistId", 1),
      IndexOptions().name("app_playlist_check_playlistId_1"),
    )
    appPlaylistCheckDocumentRepository.mongoCollection().createIndex(
      Document("succeeded", 1),
      IndexOptions().name("app_playlist_check_succeeded_1"),
    )
  }

  companion object : KLogging()
}
