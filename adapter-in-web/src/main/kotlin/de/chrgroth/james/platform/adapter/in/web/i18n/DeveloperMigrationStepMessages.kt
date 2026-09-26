package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Migration step editor labels for `version-editor.html`, split out of [DeveloperMessages] into their own bundle for the same
 * reason as [DeveloperAggregationMessages] - Quarkus Qute's generated message bundle resolver fails bytecode verification once
 * a single interface grows past roughly 300 `@Message` methods, and [DeveloperMessages] is right at that wall.
 */
@MessageBundle("developerMigrationStep")
interface DeveloperMigrationStepMessages {

  @Message
  fun developerMigrationHeading(): String

  @Message
  fun developerMigrationScriptSubheading(): String

  @Message
  fun developerNoMigrationStepsMessage(): String

  @Message
  fun developerAddMigrationStepModalTitle(): String

  @Message
  fun developerEditMigrationStepModalTitle(): String

  @Message
  fun developerMigrationStepTypeLabel(): String

  @Message
  fun developerMigrationStepTypeConvertType(): String

  @Message
  fun developerMigrationStepTypeCopyValue(): String

  @Message
  fun developerMigrationStepTypeConvertUnit(): String

  @Message
  fun developerMigrationStepTypeFillEmptyValue(): String

  @Message
  fun developerMigrationStepTypeAdjustToConstraints(): String

  @Message
  fun developerMigrationStepPropertyLabel(): String

  @Message
  fun developerMigrationStepSourcePropertyLabel(): String

  @Message
  fun developerMigrationStepTargetPropertyLabel(): String

  @Message
  fun developerMigrationStepPropertyPlaceholder(): String

  @Message
  fun developerMigrationStepSourceGranularityLabel(): String

  @Message
  fun developerMigrationStepSourceGranularityPlaceholder(): String

  @Message
  fun developerMigrationStepValueLabel(): String

  @Message
  fun developerMigrationStepValueHelp(): String

  @Message
  fun developerMigrationStepConvertTypeDescription(propertyName: String): String

  @Message
  fun developerMigrationStepCopyValueDescription(sourceName: String, targetName: String): String

  @Message
  fun developerMigrationStepConvertUnitDescription(propertyName: String, sourceGranularity: String): String

  @Message
  fun developerMigrationStepFillEmptyValueDescriptionWithValue(propertyName: String, value: String): String

  @Message
  fun developerMigrationStepFillEmptyValueDescriptionWithDefault(propertyName: String): String

  @Message
  fun developerMigrationStepAdjustToConstraintsDescription(propertyName: String): String

  @Message
  fun developerMigrationStepInvalidBadge(): String

  @Message
  fun developerRemoveMigrationStepModalTitle(): String

  @Message
  fun developerRemoveMigrationStepConfirmMessage(): String

  @Message
  fun developerMigrationStepAddedMessage(): String

  @Message
  fun developerMigrationStepUpdatedMessage(): String

  @Message
  fun developerMigrationStepDeletedMessage(): String

  @Message
  fun developerMigrationStepsReorderedMessage(): String

  @Message
  fun developerMigrationStepNotFoundError(): String

  @Message
  fun developerMigrationStepSourcePropertyNotFoundError(): String

  @Message
  fun developerMigrationStepTargetPropertyNotFoundError(): String

  @Message
  fun developerMigrationStepTypeNotConvertibleError(): String

  @Message
  fun developerMigrationStepTargetAlreadyUsedError(): String

  @Message
  fun developerMigrationStepTypeInvalidError(): String

  @Message
  fun developerMigrationStepUnitRequiredError(): String

  @Message
  fun developerMigrationStepUnitGranularityInvalidError(): String

  @Message
  fun developerMigrationStepFillValueInvalidError(): String

  @Message
  fun developerInvalidMigrationStepError(names: String): String
}
