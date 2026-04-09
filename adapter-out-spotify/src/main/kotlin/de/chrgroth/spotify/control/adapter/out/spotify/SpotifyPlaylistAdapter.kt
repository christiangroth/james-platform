package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyAddPlaylistTracksRequest
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyPlaylistTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyPlaylistTracksResponse
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemovePlaylistTrackAtPositionRequest
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemovePlaylistTracksRequest
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemoveTrackAtPositionObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRemoveTrackObject
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyUserPlaylistsResponse
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaylistFixError
import de.chrgroth.spotify.control.domain.error.PlaylistSyncError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playlist.Playlist
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTrack
import de.chrgroth.spotify.control.domain.model.playlist.PlaylistTracksPage
import de.chrgroth.spotify.control.domain.model.playlist.SpotifyPlaylistItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.outbox.DomainOutboxPartition
import de.chrgroth.spotify.control.domain.port.out.playlist.SpotifyPlaylistPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyPlaylistAdapter(
  @param:ConfigProperty(name = "spotify.api.base-url")
  private val apiBaseUrl: String,
  private val httpMetrics: SpotifyHttpMetrics,
  private val throttler: SpotifyRequestThrottler,
) : SpotifyPlaylistPort {

  private val httpClient = HttpClient.newHttpClient()

  override fun getPlaylists(userId: UserId, accessToken: AccessToken): Either<DomainError, List<SpotifyPlaylistItem>> {
    return try {
      val items = mutableListOf<SpotifyPlaylistItem>()
      var nextUrl: String? = "$apiBaseUrl/v1/me/playlists?limit=50"
      while (nextUrl != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val request = HttpRequest.newBuilder()
          .uri(URI.create(nextUrl))
          .header("Authorization", "Bearer ${accessToken.value}")
          .GET()
          .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val errorResult = response.checkRateLimitOrError(logger, PlaylistSyncError.PLAYLIST_FETCH_FAILED)
        if (errorResult != null) return errorResult
        val playlistsResponse = spotifyJson.decodeFromString<SpotifyUserPlaylistsResponse>(response.body())
        items += playlistsResponse.items.map { playlist ->
          SpotifyPlaylistItem(
            id = playlist.id,
            name = playlist.name,
            snapshotId = playlist.snapshotId,
            ownerId = playlist.owner.id,
          )
        }
        nextUrl = playlistsResponse.next
      }
      items.right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist fetch for user ${userId.value}" }
      PlaylistSyncError.PLAYLIST_FETCH_FAILED.left()
    }
  }

  override fun getPlaylistTracks(userId: UserId, accessToken: AccessToken, playlistId: String): Either<DomainError, Playlist> {
    return try {
      val tracks = mutableListOf<PlaylistTrack>()
      var nextUrl: String? = "$apiBaseUrl/v1/playlists/$playlistId/items?limit=50"
      while (nextUrl != null) {
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val request = HttpRequest.newBuilder()
          .uri(URI.create(nextUrl))
          .header("Authorization", "Bearer ${accessToken.value}")
          .GET()
          .build()
        val response = httpMetrics.timed("/v1/playlists/{id}/items") {
          httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val errorResult = response.checkRateLimitOrError(logger, PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED)
        if (errorResult != null) return errorResult
        val tracksResponse = spotifyJson.decodeFromString<SpotifyPlaylistTracksResponse>(response.body())
        tracks += parsePlaylistTracks(tracksResponse.items)
        nextUrl = tracksResponse.next
      }
      Playlist(
        spotifyPlaylistId = playlistId,
        tracks = tracks,
      ).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist tracks fetch for playlist $playlistId (user ${userId.value})" }
      PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()
    }
  }

  override fun getPlaylistTracksPage(userId: UserId, accessToken: AccessToken, playlistId: String, pageUrl: String?): Either<DomainError, PlaylistTracksPage> {
    return try {
      val url = pageUrl ?: "$apiBaseUrl/v1/playlists/$playlistId/items?limit=50"
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed("/v1/playlists/{id}/items") {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      val errorResult = response.checkRateLimitOrError(logger, PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED)
      if (errorResult != null) return errorResult
      val tracksResponse = spotifyJson.decodeFromString<SpotifyPlaylistTracksResponse>(response.body())
      PlaylistTracksPage(
        snapshotId = tracksResponse.snapshotId ?: "",
        tracks = parsePlaylistTracks(tracksResponse.items),
        nextUrl = tracksResponse.next,
      ).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during playlist tracks page fetch for playlist $playlistId (user ${userId.value})" }
      PlaylistSyncError.PLAYLIST_TRACKS_FETCH_FAILED.left()
    }
  }

  private fun parsePlaylistTracks(items: List<SpotifyPlaylistTrackObject?>): List<PlaylistTrack> =
    items.mapNotNull { item ->
      if (item == null) return@mapNotNull null
      val track = item.track ?: return@mapNotNull null
      if (track.type != "track") {
        logger.info { "Ignoring non-track playlist item of type '${track.type}'" }
        return@mapNotNull null
      }
      if (track.id == null) {
        logger.info { "Ignoring local track '${track.name}' without id in playlist" }
        return@mapNotNull null
      }
      val albumId = track.album?.id
      if (albumId == null) {
        logger.warn { "Track ${track.id} has no albumId in playlist, saving track without album" }
      }
      val artistIds = track.artists.mapNotNull { artist ->
        if (artist.id == null) {
          logger.warn { "Artist '${artist.name}' has no id in track ${track.id}, skipping artist" }
          null
        } else {
          ArtistId(artist.id)
        }
      }
      PlaylistTrack(
        trackId = TrackId(track.id),
        artistIds = artistIds,
        albumId = albumId?.let { AlbumId(it) },
      )
    }

  override fun removePlaylistTracks(
    userId: UserId,
    accessToken: AccessToken,
    playlistId: String,
    trackIds: List<String>,
  ): Either<DomainError, Unit> {
    return try {
      val allItems = trackIds.map { trackId -> SpotifyRemoveTrackObject(uri = "spotify:track:$trackId") }
      allItems.chunked(REMOVE_TRACKS_BATCH_SIZE).forEach { batch ->
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val requestBody = spotifyJson.encodeToString(SpotifyRemovePlaylistTracksRequest(items = batch))
        val request = HttpRequest.newBuilder()
          .uri(URI.create("$apiBaseUrl/v1/playlists/$playlistId/items"))
          .header("Authorization", "Bearer ${accessToken.value}")
          .header("Content-Type", "application/json")
          .method("DELETE", HttpRequest.BodyPublishers.ofString(requestBody))
          .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val errorResult = response.checkRateLimitOrError(logger, PlaylistFixError.FIX_FAILED)
        if (errorResult != null) return errorResult
      }
      Unit.right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during track removal from playlist $playlistId (user ${userId.value})" }
      PlaylistFixError.FIX_FAILED.left()
    }
  }

  override fun addPlaylistTracks(
    userId: UserId,
    accessToken: AccessToken,
    playlistId: String,
    trackIds: List<String>,
  ): Either<DomainError, Unit> {
    return try {
      val uris = trackIds.map { "spotify:track:$it" }
      uris.chunked(ADD_TRACKS_BATCH_SIZE).forEach { batch ->
        throttler.throttle(DomainOutboxPartition.ToSpotify.key)
        val requestBody = spotifyJson.encodeToString(SpotifyAddPlaylistTracksRequest(uris = batch))
        val request = HttpRequest.newBuilder()
          .uri(URI.create("$apiBaseUrl/v1/playlists/$playlistId/items"))
          .header("Authorization", "Bearer ${accessToken.value}")
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody))
          .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val errorResult = response.checkRateLimitOrError(logger, PlaylistFixError.FIX_FAILED)
        if (errorResult != null) return errorResult
      }
      Unit.right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during track addition to playlist $playlistId (user ${userId.value})" }
      PlaylistFixError.FIX_FAILED.left()
    }
  }

  override fun replacePlaylistTrack(
    userId: UserId,
    accessToken: AccessToken,
    playlistId: String,
    oldTrackId: String,
    newTrackId: String,
    position: Int,
  ): Either<DomainError, Unit> {
    return try {
      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val removeBody = spotifyJson.encodeToString(
        SpotifyRemovePlaylistTrackAtPositionRequest(
          items = listOf(SpotifyRemoveTrackAtPositionObject(uri = "spotify:track:$oldTrackId", positions = listOf(position))),
        ),
      )
      val removeRequest = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/playlists/$playlistId/tracks"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .header("Content-Type", "application/json")
        .method("DELETE", HttpRequest.BodyPublishers.ofString(removeBody))
        .build()
      val removeResponse = httpClient.send(removeRequest, HttpResponse.BodyHandlers.ofString())
      val removeError = removeResponse.checkRateLimitOrError(logger, PlaylistFixError.FIX_FAILED)
      if (removeError != null) return removeError

      throttler.throttle(DomainOutboxPartition.ToSpotify.key)
      val addBody = spotifyJson.encodeToString(SpotifyAddPlaylistTracksRequest(uris = listOf("spotify:track:$newTrackId"), position = position))
      val addRequest = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/playlists/$playlistId/tracks"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(addBody))
        .build()
      val addResponse = httpClient.send(addRequest, HttpResponse.BodyHandlers.ofString())
      val addError = addResponse.checkRateLimitOrError(logger, PlaylistFixError.FIX_FAILED)
      if (addError != null) return addError

      Unit.right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during track replacement in playlist $playlistId (user ${userId.value})" }
      PlaylistFixError.FIX_FAILED.left()
    }
  }

  companion object : KLogging() {
    private const val REMOVE_TRACKS_BATCH_SIZE = 100
    private const val ADD_TRACKS_BATCH_SIZE = 100
  }
}
