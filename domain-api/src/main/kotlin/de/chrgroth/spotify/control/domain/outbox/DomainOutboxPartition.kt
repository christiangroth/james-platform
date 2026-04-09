package de.chrgroth.spotify.control.domain.outbox

import de.chrgroth.quarkus.outbox.domain.ApplicationOutboxPartition

sealed interface DomainOutboxPartition : ApplicationOutboxPartition {
  data object ToSpotify : DomainOutboxPartition {
    override val key = "to-spotify"
  }

  data object ToSpotifyPlayback : DomainOutboxPartition {
    override val key = "to-spotify-playback"
  }

  data object Domain : DomainOutboxPartition {
    override val key = "domain"
  }

  companion object {
    val all: List<DomainOutboxPartition> = listOf(ToSpotify, ToSpotifyPlayback, Domain)
  }
}
