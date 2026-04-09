package de.chrgroth.spotify.control.adapter.`in`.starter

import de.chrgroth.quarkus.starters.domain.Starter
import de.chrgroth.spotify.control.domain.port.`in`.playback.PlaybackAggregationPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class BackfillPlaybackAggregationsStarter(
  private val playbackAggregation: PlaybackAggregationPort,
) : Starter {

  override val id = "BackfillPlaybackAggregationsStarter-v1"

  override fun execute() {
    playbackAggregation.rebuildAllAggregations()
  }
}
