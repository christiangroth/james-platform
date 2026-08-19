package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppData

/**
 * Automatic test data generator (see docs/dev-tests.md, "Phase 1 – Automatic Test Data Generator"). Generates and persists
 * valid test objects for an Entity into a Developer's test installation, deriving values from each Property's [de.chrgroth.james.platform.domain.model.app.PropertyType]
 * and [de.chrgroth.james.platform.domain.model.app.PropertyConstraint]s. `REF` properties without any existing target objects
 * yet cause those target entities to be generated first (topologically). Computed Properties and Smart Defaults are never
 * generated - they stay derived, as for real data. Bounded/synchronous by design; a Developer-triggered async/outbox path
 * for very large runs (see the concept doc's "Execution model" note) is not implemented here.
 */
interface TestDataGeneratorPort {

  /**
   * Generates [count] valid test objects for the entity [entityId] into the test installation [installedAppId], which must
   * belong to [appId] and be flagged [de.chrgroth.james.platform.domain.model.app.InstalledApp.isTest], and which [developerId]
   * must own via [appId]. [seed], if given, makes the generated data reproducible across otherwise-identical calls.
   */
  fun generateTestData(
    appId: String,
    installedAppId: String,
    entityId: String,
    count: Int,
    developerId: String,
    seed: Long? = null,
  ): Either<DomainError, List<AppData>>
}
