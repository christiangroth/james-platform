package de.chrgroth.spotify.control.domain.playlist.check

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.PlaylistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class YearSongsInAllCheckRunner(
  private val playlistRepository: PlaylistRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
  private val spotifyPlaylist: SpotifyPlaylistPort,
) : PlaylistCheckRunner {

  override val checkId = "year-songs-in-all"
  override val displayName = "Year Songs In All"

  override fun isApplicable(playlistInfo: PlaylistInfo?): Boolean = playlistInfo?.type == PlaylistType.YEAR

  override fun run(userId: UserId, playlistId: String, playlist: Playlist, currentPlaylistInfo: PlaylistInfo?, allPlaylistInfos: List<PlaylistInfo>): AppPlaylistCheck {
    val allPlaylistInfo = allPlaylistInfos.find { it.type == PlaylistType.ALL }
    val allPlaylist = allPlaylistInfo?.let { playlistRepository.findByUserIdAndPlaylistId(userId, it.spotifyPlaylistId) }
    val violations = if (allPlaylist == null) {
      emptyList()
    } else {
      val allTrackIds = allPlaylist.tracks.map { it.trackId }.toSet()
      val missingTrackIds = playlist.tracks
        .filter { it.trackId !in allTrackIds }
        .distinctBy { it.trackId }
        .map { it.trackId }
      val appTrackById = if (missingTrackIds.isNotEmpty()) {
        appTrackRepository.findByTrackIds(missingTrackIds.toSet()).associateBy { it.id.value }
      } else {
        emptyMap()
      }
      missingTrackIds.map { trackId ->
        val appTrack = requireNotNull(appTrackById[trackId.value]) { "Track ${trackId.value} not found in catalog" }
        val artistName = appTrack.artistName ?: "Unknown Artist"
        "$artistName – ${appTrack.title}"
      }
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
  ): Either<DomainError, Unit> =
    resolveAllPlaylist(userId, playlistId, allPlaylistInfos).flatMap { (allPlaylistInfo, allPlaylist) ->
      val allTrackIds = allPlaylist.tracks.map { it.trackId.value }.toSet()
      val missingTrackIds = playlist.tracks
        .filter { it.trackId.value !in allTrackIds }
        .distinctBy { it.trackId }
        .map { it.trackId.value }
      if (missingTrackIds.isEmpty()) {
        logger.info { "No missing tracks found in all playlist for year playlist $playlistId, nothing to fix" }
        Unit.right()
      } else {
        logger.info { "Adding ${missingTrackIds.size} track(s) to all playlist ${allPlaylistInfo.spotifyPlaylistId} (user ${userId.value})" }
        spotifyPlaylist.addPlaylistTracks(userId, accessToken, allPlaylistInfo.spotifyPlaylistId, missingTrackIds)
      }
    }

  private fun resolveAllPlaylist(userId: UserId, playlistId: String, allPlaylistInfos: List<PlaylistInfo>): Either<DomainError, Pair<PlaylistInfo, Playlist>> {
    val allPlaylistInfo = allPlaylistInfos.find { it.type == PlaylistType.ALL } ?: run {
      logger.warn { "No all playlist found for user ${userId.value}, cannot fix $checkId for playlist $playlistId" }
      return PlaylistFixError.FIX_NOT_FOUND.left()
    }
    val allPlaylist = playlistRepository.findByUserIdAndPlaylistId(userId, allPlaylistInfo.spotifyPlaylistId) ?: run {
      logger.warn { "All playlist ${allPlaylistInfo.spotifyPlaylistId} not yet synced for user ${userId.value}, cannot fix $checkId" }
      return PlaylistFixError.PLAYLIST_NOT_FOUND.left()
    }
    return (allPlaylistInfo to allPlaylist).right()
  }

  companion object : KLogging()
}
