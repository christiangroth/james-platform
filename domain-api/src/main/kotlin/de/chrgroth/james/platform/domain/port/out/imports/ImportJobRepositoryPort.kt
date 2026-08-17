package de.chrgroth.james.platform.domain.port.out.imports

import de.chrgroth.james.platform.domain.model.imports.ImportJob
import de.chrgroth.james.platform.domain.model.imports.ImportJobId
import java.time.Instant

interface ImportJobRepositoryPort {
  fun findAllByUserId(userId: String): List<ImportJob>
  fun findById(id: ImportJobId): ImportJob?
  fun save(importJob: ImportJob)
  fun delete(id: ImportJobId)
  fun deleteAllLastChangedBefore(cutoff: Instant): Long

  /**
   * One-time migration (see ADR 0017): rewrites every stored field mapping whose `conversion` is the removed
   * `LONG_TO_DURATION` value (target property was `DURATION`, now migrated to `LONG`) to `NONE`, since a `LONG`
   * source mapped to a `LONG` target needs no conversion. Without this, [findAllByUserId]/[findById] fail with
   * `IllegalArgumentException` on any stored import job whose mapping still references the removed enum constant.
   */
  fun migrateLongToDurationFieldMappingConversion()
}
