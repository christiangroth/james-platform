package de.chrgroth.james.platform.domain.port.`in`.catalog

import de.chrgroth.james.platform.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.james.platform.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.james.platform.domain.model.catalog.CatalogStats
import de.chrgroth.james.platform.domain.model.catalog.TrackBrowseItem

interface CatalogBrowserPort {
  fun getCatalogStats(): CatalogStats
  fun getArtists(filter: String?): List<ArtistBrowseItem>
  fun getArtistAlbums(artistId: String): List<AlbumBrowseItem>
  fun getAlbumTracks(albumId: String): List<TrackBrowseItem>
}
