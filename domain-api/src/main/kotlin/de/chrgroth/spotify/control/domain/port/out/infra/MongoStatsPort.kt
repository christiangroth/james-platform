package de.chrgroth.spotify.control.domain.port.out.infra

import de.chrgroth.spotify.control.domain.model.infra.MongoCollectionStats
import de.chrgroth.spotify.control.domain.model.infra.MongoQueryStats

interface MongoStatsPort {
  fun getCollectionStats(): List<MongoCollectionStats>
  fun getQueryStats(): List<MongoQueryStats>
}
