package de.chrgroth.spotify.control.domain.playlist.check

/*
import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class DuplicateTrackIdsCheckRunner(
    private val appTrackRepository: AppTrackRepositoryPort,
    private val spotifyPlaylist: SpotifyPlaylistPort,
) : PlaylistCheckRunner {

  override val checkId = "duplicate-track-ids"
  override val displayName = "Duplicate Track IDs"

  override fun run(userId: UserId, playlistId: String, playlist: Playlist, currentPlaylistInfo: PlaylistInfo?, allPlaylistInfos: List<PlaylistInfo>): AppPlaylistCheck {
    val countByTrackId = playlist.tracks.groupingBy { it.trackId }.eachCount()
    val duplicateTrackIds = playlist.tracks
      .distinctBy { it.trackId }
      .filter { (countByTrackId[it.trackId] ?: 0) > 1 }
      .map { it.trackId }
    val appTrackById = if (duplicateTrackIds.isNotEmpty()) {
      appTrackRepository.findByTrackIds(duplicateTrackIds.toSet()).associateBy { it.id.value }
    } else {
      emptyMap()
    }
    val violations = duplicateTrackIds.map { trackId ->
      val appTrack = requireNotNull(appTrackById[trackId.value]) { "Track ${trackId.value} not found in catalog" }
      val artistName = appTrack.artistName ?: "Unknown Artist"
      "$artistName – ${appTrack.title}"
    }
    return AppPlaylistCheck(
      checkId = "$playlistId:$checkId",
      playlistId = PlaylistId(playlistId),
      lastCheck = Clock.System.now(),
      succeeded = violations.isEmpty(),
      violations = violations,
    )
  }

  override fun canFix(): Boolean = true

  override fun fix(
    userId: UserId,
    accessToken: AccessToken,
    playlistId: String,
    playlist: Playlist,
    currentPlaylistInfo: PlaylistInfo?,
    allPlaylistInfos: List<PlaylistInfo>,
  ): Either<DomainError, Unit> {
    val countByTrackId = playlist.tracks.groupingBy { it.trackId.value }.eachCount()
    val duplicateTrackIds = countByTrackId.entries.filter { it.value > 1 }.map { it.key }
    if (duplicateTrackIds.isEmpty()) {
      logger.info { "No duplicate tracks found in playlist $playlistId, nothing to fix" }
      return Unit.right()
    }
    logger.info { "Removing all occurrences of ${duplicateTrackIds.size} duplicate track(s) from playlist $playlistId (user ${userId.value}), then re-adding once each" }
    return spotifyPlaylist.removePlaylistTracks(userId, accessToken, playlistId, duplicateTrackIds)
      .flatMap { spotifyPlaylist.addPlaylistTracks(userId, accessToken, playlistId, duplicateTrackIds) }
  }

  companion object : KLogging()
}
*/
