package de.chrgroth.james.platform.domain.port.out.infra

import de.chrgroth.james.platform.domain.model.infra.HttpResponseStats

interface WebMetricsPort {
  fun getResponseStats(): List<HttpResponseStats>
}
