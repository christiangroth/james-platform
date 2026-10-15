package de.chrgroth.james.platform.adapter.`in`.web

import io.micrometer.core.instrument.config.MeterFilter
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces

/**
 * The MongoDB driver tags its command and pool metrics with a random cluster.id that changes on every start, so each
 * deploy created a whole new set of series (high churn in Grafana Cloud's active-series count). Ignoring the tag merges
 * the meters of all clients into one stable series per command/pool.
 */
@ApplicationScoped
class MongoClusterIdMeterFilter {

  @Produces
  fun ignoreMongoClusterIdFilter(): MeterFilter = MeterFilter.ignoreTags(CLUSTER_ID_TAG)

  companion object {
    const val CLUSTER_ID_TAG = "cluster.id"
  }
}
