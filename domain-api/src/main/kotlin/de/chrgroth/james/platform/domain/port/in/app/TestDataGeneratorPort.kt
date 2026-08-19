package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent

/** Result of [TestDataGeneratorPort.generateTestData] - see docs/dev-tests.md, "Execution model - synchronous vs. outbox". */
sealed interface TestDataGenerationOutcome {
  /** A small, bounded run that was generated synchronously; [objects] are the newly created test objects. */
  data class Completed(val objects: List<AppData>) : TestDataGenerationOutcome

  /** A large run that was enqueued via the outbox instead of blocking the request; no objects exist yet. */
  data object Enqueued : TestDataGenerationOutcome
}

/**
 * Automatic test data generator (see docs/dev-tests.md, "Phase 1 – Automatic Test Data Generator"). Generates and persists
 * valid test objects for an Entity into a Developer's test installation, deriving values from each Property's [de.chrgroth.james.platform.domain.model.app.PropertyType]
 * and [de.chrgroth.james.platform.domain.model.app.PropertyConstraint]s. `REF` properties without any existing target objects
 * yet cause those target entities to be generated first (topologically). Computed Properties and Smart Defaults are never
 * generated - they stay derived, as for real data. A small, bounded run stays synchronous; a run above
 * [de.chrgroth.james.platform.domain.app.TestDataGeneratorService]'s async threshold is enqueued via the outbox instead
 * (see the concept doc's "Execution model" note and [TestDataGenerationOutcome]).
 */
interface TestDataGeneratorPort {

  /**
   * Generates [count] valid test objects for the entity [entityId] into the test installation [installedAppId], which must
   * belong to [appId] and be flagged [de.chrgroth.james.platform.domain.model.app.InstalledApp.isTest], and which [developerId]
   * must own via [appId]. [seed], if given, makes the generated data reproducible across otherwise-identical calls. Returns
   * [TestDataGenerationOutcome.Enqueued] instead of generating inline once [count] exceeds the async threshold.
   */
  fun generateTestData(
    appId: String,
    installedAppId: String,
    entityId: String,
    count: Int,
    developerId: String,
    seed: Long? = null,
  ): Either<DomainError, TestDataGenerationOutcome>

  /** Whether a background generation run enqueued by [generateTestData] is currently in flight for [entityId] in [installedAppId], owned by [developerId] via [appId]. */
  fun isGenerating(appId: String, installedAppId: String, entityId: String, developerId: String): Boolean

  /**
   * Performs the actual background generation for a [DomainOutboxEvent.GenerateTestData] event, reusing the same generator
   * and safety net as the synchronous path. Called by the outbox dispatcher, not directly by inbound adapters. Clears
   * [de.chrgroth.james.platform.domain.model.app.InstalledApp.generatingEntityId] when done, regardless of outcome, so the
   * Developer-facing "in progress" signal never gets stuck. A no-longer-existing app or test installation (deleted while
   * the run was queued) is treated as a no-op success.
   */
  fun handle(event: DomainOutboxEvent.GenerateTestData): Either<DomainError, Unit>
}
