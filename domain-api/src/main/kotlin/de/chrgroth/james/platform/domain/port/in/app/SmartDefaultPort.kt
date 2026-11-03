package de.chrgroth.james.platform.domain.port.`in`.app

import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import kotlin.time.Instant

/**
 * What smart default expressions (ADR 0029) read besides the static defaults: `today()`/`now()` at [now] (application time zone), the values
 * already entered in the form by property id ([formValues], when the form re-evaluates while being filled; they win over defaults), the entity's
 * existing objects for `last(field)` and `next_day(field)` ([entries], only loaded if an expression needs them) and `config.<field>` from [config]
 * (configuration values by property id) typed by [configEntity].
 */
class SmartDefaultContext(
  val now: Instant,
  val formValues: Map<String, String?> = emptyMap(),
  val entries: () -> List<AppData> = { emptyList() },
  val config: Map<String, String?> = emptyMap(),
  val configEntity: EntityDefinition? = null,
)

interface SmartDefaultPort {
  /** The smart default values of [entity]'s top-level properties by property id, evaluated in property order; null where there is no value. */
  fun computeSmartDefaults(entity: EntityDefinition, context: SmartDefaultContext): Map<String, String?>

  /**
   * For each top-level property of [entity] whose smart default reads other fields of the form (not via `last`/`next_day`): the ids of those
   * properties, so the form can re-evaluate the smart default when one of them changes.
   */
  fun formDependencies(entity: EntityDefinition): Map<String, Set<String>>
}
