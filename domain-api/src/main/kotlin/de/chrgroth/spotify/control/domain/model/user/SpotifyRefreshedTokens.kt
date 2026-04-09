package de.chrgroth.spotify.control.domain.model.user

data class SpotifyRefreshedTokens(
  val accessToken: AccessToken,
  val refreshToken: RefreshToken?,
  val expiresInSeconds: Int,
)
