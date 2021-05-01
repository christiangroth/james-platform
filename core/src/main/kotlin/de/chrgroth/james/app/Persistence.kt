package de.chrgroth.james.app

import de.chrgroth.james.CrudRepository
import java.util.UUID

// TODO split query and command??
interface AppPersistencePort : CrudRepository<App, UUID>
