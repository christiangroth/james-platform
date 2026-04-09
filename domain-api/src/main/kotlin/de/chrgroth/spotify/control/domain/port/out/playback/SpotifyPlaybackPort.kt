package de.chrgroth.spotify.control.domain.port.out.playback

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.playback.CurrentlyPlayingItem
import de.chrgroth.spotify.control.domain.model.playback.RecentlyPlayedItem
import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlin.time.Instant

interface SpotifyPlaybackPort {
  fun getCurrentlyPlaying(userId: UserId, accessToken: AccessToken): Either<DomainError, CurrentlyPlayingItem?>
  fun getRecentlyPlayed(userId: UserId, accessToken: AccessToken, after: Instant? = null): Either<DomainError, List<RecentlyPlayedItem>>
}
