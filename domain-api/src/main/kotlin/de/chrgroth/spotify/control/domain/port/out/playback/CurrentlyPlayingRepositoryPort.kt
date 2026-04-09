package de.chrgroth.spotify.control.domain.port.out.playback

import de.chrgroth.spotify.control.domain.model.catalog.TrackId
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.user.UserId

interface CurrentlyPlayingRepositoryPort {
  fun save(item: CurrentlyPlayingItem)
  fun findMostRecentByUserAndTrack(userId: UserId, trackId: TrackId): CurrentlyPlayingItem?
  fun updateProgress(item: CurrentlyPlayingItem)
  fun findByUserId(userId: UserId): List<CurrentlyPlayingItem>
  fun deleteByUserIdAndTrackIds(userId: UserId, trackIds: Set<String>)
}
