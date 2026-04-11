package de.chrgroth.james.platform.domain.model.playback.aggregation

import java.time.DayOfWeek

data class ActivityEntry(
  val dayOfWeek: DayOfWeek,
  val timeWindow: ActivityTimeWindow,
  val totalSeconds: Long,
)
