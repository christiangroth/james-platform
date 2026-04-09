package de.chrgroth.spotify.control.domain.port.out.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId

interface AppArtistRepositoryPort {
  fun upsertAll(items: List<AppArtist>)
  fun countAll(): Long
  fun findAll(): List<AppArtist>
  fun findByArtistIds(artistIds: Set<ArtistId>): List<AppArtist>
  fun findWithImageLinkAndBlankName(): List<AppArtist>
  fun deleteAll()
}
