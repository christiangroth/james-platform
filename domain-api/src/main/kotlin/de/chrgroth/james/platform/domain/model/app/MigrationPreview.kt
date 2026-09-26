package de.chrgroth.james.platform.domain.model.app

import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation

/**
 * A single finding produced while previewing what a draft Entity's migration steps and script (see [MigrationStep],
 * docs/adr/0023-migration-steps.md) would do to one existing [AppData] object, without running the real
 * dry-run/publish flow. Mirrors [de.chrgroth.james.platform.domain.model.imports.DryRunIssue], but for the
 * migration pipeline rather than the import mapping pipeline.
 */
sealed interface MigrationPreviewIssue {
  val propertyId: PropertyId?

  /** A [MigrationStep] could not be applied to [propertyId] (e.g. its source value could not be converted) - see [reason]. */
  data class StepFailed(override val propertyId: PropertyId?, val reason: String) : MigrationPreviewIssue

  /** The Entity's migration script threw, timed out, or did not return a `Map<String, String?>` - see [reason]. */
  data class ScriptFailed(val reason: String) : MigrationPreviewIssue {
    override val propertyId: PropertyId? = null
  }

  /** The migrated object still violates [violation] on [propertyId] after steps and script ran. */
  data class ConstraintViolated(override val propertyId: PropertyId, val violation: PropertyConstraintViolation) : MigrationPreviewIssue
}

/**
 * One existing [AppData] object's values before and after applying a draft Entity's migration steps and script, with
 * the issues found while re-validating the result. [before]/[after] are keyed by [PropertyId.value]; a property
 * dropped between the previous and draft shape (e.g. a `CopyValue` step's source) may only have an entry in [before].
 */
data class MigrationPreviewObject(
  val index: Int,
  val appDataId: AppDataId,
  val before: Map<String, String?>,
  val after: Map<String, String?>,
  val issues: List<MigrationPreviewIssue>,
) {
  val isValid: Boolean get() = issues.isEmpty()
}

/**
 * [total] is the number of existing [AppData] objects of the entity sampled for the preview (see
 * docs/adr/0015-import-object-preview-endpoint.md's `total`/index-style building block, reused here), so the UI can
 * render a "x of y" position without a separate count call. [previewObject] is null when [index] falls outside
 * `0 until total`.
 */
data class MigrationPreviewSample(
  val total: Int,
  val previewObject: MigrationPreviewObject?,
)
