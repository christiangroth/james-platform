package de.chrgroth.spotify.control.domain.model.playback

data class TopEntry(
  val name: String,
  val totalMinutes: Long,
  val imageLink: String? = null,
)
