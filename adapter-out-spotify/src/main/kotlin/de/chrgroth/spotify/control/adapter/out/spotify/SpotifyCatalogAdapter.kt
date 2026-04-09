package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyArtistAlbumsPage
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyAlbumResponse
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyAlbumTracksPage
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyArtistResponse
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifySimplifiedTrackResponse
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.SyncError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.AlbumSyncResult
import de.chrgroth.spotify.control.domain.model.catalog.AppAlbum
import de.chrgroth.spotify.control.domain.model.catalog.AppArtist
import de.chrgroth.spotify.control.domain.model.catalog.AppTrack
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.catalog.SpotifyCatalogPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Clock
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyCatalogAdapter(
  @param:ConfigProperty(name = "spotify.api.base-url")
  private val apiBaseUrl: String,
  private val httpMetrics: SpotifyHttpMetrics,
  private val throttler: SpotifyRequestThrottler,
) : SpotifyCatalogPort {

  private val httpClient = HttpClient.newHttpClient()

  override fun getArtist(
    userId: UserId,
    accessToken: AccessToken,
    artistId: String,
  ): Either<DomainError, AppArtist?> {
    return try {
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/artists/$artistId"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed("/v1/artists/{id}") {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      val errorResult = response.checkRateLimitOrError(logger, SyncError.ARTIST_DETAILS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      parseArtist(spotifyJson.decodeFromString<SpotifyArtistResponse>(response.body())).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching artist details for artist $artistId (user ${userId.value})" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    }
  }

  override fun getAlbum(
    userId: UserId,
    accessToken: AccessToken,
    albumId: String,
  ): Either<DomainError, AlbumSyncResult> {
    return try {
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/albums/$albumId"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed("/v1/albums/{id}") {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      val errorResult = response.checkRateLimitOrError(logger, SyncError.TRACK_DETAILS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      val albumResponse = spotifyJson.decodeFromString<SpotifyAlbumResponse>(response.body())
      val appAlbum = parseAlbum(albumResponse)
      val allTracks = albumResponse.tracks.items.filterNotNull().toMutableList()
      var nextUrl = albumResponse.tracks.next
      while (nextUrl != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val nextRequest = HttpRequest.newBuilder()
          .uri(URI.create(nextUrl))
          .header("Authorization", "Bearer ${accessToken.value}")
          .GET()
          .build()
        val nextResponse = httpMetrics.timed("/v1/albums/{id}/tracks") {
          httpClient.send(nextRequest, HttpResponse.BodyHandlers.ofString())
        }
        val nextError = nextResponse.checkRateLimitOrError(logger, SyncError.TRACK_DETAILS_FETCH_FAILED)
        if (nextError != null) return nextError
        val nextPage = spotifyJson.decodeFromString<SpotifyAlbumTracksPage>(nextResponse.body())
        allTracks.addAll(nextPage.items.filterNotNull())
        nextUrl = nextPage.next
      }
      val parsedTracks = allTracks.mapNotNull { parseAlbumTrack(it, appAlbum) }
      val droppedCount = allTracks.size - parsedTracks.size
      if (droppedCount > 0) {
        logger.warn {
          "Album $albumId: dropped $droppedCount track(s) without id or primary artist" +
            " (fetched ${allTracks.size}, album reports ${appAlbum.totalTracks} total)"
        }
      }
      AlbumSyncResult(
        album = appAlbum,
        tracks = parsedTracks,
      ).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching album tracks for album $albumId (user ${userId.value})" }
      SyncError.TRACK_DETAILS_FETCH_FAILED.left()
    }
  }

  override fun getArtistAlbumIds(
    userId: UserId,
    accessToken: AccessToken,
    artistId: String,
  ): Either<DomainError, List<String>> {
    return try {
      val albumIds = mutableListOf<String>()
      var nextUrl: String? = "$apiBaseUrl/v1/artists/$artistId/albums?limit=10"
      while (nextUrl != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val request = HttpRequest.newBuilder()
          .uri(URI.create(nextUrl))
          .header("Authorization", "Bearer ${accessToken.value}")
          .GET()
          .build()
        val response = httpMetrics.timed("/v1/artists/{id}/albums") {
          httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val errorResult = response.checkRateLimitOrError(logger, SyncError.ARTIST_DETAILS_FETCH_FAILED)
        if (errorResult != null) return errorResult
        val page = spotifyJson.decodeFromString<SpotifyArtistAlbumsPage>(response.body())
        albumIds.addAll(page.items.map { it.id })
        nextUrl = page.next
      }
      albumIds.right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error fetching album ids for artist $artistId (user ${userId.value})" }
      SyncError.ARTIST_DETAILS_FETCH_FAILED.left()
    }
  }

  private fun parseArtist(artist: SpotifyArtistResponse): AppArtist =
    AppArtist(
      id = ArtistId(artist.id),
      artistName = artist.name,
      imageLink = artist.images.firstOrNull()?.url,
      type = artist.type,
      lastSync = Clock.System.now(),
    )

  private fun parseAlbum(album: SpotifyAlbumResponse): AppAlbum =
    AppAlbum(
      id = AlbumId(album.id),
      totalTracks = album.totalTracks,
      title = album.name,
      imageLink = album.images.firstOrNull()?.url,
      releaseDate = album.releaseDate,
      releaseDatePrecision = album.releaseDatePrecision,
      type = album.albumType,
      artistId = album.artists.firstOrNull()?.id?.let { ArtistId(it) },
      artistName = album.artists.firstOrNull()?.name,
      additionalArtistIds = album.artists.additionalItems { id?.let { ArtistId(it) } }?.filterNotNull(),
      additionalArtistNames = album.artists.additionalItems { name },
      lastSync = Clock.System.now(),
    )

  private fun parseAlbumTrack(track: SpotifySimplifiedTrackResponse, album: AppAlbum): AppTrack? {
    val trackId = track.id ?: return null
    val (primaryArtistId, primaryArtistName) = track.artists.firstOrNull()
      ?.let { artist -> artist.id?.let { id -> id to artist.name } }
      ?: return null
    return AppTrack(
      id = TrackId(trackId),
      title = track.name,
      albumId = album.id,
      albumName = album.title,
      artistId = ArtistId(primaryArtistId),
      artistName = primaryArtistName,
      additionalArtistIds = track.artists.additionalItems { id?.let { ArtistId(it) } }?.filterNotNull() ?: emptyList(),
      additionalArtistNames = track.artists.additionalItems { name },
      discNumber = track.discNumber,
      durationMs = track.durationMs,
      trackNumber = track.trackNumber,
      type = track.type,
      lastSync = album.lastSync,
    )
  }

  private fun <T, R> List<T>.additionalItems(extractor: T.() -> R): List<R>? =
    if (size <= 1) null else (1 until size).map { get(it).extractor() }

  companion object : KLogging()
}

