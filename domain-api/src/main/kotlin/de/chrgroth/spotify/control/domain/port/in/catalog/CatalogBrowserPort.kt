package de.chrgroth.spotify.control.domain.port.`in`.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.TrackBrowseItem

interface CatalogBrowserPort {
  fun getCatalogStats(): CatalogStats
  fun getArtists(filter: String?): List<ArtistBrowseItem>
  fun getArtistAlbums(artistId: String): List<AlbumBrowseItem>
  fun getAlbumTracks(albumId: String): List<TrackBrowseItem>
}
