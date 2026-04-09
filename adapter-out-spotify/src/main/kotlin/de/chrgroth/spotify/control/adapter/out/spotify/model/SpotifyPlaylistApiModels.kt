package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SpotifyOwner(val id: String)

@Serializable
internal data class SpotifyPlaylistResponse(
  val id: String,
  val name: String,
  @SerialName("snapshot_id") val snapshotId: String,
  val owner: SpotifyOwner,
)

@Serializable
internal data class SpotifyUserPlaylistsResponse(
  val items: List<SpotifyPlaylistResponse> = emptyList(),
  val next: String? = null,
)

@Serializable
internal data class SpotifyPlaylistTrackObject(
  @SerialName("item") val track: SpotifyTrackResponse? = null,
)

@Serializable
internal data class SpotifyPlaylistTracksResponse(
  val items: List<SpotifyPlaylistTrackObject?> = emptyList(),
  val next: String? = null,
  @SerialName("snapshot_id") val snapshotId: String? = null,
)

@Serializable
internal data class SpotifyRemoveTrackObject(
  val uri: String,
)

@Serializable
internal data class SpotifyRemoveTrackAtPositionObject(
  val uri: String,
  val positions: List<Int>,
)

@Serializable
internal data class SpotifyRemovePlaylistTracksRequest(
  val items: List<SpotifyRemoveTrackObject>,
)

@Serializable
internal data class SpotifyRemovePlaylistTrackAtPositionRequest(
  val items: List<SpotifyRemoveTrackAtPositionObject>,
)

@Serializable
internal data class SpotifyAddPlaylistTracksRequest(
  val uris: List<String>,
  val position: Int? = null,
)
