package de.chrgroth.spotify.control.domain.port.out.user

import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.UserId

interface SpotifyAccessTokenPort {
  fun getValidAccessToken(userId: UserId): AccessToken
}
