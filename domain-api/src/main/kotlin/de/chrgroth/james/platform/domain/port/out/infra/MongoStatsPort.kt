package de.chrgroth.james.platform.domain.port.out.infra

import de.chrgroth.james.platform.domain.model.infra.MongoCollectionStats
import de.chrgroth.james.platform.domain.model.infra.MongoQueryStats

interface MongoStatsPort {
  fun getCollectionStats(): List<MongoCollectionStats>
  fun getQueryStats(): List<MongoQueryStats>
}
