package de.chrgroth.spotify.control.domain.playlist.check

/*
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.AppPlaylistCheck
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistId
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistInfo
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import kotlin.time.Clock

@ApplicationScoped
@Suppress("Unused")
class TrackFromLatestReleaseCheckRunner(
  private val appTrackRepository: AppTrackRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val spotifyPlaylist: SpotifyPlaylistPort,
) : PlaylistCheckRunner {

  override val checkId = "track-from-latest-release"
  override val displayName = "Track From Latest Release"

  override fun run(
    userId: UserId,
    playlistId: String,
    playlist: Playlist,
    currentPlaylistInfo: PlaylistInfo?,
    allPlaylistInfos: List<PlaylistInfo>,
  ): AppPlaylistCheck {
    val violations = findViolations(playlist).map { it.message }
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
    val violations = findViolations(playlist)
    if (violations.isEmpty()) {
      logger.info { "No track-from-latest-release violations found in playlist $playlistId, nothing to fix" }
      return Unit.right()
    }
    logger.info { "Replacing ${violations.size} outdated track(s) in playlist $playlistId (user ${userId.value})" }
    // Process in reverse position order so earlier positions are unaffected by later replacements
    violations.sortedByDescending { it.position }.forEach { violation ->
      val result = spotifyPlaylist.replacePlaylistTrack(
        userId, accessToken, playlistId,
        violation.oldTrackId, violation.newTrackId, violation.position,
      )
      if (result.isLeft()) {
        logger.error { "Failed to replace track at position ${violation.position} in playlist $playlistId" }
        return (result as Either.Left).value.left()
      }
    }
    return Unit.right()
  }

  private fun findViolations(playlist: Playlist): List<TrackViolation> {
    if (playlist.tracks.isEmpty()) return emptyList()

    val playlistTrackIds = playlist.tracks.map { it.trackId }.toSet()
    val currentTracksById = appTrackRepository.findByTrackIds(playlistTrackIds).associateBy { it.id }

    val artistIds = playlist.tracks.mapNotNull { currentTracksById[it.trackId]?.artistId }.toSet()
    val albumsByArtistId: Map<ArtistId, List<AppAlbum>> = artistIds.associateWith { appAlbumRepository.findByArtistId(it) }

    val allAlbumIds = albumsByArtistId.values.flatten().map { it.id }.toSet()
    val albumById: Map<AlbumId, AppAlbum> = albumsByArtistId.values.flatten().associateBy { it.id }
    val tracksByAlbumId: Map<AlbumId, List<AppTrack>> = allAlbumIds.associateWith { appTrackRepository.findByAlbumId(it) }

    return playlist.tracks
      .withIndex()
      .mapNotNull { (position, playlistTrack) ->
        val currentTrack = currentTracksById[playlistTrack.trackId] ?: return@mapNotNull null
        buildViolation(position, currentTrack, albumsByArtistId, albumById, tracksByAlbumId)
      }
  }

  private fun buildViolation(
    position: Int,
    currentTrack: AppTrack,
    albumsByArtistId: Map<ArtistId, List<AppAlbum>>,
    albumById: Map<AlbumId, AppAlbum>,
    tracksByAlbumId: Map<AlbumId, List<AppTrack>>,
  ): TrackViolation? {
    val artistAlbumIds = albumsByArtistId[currentTrack.artistId]?.map { it.id }?.toSet() ?: return null
    val candidates = tracksByAlbumId
      .filterKeys { it in artistAlbumIds }
      .values
      .flatten()
      .filter { t -> t.id == currentTrack.id || t.title.equals(currentTrack.title, ignoreCase = true) }
      .mapNotNull { t -> t.albumId?.let { albumById[it] }?.let { album -> t to album } }

    if (candidates.size <= 1) return null

    val (bestTrack, bestAlbum) = candidates.maxWith(ALBUM_COMPARATOR)
    val currentAlbumTitle = currentTrack.albumId?.let { albumById[it]?.title }
      ?: currentTrack.albumName
      ?: "Unknown Album"
    val artistName = currentTrack.artistName ?: "Unknown Artist"
    return TrackViolation(
      position = position,
      oldTrackId = currentTrack.id.value,
      newTrackId = bestTrack.id.value,
      message = "$artistName – ${currentTrack.title} ($currentAlbumTitle → ${bestAlbum.title ?: "Unknown Album"})",
    ).takeIf { currentTrack.albumId != bestAlbum.id }
  }

  private data class TrackViolation(
    val position: Int,
    val oldTrackId: String,
    val newTrackId: String,
    val message: String,
  )

  companion object : KLogging() {
    /**
     * Compares two (track, album) pairs: album type "album" beats "single"/"ep", then newest release wins.
     */
    private val ALBUM_COMPARATOR = Comparator<Pair<AppTrack, AppAlbum>> { a, b ->
      val priorityA = albumTypePriority(a.second.type)
      val priorityB = albumTypePriority(b.second.type)
      if (priorityA != priorityB) {
        priorityA.compareTo(priorityB)
      } else {
        compareReleaseDates(a.second.releaseDate, b.second.releaseDate)
      }
    }

    private fun albumTypePriority(type: String?): Int = when (type?.lowercase()) {
      "album" -> 2
      "single", "ep" -> 1
      else -> 0
    }

    private fun compareReleaseDates(dateA: String?, dateB: String?): Int = when {
      dateA == null && dateB == null -> 0
      dateA == null -> -1
      dateB == null -> 1
      else -> dateA.compareTo(dateB)
    }
  }
}
*/
