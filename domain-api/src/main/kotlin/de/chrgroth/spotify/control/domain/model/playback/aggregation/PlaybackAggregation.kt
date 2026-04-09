package de.chrgroth.spotify.control.domain.model.playback.aggregation

import de.chrgroth.spotify.control.domain.model.user.UserId
import kotlinx.datetime.LocalDate

data class PlaybackAggregation(
  val userId: UserId,
  val type: AggregationPeriodType,
  val periodStart: LocalDate,
  val totalPlaybackSeconds: Long,
  val distinctArtistCount: Int,
  val distinctTrackCount: Int,
  val artistEntries: List<AggregationRankEntry>,
  val trackEntries: List<AggregationRankEntry>,
  val activityEntries: List<ActivityEntry>,
)
