package de.chrgroth.spotify.control.domain.playlist.check

/*
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistType
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class SingleArtistTrackCheckRunner(
  private val appTrackRepository: AppTrackRepositoryPort,
) : PlaylistCheckRunner {

  override val checkId = "single-artist-track"
  override val displayName = "Single Artist Track"

  override fun isApplicable(playlistInfo: PlaylistInfo?): Boolean = playlistInfo?.type == PlaylistType.SINGULARITY

  override fun run(
    userId: UserId,
    playlistId: String,
    playlist: Playlist,
    currentPlaylistInfo: PlaylistInfo?,
    allPlaylistInfos: List<PlaylistInfo>,
  ): AppPlaylistCheck {
    val trackIds = playlist.tracks.map { it.trackId }.toSet()
    val appTrackById = if (trackIds.isNotEmpty()) {
      appTrackRepository.findByTrackIds(trackIds).associateBy { it.id.value }
    } else {
      emptyMap()
    }
    val tracksByArtistId = playlist.tracks
      .filter { it.artistIds.isNotEmpty() }
      .groupBy { it.artistIds.first() }
    val violations = tracksByArtistId
      .filterValues { it.size > 1 }
      .flatMap { (_, tracks) ->
        tracks.map { track ->
          val appTrack = appTrackById[track.trackId.value]
          val artistName = appTrack?.artistName ?: track.artistIds.first().value
          "$artistName – ${appTrack?.title ?: track.trackId.value}"
        }
      }
      .sorted()
    return AppPlaylistCheck(
      checkId = "$playlistId:$checkId",
      playlistId = PlaylistId(playlistId),
      lastCheck = Clock.System.now(),
      succeeded = violations.isEmpty(),
      violations = violations,
    )
  }
}
*/
