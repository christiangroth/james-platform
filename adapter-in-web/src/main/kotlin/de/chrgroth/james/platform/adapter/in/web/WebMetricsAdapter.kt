package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.infra.HttpResponseStats
import de.chrgroth.james.platform.domain.port.out.infra.WebMetricsPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class WebMetricsAdapter(
  private val httpResponseMetrics: HttpResponseMetrics,
) : WebMetricsPort {

  override fun getResponseStats(): List<HttpResponseStats> = httpResponseMetrics.getResponseStats()
}
