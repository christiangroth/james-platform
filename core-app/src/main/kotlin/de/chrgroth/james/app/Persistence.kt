package de.chrgroth.james.app

import arrow.core.ValidatedNel
import com.github.glwithu06.semver.Semver
import de.chrgroth.james.Error
import java.util.UUID

interface AppQueryPersistencePort {
    fun getOrError(id: UUID): ValidatedNel<Error, App>
    fun getOrError(id: UUID, version: Semver): ValidatedNel<Error, AppVersion>

    // TODO #16 what about paging and how to design filter parameters??
    fun find(): ValidatedNel<Error, Set<App>>
}

interface AppCommandPersistencePort {
    fun upsert(item: App): ValidatedNel<Error, App>
    fun delete(id: UUID): ValidatedNel<Error, Unit>
}
