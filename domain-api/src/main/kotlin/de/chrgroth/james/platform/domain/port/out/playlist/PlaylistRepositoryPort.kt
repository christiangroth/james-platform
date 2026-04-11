package de.chrgroth.james.platform.domain.port.out.playlist

import de.chrgroth.james.platform.domain.model.playlist.Playlist
import de.chrgroth.james.platform.domain.model.playlist.PlaylistInfo
import de.chrgroth.james.platform.domain.model.playlist.PlaylistTrack
import de.chrgroth.james.platform.domain.model.user.UserId

interface PlaylistRepositoryPort {
  fun findByUserId(userId: UserId): List<PlaylistInfo>
  fun replaceAll(userId: UserId, playlists: List<PlaylistInfo>)
  fun findByUserIdAndPlaylistId(userId: UserId, playlistId: String): Playlist?
  fun findTrackCountsByUserId(userId: UserId): Map<String, Int>
  fun save(userId: UserId, playlist: Playlist)
  fun appendTracks(userId: UserId, playlistId: String, tracks: List<PlaylistTrack>)
  fun updateLastSyncTime(userId: UserId, playlistId: String, time: kotlin.time.Instant)
  fun setAllSyncInactive()
}
