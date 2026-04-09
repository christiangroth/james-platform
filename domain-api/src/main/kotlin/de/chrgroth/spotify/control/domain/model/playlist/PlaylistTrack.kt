package de.chrgroth.spotify.control.domain.model.playlist

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId

data class PlaylistTrack(
  val trackId: TrackId,
  val artistIds: List<ArtistId>,
  val albumId: AlbumId?,
)
