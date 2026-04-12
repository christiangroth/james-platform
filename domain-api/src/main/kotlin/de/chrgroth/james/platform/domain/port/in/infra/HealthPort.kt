package de.chrgroth.james.platform.domain.port.`in`.infra

import de.chrgroth.james.platform.domain.model.infra.HealthStats

interface HealthPort {
  fun getStats(): HealthStats
}
