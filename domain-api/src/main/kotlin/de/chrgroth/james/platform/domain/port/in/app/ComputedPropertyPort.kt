package de.chrgroth.james.platform.domain.port.`in`.app

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import kotlin.time.Instant

interface ComputedPropertyPort {
  /**
   * Evaluates the computed property scripts of [entity] for one object. Scripts see the bindings `it` ([data]), `computed` (results of the
   * computed properties evaluated before), `now` and `config` ([config], the installation's configuration values keyed by property id like
   * `it`, empty without configuration - see ADR 0027).
   */
  fun computeValues(entity: EntityDefinition, data: Map<String, String?>, now: Instant, config: Map<String, String?> = emptyMap()): Map<String, String?>
}
