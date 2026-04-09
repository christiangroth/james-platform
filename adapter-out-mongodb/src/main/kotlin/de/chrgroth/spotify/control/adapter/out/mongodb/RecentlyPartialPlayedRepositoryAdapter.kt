package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.catalog.AlbumId
import de.chrgroth.spotify.control.domain.model.catalog.ArtistId
import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPartialPlayedItem
import de.chrgroth.spotify.control.domain.model.user.UserId
import de.chrgroth.spotify.control.domain.port.out.playback.RecentlyPartialPlayedRepositoryPort
import jakarta.enterprise.context.ApplicationScoped
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant
import mu.KLogging

@ApplicationScoped
class RecentlyPartialPlayedRepositoryAdapter(
  private val recentlyPartialPlayedDocumentRepository: RecentlyPartialPlayedDocumentRepository,
  private val mongoQueryMetrics: MongoQueryMetrics,
) : RecentlyPartialPlayedRepositoryPort {

  override fun findExistingPlayedAts(userId: UserId, playedAts: Set<Instant>): Set<Instant> {
    if (playedAts.isEmpty()) return emptySet()
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    return mongoQueryMetrics.timed("recently_partial_played.findExistingPlayedAts") {
      recentlyPartialPlayedDocumentRepository
        .list("spotifyUserId = ?1 and playedAt in ?2", userId.value, javaPlayedAts)
        .map { it.playedAt.toKotlinInstant() }
        .toSet()
    }
  }

  override fun findSince(userId: UserId, since: Instant?): List<RecentlyPartialPlayedItem> =
    mongoQueryMetrics.timed("recently_partial_played.findSince") {
      val query = if (since != null) {
        recentlyPartialPlayedDocumentRepository.list(
          "spotifyUserId = ?1 and playedAt > ?2",
          userId.value,
          since.toJavaInstant(),
        )
      } else {
        recentlyPartialPlayedDocumentRepository.list("spotifyUserId = ?1", userId.value)
      }
      query.map { doc -> doc.toItem() }
    }

  override fun findByUserIdAndTrackIds(userId: UserId, trackIds: Set<TrackId>): List<RecentlyPartialPlayedItem> {
    if (trackIds.isEmpty()) return emptyList()
    val trackIdValues = trackIds.map { it.value }
    return mongoQueryMetrics.timed("recently_partial_played.findByUserIdAndTrackIds") {
      recentlyPartialPlayedDocumentRepository
        .list("spotifyUserId = ?1 and trackId in ?2", userId.value, trackIdValues)
        .map { doc -> doc.toItem() }
    }
  }

  override fun saveAll(items: List<RecentlyPartialPlayedItem>) {
    if (items.isEmpty()) return
    val documents = items.map { item ->
      RecentlyPartialPlayedDocument().apply {
        spotifyUserId = item.spotifyUserId.value
        trackId = item.trackId.value
        trackName = item.trackName
        artistIds = item.artistIds.map { it.value }
        artistNames = item.artistNames
        playedAt = item.playedAt.toJavaInstant()
        startTime = item.startTime.toJavaInstant()
        playedSeconds = item.playedSeconds
        albumId = item.albumId?.value
      }
    }
    logger.info { "Saving ${documents.size} recently partial played documents" }
    mongoQueryMetrics.timed("recently_partial_played.saveAll") {
      recentlyPartialPlayedDocumentRepository.persist(documents)
    }
  }

  override fun deleteByPlayedAts(userId: UserId, playedAts: Set<Instant>) {
    if (playedAts.isEmpty()) return
    val javaPlayedAts = playedAts.map { it.toJavaInstant() }
    logger.info { "Deleting ${playedAts.size} recently partial played document(s) for user: ${userId.value}" }
    mongoQueryMetrics.timed("recently_partial_played.deleteByPlayedAts") {
      recentlyPartialPlayedDocumentRepository.delete("spotifyUserId = ?1 and playedAt in ?2", userId.value, javaPlayedAts)
    }
  }

  private fun RecentlyPartialPlayedDocument.toItem() = RecentlyPartialPlayedItem(
    spotifyUserId = UserId(spotifyUserId),
    trackId = TrackId(trackId),
    trackName = trackName,
    artistIds = artistIds.map { ArtistId(it) },
    artistNames = artistNames,
    playedAt = playedAt.toKotlinInstant(),
    startTime = startTime.toKotlinInstant(),
    playedSeconds = playedSeconds,
    albumId = albumId?.let { AlbumId(it) },
  )

  companion object : KLogging()
}
