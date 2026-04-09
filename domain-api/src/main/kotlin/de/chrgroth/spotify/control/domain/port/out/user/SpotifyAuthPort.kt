package de.chrgroth.spotify.control.domain.port.out.user

import arrow.core.Either
import de.chrgroth.spotify.control.domain.error.DomainError
import de.chrgroth.spotify.control.domain.model.user.AccessToken
import de.chrgroth.spotify.control.domain.model.user.RefreshToken
import de.chrgroth.spotify.control.domain.model.user.SpotifyProfile
import de.chrgroth.spotify.control.domain.model.user.SpotifyRefreshedTokens
import de.chrgroth.spotify.control.domain.model.user.SpotifyTokens

interface SpotifyAuthPort {
  fun exchangeCode(code: String): Either<DomainError, SpotifyTokens>
  fun getUserProfile(accessToken: AccessToken): Either<DomainError, SpotifyProfile>
  fun refreshToken(refreshToken: RefreshToken): Either<DomainError, SpotifyRefreshedTokens>
}
