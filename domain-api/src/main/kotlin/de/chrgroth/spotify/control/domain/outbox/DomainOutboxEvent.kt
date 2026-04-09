package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxEvent
import de.chrgroth.quarkus.outbox.domain.OutboxEventPriority
import de.chrgroth.spotify.control.domain.model.playback.aggregation.AggregationPeriodType
import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlinx.datetime.LocalDate

sealed interface DomainOutboxEvent : ApplicationOutboxEvent {
  override val partition: DomainOutboxPartition
  override val priority: OutboxEventPriority get() = OutboxEventPriority.MEDIUM
  override val serializePayload: String

  data class FetchPlaybackData(val userId: UserId) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:${userId.value}"
    override val partition = DomainOutboxPartition.ToSpotifyPlayback
    override val priority = OutboxEventPriority.HIGH
    override val serializePayload = userId.value

    companion object {
      const val KEY = "FetchPlaybackData"
    }
  }

  data class UpdateUserProfile(val userId: UserId) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:${userId.value}"
    override val partition = DomainOutboxPartition.ToSpotify
    override val serializePayload = userId.value

    companion object {
      const val KEY = "UpdateUserProfile"
    }
  }

  data class SyncPlaylistInfo(val userId: UserId) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:${userId.value}"
    override val partition = DomainOutboxPartition.ToSpotify
    override val serializePayload = userId.value

    companion object {
      const val KEY = "SyncPlaylistInfo"
    }
  }

  /**
   * Syncs track data for a specific page of a playlist.
   * [nextUrl] is the Spotify API URL for the page to fetch; `null` means fetch the first page.
   * [snapshotId] is the Spotify snapshot ID observed when the previous page was fetched; `null` for the first page.
   * If the fetched page's snapshot ID differs from [snapshotId], the sync restarts from the first page.
   * The deduplication key includes both [snapshotId] and [nextUrl] so that each page+snapshot combination
   * can be queued independently while retries of the same page are still correctly deduplicated.
   * payload: "${userId.value}:$playlistId" for the first page;
   *          "${userId.value}:$playlistId\n$snapshotId\n$nextUrl" for subsequent pages.
   * Legacy payload (no snapshotId): "${userId.value}:$playlistId\n$nextUrl" — parsed with snapshotId=null.
   */
  data class SyncPlaylistData(val userId: UserId, val playlistId: String, val nextUrl: String? = null, val snapshotId: String? = null) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:${userId.value}:$playlistId:${snapshotId ?: ""}:${nextUrl ?: ""}"
    override val partition = DomainOutboxPartition.ToSpotify
    override val serializePayload = when {
      nextUrl == null -> "${userId.value}:$playlistId"
      snapshotId != null -> "${userId.value}:$playlistId\n$snapshotId\n$nextUrl"
      else -> "${userId.value}:$playlistId\n$nextUrl"
    }

    companion object {
      const val KEY = "SyncPlaylistData"
      fun fromPayload(payload: String): SyncPlaylistData {
        val colonIndex = payload.indexOf(':')
        require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid SyncPlaylistData payload: $payload" }
        val userId = UserId(payload.substring(0, colonIndex))
        val rest = payload.substring(colonIndex + 1)
        val firstNewline = rest.indexOf('\n')
        return if (firstNewline < 0) {
          SyncPlaylistData(userId, rest)
        } else {
          val playlistId = rest.substring(0, firstNewline)
          val afterFirst = rest.substring(firstNewline + 1)
          val secondNewline = afterFirst.indexOf('\n')
          if (secondNewline < 0) {
            // Legacy format: no snapshotId
            SyncPlaylistData(userId, playlistId, afterFirst)
          } else {
            // New format: snapshotId\nnextUrl
            val snapshotId = afterFirst.substring(0, secondNewline).takeIf { it.isNotEmpty() }
            val nextUrl = afterFirst.substring(secondNewline + 1)
            SyncPlaylistData(userId, playlistId, nextUrl, snapshotId)
          }
        }
      }
    }
  }

  data class RebuildPlaybackData(val userId: UserId) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:${userId.value}"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = userId.value

    companion object {
      const val KEY = "RebuildPlaybackData"
    }
  }

  data class AppendPlaybackData(val userId: UserId) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:${userId.value}"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = userId.value

    companion object {
      const val KEY = "AppendPlaybackData"
    }
  }

  /**
   * Syncs genres and images for a single artist from the Spotify API and updates app_artist.
   * Deduplication is by artistId only (artist data is shared across users).
   * payload = "$artistId:${userId.value}"
   */
  data class SyncArtistDetails(val artistId: String, val userId: UserId) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$artistId"
    override val partition = DomainOutboxPartition.ToSpotify
    override val serializePayload = "$artistId:${userId.value}"

    companion object {
      const val KEY = "SyncArtistDetails"
      const val LEGACY_KEY = "EnrichArtistDetails"
      fun fromPayload(payload: String): SyncArtistDetails {
        val colonIndex = payload.indexOf(':')
        require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid SyncArtistDetails payload: $payload" }
        return SyncArtistDetails(
          artistId = payload.substring(0, colonIndex),
          userId = UserId(payload.substring(colonIndex + 1)),
        )
      }
    }
  }

  /**
   * Syncs a single album by fetching all its tracks via GET /v1/albums/{id}.
   * All returned tracks are upserted. Enqueues SyncArtistDetails for all artists found.
   * payload = albumId
   */
  data class SyncAlbumDetails(val albumId: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$albumId"
    override val partition = DomainOutboxPartition.ToSpotify
    override val serializePayload = albumId

    companion object {
      const val KEY = "SyncAlbumDetails"
      fun fromPayload(payload: String): SyncAlbumDetails = SyncAlbumDetails(payload)
    }
  }

  /**
   * Syncs all album IDs for a single artist from the Spotify API and enqueues
   * SyncAlbumDetails for any albums not yet in the catalog.
   * Deduplication is by artistId only (album data is shared across users).
   * payload = "$artistId:${userId.value}"
   */
  data class SyncArtistAlbums(val artistId: String, val userId: UserId) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:$artistId"
    override val partition = DomainOutboxPartition.ToSpotify
    override val serializePayload = "$artistId:${userId.value}"

    companion object {
      const val KEY = "SyncArtistAlbums"
      fun fromPayload(payload: String): SyncArtistAlbums {
        val colonIndex = payload.indexOf(':')
        require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid SyncArtistAlbums payload: $payload" }
        return SyncArtistAlbums(
          artistId = payload.substring(0, colonIndex),
          userId = UserId(payload.substring(colonIndex + 1)),
        )
      }
    }
  }

  /**
   * Re-enqueues sync events for all known artists, tracks, and albums in the catalog
   * so that they are refreshed from Spotify.
   * Deduplication ensures only one instance is queued at a time.
   */
  data class ResyncCatalog(val placeholder: String = "") : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = KEY
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = ""

    companion object {
      const val KEY = "ResyncCatalog"
    }
  }

  /**
   * Runs all playlist checks for a given user's playlist.
   * payload = "${userId.value}:$playlistId"
   */
  data class RunPlaylistChecks(val userId: UserId, val playlistId: String) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:${userId.value}:$playlistId"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = "${userId.value}:$playlistId"

    companion object {
      const val KEY = "RunPlaylistChecks"
      fun fromPayload(payload: String): RunPlaylistChecks {
        val colonIndex = payload.indexOf(':')
        require(colonIndex > 0 && colonIndex < payload.length - 1) { "Invalid RunPlaylistChecks payload: $payload" }
        return RunPlaylistChecks(UserId(payload.substring(0, colonIndex)), payload.substring(colonIndex + 1))
      }
    }
  }

  /**
   * Triggers aggregation of playback data for a specific period and user.
   * payload = "${userId.value}:${type.name}:${periodStart}"
   */
  data class AggregatePlaybackData(val userId: UserId, val type: AggregationPeriodType, val periodStart: LocalDate) : DomainOutboxEvent {
    override val key = KEY
    override val deduplicationKey = "$KEY:${userId.value}:${type.name}:$periodStart"
    override val partition = DomainOutboxPartition.Domain
    override val serializePayload = "${userId.value}:${type.name}:$periodStart"

    companion object {
      const val KEY = "AggregatePlaybackData"
      fun fromPayload(payload: String): AggregatePlaybackData {
        val firstColon = payload.indexOf(':')
        require(firstColon > 0) { "Invalid AggregatePlaybackData payload: $payload" }
        val secondColon = payload.indexOf(':', firstColon + 1)
        require(secondColon > firstColon) { "Invalid AggregatePlaybackData payload: $payload" }
        return AggregatePlaybackData(
          userId = UserId(payload.substring(0, firstColon)),
          type = AggregationPeriodType.valueOf(payload.substring(firstColon + 1, secondColon)),
          periodStart = LocalDate.parse(payload.substring(secondColon + 1)),
        )
      }
    }
  }

  companion object {
    @Suppress("CyclomaticComplexMethod")
    fun fromKey(key: String, payload: String): DomainOutboxEvent = when (key) {
      FetchPlaybackData.KEY -> FetchPlaybackData(UserId(payload))
      UpdateUserProfile.KEY -> UpdateUserProfile(UserId(payload))
      SyncPlaylistInfo.KEY -> SyncPlaylistInfo(UserId(payload))
      SyncPlaylistData.KEY -> SyncPlaylistData.fromPayload(payload)
      RebuildPlaybackData.KEY -> RebuildPlaybackData(UserId(payload))
      AppendPlaybackData.KEY -> AppendPlaybackData(UserId(payload))
      SyncArtistDetails.KEY, SyncArtistDetails.LEGACY_KEY -> SyncArtistDetails.fromPayload(payload)
      SyncArtistAlbums.KEY -> SyncArtistAlbums.fromPayload(payload)
      SyncAlbumDetails.KEY -> SyncAlbumDetails.fromPayload(payload)
      ResyncCatalog.KEY -> ResyncCatalog()
      RunPlaylistChecks.KEY -> RunPlaylistChecks.fromPayload(payload)
      AggregatePlaybackData.KEY -> AggregatePlaybackData.fromPayload(payload)
      else -> throw IllegalArgumentException("Unknown outbox event type: $key")
    }
  }
}
