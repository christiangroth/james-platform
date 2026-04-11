package de.chrgroth.james.platform.domain.port.`in`.playlist

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.playlist.PlaylistInfo
import de.chrgroth.james.platform.domain.model.playlist.PlaylistSyncStatus
import de.chrgroth.james.platform.domain.model.playlist.PlaylistType
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

interface PlaylistPort {
  fun getPlaylists(userId: UserId): List<PlaylistInfo>
  fun getTrackCounts(userId: UserId): Map<String, Int>
  fun enqueueUpdates()
  fun syncPlaylists(userId: UserId): Either<DomainError, Unit>
  fun syncPlaylistData(userId: UserId, playlistId: String, nextUrl: String? = null, snapshotId: String? = null): Either<DomainError, Unit>
  fun updateSyncStatus(userId: UserId, playlistId: String, syncStatus: PlaylistSyncStatus): Either<DomainError, Unit>
  fun updatePlaylistType(userId: UserId, playlistId: String, type: PlaylistType): Either<DomainError, Unit>
  fun enqueueSyncPlaylistData(userId: UserId, playlistId: String): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.SyncPlaylistInfo): Either<DomainError, Unit>
  fun handle(event: DomainOutboxEvent.SyncPlaylistData): Either<DomainError, Unit>
}
