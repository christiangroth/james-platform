package de.chrgroth.spotify.control.domain.model.infra

data class OutgoingRequestStats(
  val endpoint: String,
  val requestCountLast24h: Long,
)
