package de.chrgroth.james.platform.domain.port.`in`.app

interface AppDataMigrationPort {
  fun deleteAppsWithoutDeveloperId()
}
