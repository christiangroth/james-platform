package de.chrgroth.james.platform.domain.port.`in`.imports

interface ImportSchedulePort {
  /**
   * Loads every [de.chrgroth.james.platform.domain.model.imports.ImportDefinition] with a
   * [de.chrgroth.james.platform.domain.model.imports.ImportDefinition.schedule] set, evaluates each one's cron
   * expression against [de.chrgroth.james.platform.domain.model.imports.ImportDefinition.lastRunAt], and triggers
   * an unattended run (see `ImportPort.triggerScheduledImport`) for every due one. Returns the number of definitions
   * found due, regardless of whether their individual run succeeded.
   */
  fun runDueScheduledImports(): Int
}
