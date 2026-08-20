package de.chrgroth.james.platform.domain.model.imports

import de.chrgroth.james.platform.domain.model.app.EntityDefinitionId
import java.time.Instant

@JvmInline
value class ImportDefinitionId(val value: String)

/**
 * The reusable, user-owned configuration a job's fetch-to-mapping pipeline is built from: which [ImportConnection]
 * (and, via [urlPostfix], which endpoint on it) to fetch from, which [targetEntityDefinitionId] and
 * [selectedDataPath] to import into and read records from, and how to [filterRules]/[mapping] them once fetched.
 * Unlike an [ImportJob], a definition is independent of any single run's payload/schema snapshot and survives an
 * accepted job (see `ImportService.handle`) - see docs/adr/0021-import-definition-job-split.md.
 */
data class ImportDefinition(
  val id: ImportDefinitionId,
  val userId: String,
  val connectionId: ImportConnectionId,
  val name: String,
  val urlPostfix: String? = null,
  val targetEntityDefinitionId: EntityDefinitionId,
  val selectedDataPath: String? = null,
  val filterRules: List<FilterRule> = emptyList(),
  val mapping: Mapping? = null,
  val createdAt: Instant,
  val lastChangedAt: Instant,
)
