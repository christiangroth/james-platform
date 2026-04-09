package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SpotifyTokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("refresh_token") val refreshToken: String? = null,
  @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
internal data class SpotifyUserProfileResponse(
  val id: String,
  @SerialName("display_name") val displayName: String,
)
