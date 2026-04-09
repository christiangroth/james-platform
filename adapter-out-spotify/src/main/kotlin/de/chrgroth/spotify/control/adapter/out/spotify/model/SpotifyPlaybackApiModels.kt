package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SpotifyCurrentlyPlayingResponse(
  val item: SpotifyTrackResponse? = null,
  @SerialName("progress_ms") val progressMs: Long? = null,
  @SerialName("is_playing") val isPlaying: Boolean = false,
)

@Serializable
internal data class SpotifyPlayHistoryObject(
  val track: SpotifyTrackResponse,
  @SerialName("played_at") val playedAt: String,
)

@Serializable
internal data class SpotifyRecentlyPlayedResponse(
  val items: List<SpotifyPlayHistoryObject> = emptyList(),
  val next: String? = null,
)
