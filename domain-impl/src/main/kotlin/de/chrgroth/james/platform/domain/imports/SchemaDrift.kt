package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.model.imports.SchemaProperty

/**
 * Compares two [SchemaProperty] lists by property path only (not by type/mandatory-ness/statistics, which naturally
 * vary from run to run with the underlying data) to decide whether a scheduled run's source structure has changed
 * since the baseline it was last allowed to accept against (see `ImportService.triggerScheduledImport`).
 */
object SchemaDrift {
  fun detected(baseline: List<SchemaProperty>, current: List<SchemaProperty>): Boolean =
    baseline.map { it.path }.toSet() != current.map { it.path }.toSet()
}
