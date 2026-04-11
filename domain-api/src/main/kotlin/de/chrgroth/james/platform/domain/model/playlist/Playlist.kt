package de.chrgroth.james.platform.domain.model.playlist

data class Playlist(
  val spotifyPlaylistId: String,
  val tracks: List<PlaylistTrack>,
) {
  val numberOfTracks: Int get() = tracks.size
}
