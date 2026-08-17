package de.chrgroth.james.platform.domain.port.`in`.imports

interface ImportJobDataMigrationPort {
  fun migrateLongToDurationFieldMappingConversion()
}
