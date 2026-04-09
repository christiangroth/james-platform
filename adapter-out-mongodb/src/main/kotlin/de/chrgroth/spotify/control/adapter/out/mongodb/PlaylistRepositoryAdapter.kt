package de.chrgroth.spotify.control.adapter.out.mongodb

import com.mongodb.client.model.Updates
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging
import org.bson.Document

@ApplicationScoped
class PlaylistRepositoryAdapter(
  private val playlistMetadataDocumentRepository: PlaylistMetadataDocumentRepository,
  private val playlistDocumentRepository: PlaylistDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : PlaylistRepositoryPort {

  override fun findByUserId(userId: UserId): List<PlaylistInfo> =
    mongoQueryMetrics.timed("spotify_playlist_metadata.findByUserId") {
      playlistMetadataDocumentRepository
        .list("spotifyUserId = ?1", userId.value)
        .map { it.toDomain() }
    }

  override fun replaceAll(userId: UserId, playlists: List<PlaylistInfo>) {
    logger.info { "Saving ${playlists.size} playlist metadata document(s) for user ${userId.value}" }
    mongoQueryMetrics.timed("spotify_playlist_metadata.deleteByUserId") {
      playlistMetadataDocumentRepository.delete("spotifyUserId = ?1", userId.value)
    }
    if (playlists.isNotEmpty()) {
      val documents = playlists.map { it.toDocument(userId) }
      mongoQueryMetrics.timed("spotify_playlist_metadata.saveAll") {
        playlistMetadataDocumentRepository.persist(documents)
      }
    }
  }

  override fun findByUserIdAndPlaylistId(userId: UserId, playlistId: String): Playlist? =
    mongoQueryMetrics.timed("spotify_playlist.findByUserIdAndPlaylistId") {
      playlistDocumentRepository.findById("${userId.value}:$playlistId")?.toDomain()
    }

  override fun findTrackCountsByUserId(userId: UserId): Map<String, Int> =
    mongoQueryMetrics.timed("spotify_playlist.findTrackCountsByUserId") {
      playlistDocumentRepository
        .list("spotifyUserId = ?1", userId.value)
        .associate { it.spotifyPlaylistId to it.tracks.size }
    }

  override fun save(userId: UserId, playlist: Playlist) {
    logger.info { "Saving playlist document for playlist ${playlist.spotifyPlaylistId} (user ${userId.value}) with ${playlist.tracks.size} track(s)" }
    val document = playlist.toDocument(userId)
    mongoQueryMetrics.timed("spotify_playlist.save") {
      playlistDocumentRepository.persistOrUpdate(document)
    }
  }

  override fun appendTracks(userId: UserId, playlistId: String, tracks: List<PlaylistTrack>) {
    val docId = "${userId.value}:$playlistId"
    logger.info { "Appending ${tracks.size} track(s) to playlist document $playlistId (user ${userId.value})" }
    mongoQueryMetrics.timed("spotify_playlist.appendTracks") {
      val existing = playlistDocumentRepository.findById(docId)
      if (existing != null) {
        existing.tracks = existing.tracks + tracks.map { it.toSubdocument() }
        playlistDocumentRepository.persistOrUpdate(existing)
      } else {
        logger.warn { "Playlist document not found for appending tracks: $playlistId (user ${userId.value})" }
      }
    }
  }

  override fun updateLastSyncTime(userId: UserId, playlistId: String, time: kotlin.time.Instant) {
    val id = "${userId.value}:$playlistId"
    mongoQueryMetrics.timed("spotify_playlist_metadata.updateLastSyncTime") {
      playlistMetadataDocumentRepository.findById(id)?.let { doc ->
        doc.lastSyncTime = time.toJavaInstant()
        playlistMetadataDocumentRepository.persistOrUpdate(doc)
      }
    }
  }

  private fun PlaylistMetadataDocument.toDomain() = PlaylistInfo(
    spotifyPlaylistId = spotifyPlaylistId,
    snapshotId = snapshotId,
    lastSnapshotIdSyncTime = lastSnapshotIdSyncTime.toKotlinInstant(),
    name = name,
    syncStatus = PlaylistSyncStatus.valueOf(syncStatus),
    type = type?.let { PlaylistType.valueOf(it) },
    lastSyncTime = lastSyncTime?.toKotlinInstant(),
  )

  private fun PlaylistInfo.toDocument(userId: UserId) = PlaylistMetadataDocument().apply {
    id = "${userId.value}:${this@toDocument.spotifyPlaylistId}"
    spotifyUserId = userId.value
    spotifyPlaylistId = this@toDocument.spotifyPlaylistId
    snapshotId = this@toDocument.snapshotId
    lastSnapshotIdSyncTime = this@toDocument.lastSnapshotIdSyncTime.toJavaInstant()
    name = this@toDocument.name
    syncStatus = this@toDocument.syncStatus.name
    type = this@toDocument.type?.name
    lastSyncTime = this@toDocument.lastSyncTime?.toJavaInstant()
  }

  private fun PlaylistDocument.toDomain() = Playlist(
    spotifyPlaylistId = spotifyPlaylistId,
    tracks = tracks.map { it.toDomain() },
  )

  private fun PlaylistTrackSubdocument.toDomain() = PlaylistTrack(
    trackId = TrackId(trackId),
    artistIds = artistIds.map { ArtistId(it) },
    albumId = albumId?.let { AlbumId(it) },
  )

  private fun Playlist.toDocument(userId: UserId) = PlaylistDocument().apply {
    id = "${userId.value}:${this@toDocument.spotifyPlaylistId}"
    spotifyUserId = userId.value
    spotifyPlaylistId = this@toDocument.spotifyPlaylistId
    tracks = this@toDocument.tracks.map { it.toSubdocument() }
  }

  private fun PlaylistTrack.toSubdocument() = PlaylistTrackSubdocument().apply {
    trackId = this@toSubdocument.trackId.value
    artistIds = this@toSubdocument.artistIds.map { it.value }
    albumId = this@toSubdocument.albumId?.value
  }

  override fun setAllSyncInactive() {
    logger.info { "Setting all playlist sync statuses to inactive" }
    mongoQueryMetrics.timed("spotify_playlist_metadata.setAllSyncInactive") {
      playlistMetadataDocumentRepository.mongoCollection().updateMany(
        Document(),
        Updates.set(SYNC_STATUS_FIELD, PlaylistSyncStatus.PASSIVE.name),
      )
    }
  }

  companion object : KLogging() {
    internal const val SYNC_STATUS_FIELD = "syncStatus"
  }
}

