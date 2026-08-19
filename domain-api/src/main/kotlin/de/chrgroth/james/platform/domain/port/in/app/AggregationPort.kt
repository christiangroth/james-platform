package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

/** See docs/adr/0020-aggregation-definitions.md - the outbox-driven, full-scan recomputation side of aggregation values. */
interface AggregationPort {
  fun handle(event: DomainOutboxEvent.RecomputeAggregation): Either<DomainError, Unit>
}
