package de.chrgroth.james.platform.domain.port.`in`.user

interface UserDataMigrationPort {
  fun backfillCreatedAtAndActive()
}
