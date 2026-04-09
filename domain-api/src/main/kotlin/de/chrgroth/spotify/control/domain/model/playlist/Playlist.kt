package de.chrgroth.spotify.control.domain.model.playlist

data class Playlist(
  val spotifyPlaylistId: String,
  val tracks: List<PlaylistTrack>,
) {
  val numberOfTracks: Int get() = tracks.size
}
