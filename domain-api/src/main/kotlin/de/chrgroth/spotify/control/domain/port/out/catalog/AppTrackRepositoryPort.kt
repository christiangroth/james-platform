package de.chrgroth.spotify.control.domain.port.out.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.TrackId

interface AppTrackRepositoryPort {
  fun upsertAll(items: List<AppTrack>)
  fun countAll(): Long
  fun findAll(): List<AppTrack>
  fun findByTrackIds(trackIds: Set<TrackId>): List<AppTrack>
  fun findByArtistId(artistId: ArtistId): List<AppTrack>
  fun findByAlbumId(albumId: AlbumId): List<AppTrack>
  fun deleteAll()
}
