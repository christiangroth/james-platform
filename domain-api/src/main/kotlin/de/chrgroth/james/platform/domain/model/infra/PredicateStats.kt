package de.chrgroth.james.platform.domain.model.infra

import kotlin.time.Instant

data class PredicateStats(
  val name: String,
  val active: Boolean,
  val lastCheck: Instant? = null,
)
