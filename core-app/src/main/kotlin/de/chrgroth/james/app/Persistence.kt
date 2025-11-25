package de.chrgroth.james.app

import arrow.core.ValidatedNel
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.DomainError
import java.util.UUID

interface AppQueryPersistencePort {
  fun getOrError(id: UUID): ValidatedNel<DomainError, App>
  fun getOrError(id: UUID, version: Semver): ValidatedNel<DomainError, AppVersion>

  // TODO #10 what about paging and how to design filter parameters??
  fun find(): ValidatedNel<DomainError, Set<App>>
}

interface AppCommandPersistencePort {
  fun upsert(item: App): ValidatedNel<DomainError, App>
  fun delete(id: UUID): ValidatedNel<DomainError, Unit>
}
