package de.chrgroth.james.platform.domain.model.playlist

import de.chrgroth.james.platform.domain.model.catalog.AlbumId
import de.chrgroth.james.platform.domain.model.catalog.ArtistId
import de.chrgroth.james.platform.domain.model.catalog.TrackId

data class PlaylistTrack(
  val trackId: TrackId,
  val artistIds: List<ArtistId>,
  val albumId: AlbumId?,
)
