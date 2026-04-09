package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPlayedRepositoryPort
import io.quarkus.panache.common.Sort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class RecentlyPlayedRepositoryAdapter(
  private val recentlyPlayedDocumentRepository: RecentlyPlayedDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : RecentlyPlayedRepositoryPort {

  override fun findExistingPlayedAts(spotifyUserId: UserId, playedAts: Set<Instant>): Set<Instant> {
    if (playedAts.isEmpty()) return emptySet()
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    return mongoQueryMetrics.timed("spotify_recently_played.findExistingPlayedAts") {
      recentlyPlayedDocumentRepository
        .list("spotifyUserId = ?1 and playedAt in ?2", spotifyUserId.value, javaPlayedAts)
        .map { it.playedAt.toKotlinInstant() }
        .toSet()
    }
  }

  override fun findMostRecentPlayedAt(spotifyUserId: UserId): Instant? =
    mongoQueryMetrics.timed("spotify_recently_played.findMostRecentPlayedAt") {
      recentlyPlayedDocumentRepository
        .find("spotifyUserId = ?1", Sort.by("playedAt").descending(), spotifyUserId.value)
        .firstResult()
        ?.playedAt?.toKotlinInstant()
    }

  override fun findSince(spotifyUserId: UserId, since: Instant?): List<RecentlyPlayedItem> =
    mongoQueryMetrics.timed("spotify_recently_played.findSince") {
      val query = if (since != null) {
        recentlyPlayedDocumentRepository.list(
          "spotifyUserId = ?1 and playedAt > ?2",
          spotifyUserId.value,
          since.toJavaInstant(),
        )
      } else {
        recentlyPlayedDocumentRepository.list("spotifyUserId = ?1", spotifyUserId.value)
      }
      query.map { doc ->
        RecentlyPlayedItem(
          spotifyUserId = UserId(doc.spotifyUserId),
          trackId = TrackId(doc.trackId),
          trackName = doc.trackName,
          artistIds = doc.artistIds.map { ArtistId(it) },
          artistNames = doc.artistNames,
          playedAt = doc.playedAt.toKotlinInstant(),
          startTime = doc.startTime?.toKotlinInstant(),
          albumId = doc.albumId?.let { AlbumId(it) },
          durationSeconds = doc.durationSeconds,
        )
      }
    }

  override fun saveAll(items: List<RecentlyPlayedItem>) {
    if (items.isEmpty()) return
    val documents = items.map { item ->
      RecentlyPlayedDocument().apply {
        spotifyUserId = item.spotifyUserId.value
        trackId = item.trackId.value
        trackName = item.trackName
        artistIds = item.artistIds.map { it.value }
        artistNames = item.artistNames
        playedAt = item.playedAt.toJavaInstant()
        startTime = item.startTime?.toJavaInstant()
        albumId = item.albumId?.value
        durationSeconds = item.durationSeconds ?: 0L
      }
    }
    logger.info { "Saving ${documents.size} recently played documents" }
    mongoQueryMetrics.timed("spotify_recently_played.saveAll") {
      recentlyPlayedDocumentRepository.persist(documents)
    }
  }

  override fun deleteNonTracks(): Long =
    mongoQueryMetrics.timed("spotify_recently_played.deleteNonTracks") {
      recentlyPlayedDocumentRepository.delete("artistIds = ?1 and artistNames = ?2", emptyList<String>(), emptyList<String>())
    }

  companion object : KLogging()
}

