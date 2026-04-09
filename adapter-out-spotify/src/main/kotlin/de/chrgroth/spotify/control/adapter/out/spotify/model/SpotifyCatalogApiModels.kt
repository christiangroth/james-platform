package de.chrgroth.spotify.control.adapter.out.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SpotifyImage(val url: String)

@Serializable
internal data class SpotifyArtistRef(val id: String?, val name: String)

@Serializable
internal data class SpotifyArtistResponse(
  val id: String,
  val name: String,
  val images: List<SpotifyImage> = emptyList(),
  val type: String? = null,
)

@Serializable
internal data class SpotifyAlbumRef(
  val id: String?,
  val name: String,
  val images: List<SpotifyImage> = emptyList(),
  @SerialName("release_date") val releaseDate: String? = null,
  @SerialName("release_date_precision") val releaseDatePrecision: String? = null,
  @SerialName("album_type") val albumType: String? = null,
  @SerialName("total_tracks") val totalTracks: Int? = null,
  val artists: List<SpotifyArtistRef> = emptyList(),
)

@Serializable
internal data class SpotifyTrackResponse(
  val id: String?,
  val name: String,
  val type: String,
  @SerialName("is_local") val isLocal: Boolean = false,
  val artists: List<SpotifyArtistRef> = emptyList(),
  val album: SpotifyAlbumRef? = null,
  @SerialName("duration_ms") val durationMs: Long? = null,
  @SerialName("disc_number") val discNumber: Int? = null,
  @SerialName("track_number") val trackNumber: Int? = null,
)

@Serializable
internal data class SpotifySimplifiedTrackResponse(
  val id: String? = null,
  val name: String,
  val type: String,
  @SerialName("is_local") val isLocal: Boolean = false,
  val artists: List<SpotifyArtistRef> = emptyList(),
  @SerialName("duration_ms") val durationMs: Long? = null,
  @SerialName("disc_number") val discNumber: Int? = null,
  @SerialName("track_number") val trackNumber: Int? = null,
)

@Serializable
internal data class SpotifyAlbumTracksPage(
  val items: List<SpotifySimplifiedTrackResponse?> = emptyList(),
  val next: String? = null,
  val total: Int = 0,
)

@Serializable
internal data class SpotifyAlbumResponse(
  val id: String,
  val name: String,
  val images: List<SpotifyImage> = emptyList(),
  @SerialName("release_date") val releaseDate: String? = null,
  @SerialName("release_date_precision") val releaseDatePrecision: String? = null,
  @SerialName("album_type") val albumType: String? = null,
  @SerialName("total_tracks") val totalTracks: Int? = null,
  val artists: List<SpotifyArtistRef> = emptyList(),
  val tracks: SpotifyAlbumTracksPage = SpotifyAlbumTracksPage(),
)

@Serializable
internal data class SpotifyArtistAlbumRef(
  val id: String,
  @SerialName("album_type") val albumType: String? = null,
)

@Serializable
internal data class SpotifyArtistAlbumsPage(
  val items: List<SpotifyArtistAlbumRef> = emptyList(),
  val next: String? = null,
  val total: Int = 0,
)


