package de.chrgroth.spotify.control.domain.catalog

import de.chrgroth.spotify.control.domain.model.catalog.AlbumBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistBrowseItem
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.CatalogStats
import de.chrgroth.spotify.control.domain.model.catalog.TrackBrowseItem
import de.chrgroth.spotify.control.domain.port.`in`.catalog.CatalogBrowserPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppAlbumRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppArtistRepositoryPort
import de.chrgroth.spotify.control.domain.port.out.catalog.AppTrackRepositoryPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class CatalogBrowserService(
  private val appArtistRepository: AppArtistRepositoryPort,
  private val appAlbumRepository: AppAlbumRepositoryPort,
  private val appTrackRepository: AppTrackRepositoryPort,
) : CatalogBrowserPort {

  override fun getCatalogStats(): CatalogStats {
    val artistCount = appArtistRepository.countAll()
    val albumCount = appAlbumRepository.countAll()
    val trackCount = appTrackRepository.countAll()
    return CatalogStats(
      artistCount = artistCount,
      albumCount = albumCount,
      trackCount = trackCount,
    )
  }

  override fun getArtists(filter: String?): List<ArtistBrowseItem> {
    val filterLower = filter?.trim()?.lowercase()
    if (filterLower.isNullOrBlank()) return emptyList()

    val artists = appArtistRepository.findAll()
    val allAlbums = appAlbumRepository.findAll()
    val allTracks = appTrackRepository.findAll()

    val albumCountByArtistId = allAlbums.groupingBy { it.artistId?.value }.eachCount()
    val trackCountByArtistId = allTracks.groupingBy { it.artistId.value }.eachCount()

    return artists
      .filter { artist ->
        artist.artistName.lowercase().contains(filterLower)
      }
      .sortedBy { it.artistName.lowercase() }
      .map { artist ->
        ArtistBrowseItem(
          artistId = artist.id.value,
          artistName = artist.artistName,
          imageLink = artist.imageLink,
          albumCount = albumCountByArtistId[artist.id.value] ?: 0,
          trackCount = trackCountByArtistId[artist.id.value] ?: 0,
        )
      }
  }

  override fun getArtistAlbums(artistId: String): List<AlbumBrowseItem> {
    val albums = appAlbumRepository.findByArtistId(ArtistId(artistId))
    val tracks = appTrackRepository.findByArtistId(ArtistId(artistId))
    val tracksByAlbumId = tracks.groupBy { it.albumId?.value }

    return albums
      .sortedWith(compareByDescending { it.releaseDate })
      .map { album ->
        val albumTracks = tracksByAlbumId[album.id.value] ?: emptyList()
        val totalDurationMs = albumTracks.sumOf { it.durationMs ?: 0L }
        AlbumBrowseItem(
          albumId = album.id.value,
          title = album.title,
          imageLink = album.imageLink,
          releaseDate = album.releaseDate,
          trackCount = albumTracks.size,
          durationMs = totalDurationMs,
        )
      }
  }

  override fun getAlbumTracks(albumId: String): List<TrackBrowseItem> {
    val tracks = appTrackRepository.findByAlbumId(AlbumId(albumId))
    return tracks
      .sortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
      .map { track ->
        TrackBrowseItem(
          trackId = track.id.value,
          trackNumber = track.trackNumber,
          discNumber = track.discNumber,
          title = track.title,
          durationMs = track.durationMs ?: 0L,
        )
      }
  }
}
