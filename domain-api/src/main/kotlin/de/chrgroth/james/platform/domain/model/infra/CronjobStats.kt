package de.chrgroth.james.platform.domain.model.infra

import kotlin.time.Instant

data class CronjobStats(
  val simpleName: String,
  val cronSchedule: String,
  val nextExecution: Instant?,
  val running: Boolean,
)
