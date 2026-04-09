package de.chrgroth.spotify.control.adapter.out.spotify

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyCurrentlyPlayingResponse
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyRecentlyPlayedResponse
import de.chrgroth.spotify.control.adapter.out.spotify.model.SpotifyTrackResponse
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.error.PlaybackError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.SpotifyPlaybackPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@ApplicationScoped
@Suppress("Unused", "TooGenericExceptionCaught")
class SpotifyPlaybackAdapter(
  @param:ConfigProperty(name = "spotify.api.base-url")
  private val apiBaseUrl: String,
  private val httpMetrics: SpotifyHttpMetrics,
) : SpotifyPlaybackPort {

  private val httpClient = HttpClient.newHttpClient()

  override fun getCurrentlyPlaying(userId: UserId, accessToken: AccessToken): Either<DomainError, CurrentlyPlayingItem?> {
    return try {
      val request = HttpRequest.newBuilder()
        .uri(URI.create("$apiBaseUrl/v1/me/player/currently-playing"))
        .header("Authorization", "Bearer ${accessToken.value}")
        .GET()
        .build()
      val response = httpMetrics.timed("/v1/me/player/currently-playing") {
        httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      }
      if (response.statusCode() == HTTP_NO_CONTENT) {
        return null.right()
      }
      val errorResult = response.checkRateLimitOrError(logger, PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED)
      if (errorResult != null) return errorResult
      val currentlyPlaying = spotifyJson.decodeFromString<SpotifyCurrentlyPlayingResponse>(response.body())
      parseCurrentlyPlayingItem(userId, currentlyPlaying).right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during currently playing fetch" }
      PlaybackError.CURRENTLY_PLAYING_FETCH_FAILED.left()
    }
  }

  private fun parseCurrentlyPlayingItem(userId: UserId, response: SpotifyCurrentlyPlayingResponse): CurrentlyPlayingItem? {
    val track = response.item
    if (track == null || track.type != "track") {
      logger.info { "Ignoring non-track currently playing event of type '${track?.type}'" }
      return null
    }
    if (track.isLocal || track.id == null) {
      logger.info { "Ignoring local or id-less currently playing track '${track.name}'" }
      return null
    }
    val progressMs = response.progressMs ?: 0L
    val observedAt = Clock.System.now()
    return CurrentlyPlayingItem(
      spotifyUserId = userId,
      trackId = TrackId(track.id),
      trackName = track.name,
      artistIds = track.artists.mapNotNull { it.id?.let { id -> ArtistId(id) } },
      artistNames = track.artists.map { it.name },
      progressMs = progressMs,
      durationMs = track.durationMs ?: 0L,
      isPlaying = response.isPlaying,
      observedAt = observedAt,
      startTime = observedAt - progressMs.milliseconds,
      albumId = track.album?.id?.let { AlbumId(it) },
    )
  }

  override fun getRecentlyPlayed(userId: UserId, accessToken: AccessToken, after: Instant?): Either<DomainError, List<RecentlyPlayedItem>> {
    return try {
      val allItems = mutableListOf<RecentlyPlayedItem>()
      val afterParam = after?.let { "&after=${it.toEpochMilliseconds()}" } ?: ""
      var nextUrl: String? = "$apiBaseUrl/v1/me/player/recently-played?limit=50$afterParam"
      while (nextUrl != null) {
        val request = HttpRequest.newBuilder()
          .uri(URI.create(nextUrl))
          .header("Authorization", "Bearer ${accessToken.value}")
          .GET()
          .build()
        val response = httpMetrics.timed("/v1/me/player/recently-played") {
          httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val errorResult = response.checkRateLimitOrError(logger, PlaybackError.RECENTLY_PLAYED_FETCH_FAILED)
        if (errorResult != null) return errorResult
        val recentlyPlayed = spotifyJson.decodeFromString<SpotifyRecentlyPlayedResponse>(response.body())
        recentlyPlayed.items.mapNotNullTo(allItems) { item ->
          parseRecentlyPlayedItem(userId, item.track, item.playedAt)
        }
        nextUrl = recentlyPlayed.next
      }
      allItems.right()
    } catch (e: Exception) {
      logger.error(e) { "Unexpected error during recently played fetch" }
      PlaybackError.RECENTLY_PLAYED_FETCH_FAILED.left()
    }
  }

  private fun parseRecentlyPlayedItem(userId: UserId, track: SpotifyTrackResponse, playedAt: String): RecentlyPlayedItem? {
    if (track.type != "track") {
      logger.info { "Ignoring non-track playback event of type '${track.type}'" }
      return null
    }
    if (track.isLocal || track.id == null) {
      logger.info { "Ignoring local or id-less recently played track '${track.name}'" }
      return null
    }
    val durationSeconds = track.durationMs?.let { it / MS_PER_SECOND }
    val playedAtInstant = Instant.parse(playedAt)
    return RecentlyPlayedItem(
      spotifyUserId = userId,
      trackId = TrackId(track.id),
      trackName = track.name,
      artistIds = track.artists.mapNotNull { it.id?.let { id -> ArtistId(id) } },
      artistNames = track.artists.map { it.name },
      playedAt = playedAtInstant,
      startTime = durationSeconds?.let { playedAtInstant - it.seconds },
      albumId = track.album?.id?.let { AlbumId(it) },
      durationSeconds = durationSeconds,
    )
  }

  companion object : KLogging() {
    private const val HTTP_NO_CONTENT = 204
    private const val MS_PER_SECOND = 1_000L
  }
}

