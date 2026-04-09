package de.chrgroth.spotify.control.domain.model.user

@JvmInline
value class SpotifyProfileId(val value: String)

data class SpotifyProfile(val id: SpotifyProfileId, val displayName: String)
