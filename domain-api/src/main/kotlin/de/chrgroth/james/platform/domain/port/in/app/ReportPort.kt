package de.chrgroth.james.platform.domain.port.`in`.app

import arrow.core.Either
import de.chrgroth.james.platform.domain.error.DomainError
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.ReportDefinition
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionError
import de.chrgroth.james.platform.domain.model.app.ReportView

/**
 * Parses, validates and executes declarative reports (see docs/plans/reports.md). Execution only ever reads the data of the one installation
 * it is asked for (sandbox boundary) and answers with a renderer-neutral [ReportView].
 */
interface ReportPort {

  /** Parses [definition] and validates it against [version]'s entities. Either the typed definition or every error found. */
  fun parseAndValidate(version: AppVersion, definition: String): Either<List<ReportDefinitionError>, ReportDefinition>

  /**
   * Executes the stored report [reportId] of the installation's version for its owner [userId] (the username). [variables] carries the selected
   * value per report variable name; missing or unknown selections fall back to the variable's default.
   */
  fun renderReport(userId: String, installedAppId: String, reportId: String, variables: Map<String, String> = emptyMap()): Either<DomainError, ReportView>

  /**
   * Executes an unsaved [definition] against the installation [installedAppId] owned by [userId] (a Developer's test installation of the draft),
   * for the editor's preview. Invalid definitions answer with an `InvalidReportDefinitionError`.
   */
  fun previewReport(
    userId: String,
    installedAppId: String,
    name: String,
    definition: String,
    variables: Map<String, String> = emptyMap(),
  ): Either<DomainError, ReportView>
}
