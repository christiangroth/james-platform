package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

/**
 * Labels for the definition-grouped rows on the merged `imports.html` page (issue #676) and the reuse hint shown on
 * the import filter/mapping wizard steps, split out of [UserMessages] into their own bundle for the same reason as
 * [UserImportFilterMessages] - [UserMessages] is close to the bytecode-verification wall Quarkus Qute's generated
 * message bundle resolver hits at around 300 `@Message` methods on a single interface.
 */
@MessageBundle("userImportDefinition")
interface UserImportDefinitionMessages {

  // shown on the import filter/mapping wizard steps, clarifying that this configuration is saved on the reusable
  // definition (see docs/adr/0021-import-definition-job-split.md) and can be re-run later from the imports page
  @Message
  fun userImportDefinitionReuseHint(): String

  @Message
  fun userImportDefinitionColSource(): String

  @Message
  fun userImportDefinitionColSchedule(): String

  @Message
  fun userImportDefinitionColNextRun(): String

  @Message
  fun userImportDefinitionColLastRun(): String

  @Message
  fun userImportDefinitionColActions(): String

  @Message
  fun userImportDefinitionScheduleManualLabel(): String

  @Message
  fun userImportDefinitionNotConfiguredHint(): String

  @Message
  fun userImportDefinitionRunButton(): String

  @Message
  fun userImportDefinitionScheduleButton(): String

  @Message
  fun userImportDefinitionScheduleModalTitle(): String

  @Message
  fun userImportDefinitionScheduleLabel(): String

  @Message
  fun userImportDefinitionScheduleHint(): String

  @Message
  fun userImportDefinitionScheduleClearHint(): String

  @Message
  fun userImportDefinitionNotifyOnSlackLabel(): String

  @Message
  fun userDeleteImportDefinitionModalTitle(): String

  @Message
  fun userDeleteImportDefinitionConfirm(): String

  @Message
  fun userImportDefinitionRunQueuedMessage(): String

  @Message
  fun userImportDefinitionScheduleSavedMessage(): String

  @Message
  fun userImportDefinitionDeletedMessage(): String

  @Message
  fun userImportDefinitionNotFoundError(): String

  @Message
  fun userImportInvalidCronScheduleError(): String

  @Message
  fun userImportDefinitionNotConfiguredError(): String

  @Message
  fun userImportSchemaDriftDetectedError(): String

  // ImportStatus.ACCEPTED's label - kept here rather than on UserMessages, which is already close to the
  // bytecode-verification wall documented on this interface's class KDoc.
  @Message
  fun userImportStatusAccepted(): String

  @Message
  fun userImportDefinitionHistoryButton(): String

  @Message
  fun userImportDefinitionHistoryModalTitle(): String

  @Message
  fun userImportDefinitionHistoryColDate(): String

  @Message
  fun userImportDefinitionHistoryColStatus(): String

  @Message
  fun userImportDefinitionHistoryColAdded(): String

  @Message
  fun userImportDefinitionHistoryColReplaced(): String

  @Message
  fun userImportDefinitionHistoryColDiscarded(): String

  @Message
  fun userImportDefinitionNoHistoryMessage(): String

  @Message
  fun userImportDefinitionJobsColStatus(): String

  @Message
  fun userImportDefinitionJobsColLastChanged(): String

  // shown for a definition with neither an in-progress job nor a schedule, inviting the user to start a new one
  // (issue #679)
  @Message
  fun userImportDefinitionNoJobsHint(): String

  @Message
  fun userImportDefinitionStartJobButton(): String

  @Message
  fun userImportDefinitionJobStartedMessage(): String
}
