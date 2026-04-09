package de.chrgroth.spotify.control.domain.model.user

@JvmInline
value class AccessToken(val value: String)

@JvmInline
value class RefreshToken(val value: String)

data class SpotifyTokens(val accessToken: AccessToken, val refreshToken: RefreshToken, val expiresInSeconds: Int)
