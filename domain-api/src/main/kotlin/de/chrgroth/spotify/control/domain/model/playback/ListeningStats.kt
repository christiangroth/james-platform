package de.chrgroth.spotify.control.domain.model.playback

data class ListeningStats(
  val listenedMinutesLast30Days: Long,
  val topTracksLast30Days: List<TopEntry>,
  val topArtistsLast30Days: List<TopEntry>,
)
