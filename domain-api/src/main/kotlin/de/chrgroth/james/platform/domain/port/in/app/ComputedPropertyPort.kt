package de.chrgroth.james.platform.domain.port.`in`.app

import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import kotlin.time.Instant

interface ComputedPropertyPort {
  /**
   * Evaluates the computed property expressions of [entity] for one object ([data], raw values by property id) in definition order, so a computed
   * property can read the ones defined before it (ADR 0029). Expressions read the object's fields by name (nested OBJECT fields as
   * `object.field`, references as the referenced object's id), `config.<field>` from [config] (the installation's configuration values by
   * property id, empty without configuration) typed by [configEntity], `today()` and `now()` at [now] in the application's time zone. Results
   * are raw storage values by computed property id; a computed property without (valid) expression or whose expression has no value is null.
   */
  fun computeValues(
    entity: EntityDefinition,
    data: Map<String, String?>,
    now: Instant,
    config: Map<String, String?> = emptyMap(),
    configEntity: EntityDefinition? = null,
  ): Map<String, String?>
}
