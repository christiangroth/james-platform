package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.james.platform.adapter.`in`.web.i18n.ReportMessages
import de.chrgroth.james.platform.domain.model.app.PanelSpec
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionError
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.ReportView
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import de.chrgroth.james.platform.domain.model.app.TextPanelSpec
import io.quarkus.qute.RawString
import jakarta.enterprise.context.ApplicationScoped

// Renderer-neutral panel spec of an executed report (see docs/plans/reports.md), as served to the browser renderer (report-renderer.js)
// and by `GET /api/v1/user/apps/{installedAppId}/reports/{reportId}`. One panel DTO for all panel types: fields not used by a type are
// omitted, so new panel types only add fields.

data class ApiReportViewDto(
  val reportId: String,
  val name: String,
  val tabs: List<ApiReportTabDto>,
)

data class ApiReportTabDto(val name: String, val rows: List<ApiReportRowDto>)

data class ApiReportRowDto(val panels: List<ApiReportPanelDto>)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiReportPanelDto(
  val type: String,
  val title: String,
  val columns: List<ApiReportColumnDto>? = null,
  val rows: List<List<ApiReportCellDto>>? = null,
  val totalRows: Int? = null,
  val truncated: Boolean? = null,
  val markdown: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiReportColumnDto(val key: String, val label: String, val type: String, val unit: ApiReportUnitDto? = null)

data class ApiReportUnitDto(val family: String, val granularity: String, val symbol: String)

data class ApiReportCellDto(val value: String?, val text: String)

/** One validation error of a report definition, with its German [message] for the editor. */
data class ReportDefinitionErrorDto(val code: String, val path: String, val line: Int?, val message: String)

/** Answer of the editor's save and preview requests: [errors] for an invalid definition, [view] for a successful preview. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ReportEditorResult(
  val ok: Boolean,
  val message: String? = null,
  val redirectUrl: String? = null,
  val errors: List<ReportDefinitionErrorDto>? = null,
  val view: ApiReportViewDto? = null,
)

object ApiReportDtoMapper {

  fun view(view: ReportView) = ApiReportViewDto(
    reportId = view.reportId,
    name = view.name,
    tabs = view.tabs.map { tab -> ApiReportTabDto(tab.name, tab.rows.map { row -> ApiReportRowDto(row.panels.map { panel(it) }) }) },
  )

  private fun panel(panel: PanelSpec): ApiReportPanelDto = when (panel) {
    is TablePanelSpec -> ApiReportPanelDto(
      type = panel.type.key,
      title = panel.title,
      columns = panel.columns.map { column ->
        ApiReportColumnDto(column.key, column.label, column.type, column.unit?.let { ApiReportUnitDto(it.family, it.granularity, it.symbol) })
      },
      rows = panel.rows.map { row -> row.map { ApiReportCellDto(it.value, it.text) } },
      totalRows = panel.totalRows,
      truncated = panel.truncated,
    )
    is TextPanelSpec -> ApiReportPanelDto(type = panel.type.key, title = panel.title, markdown = panel.markdown)
  }

  /** English, machine-oriented detail line for the token API, e.g. `line 3, queries.q.entity: ENTITY_NOT_FOUND [Lauf]`. */
  fun errorDetail(error: ReportDefinitionError): String {
    val location = listOfNotNull(error.line?.let { "line $it" }, error.path.takeIf { it.isNotEmpty() }).joinToString(", ")
    val args = if (error.args.isEmpty()) "" else " ${error.args}"
    return if (location.isEmpty()) "${error.code.name}$args" else "$location: ${error.code.name}$args"
  }
}

/** German texts for report definition errors and JSON embedding of report views into pages. */
@ApplicationScoped
class ReportTexts(private val reportMsg: ReportMessages) {

  private val objectMapper = ObjectMapper()

  fun errors(errors: List<ReportDefinitionError>): List<ReportDefinitionErrorDto> = errors.map { error ->
    val text = message(error)
    val location = when {
      error.line != null -> reportMsg.reportErrorLocation(error.line.toString(), error.path.ifEmpty { "-" })
      error.path.isNotEmpty() -> error.path
      else -> null
    }
    ReportDefinitionErrorDto(error.code.name, error.path, error.line, if (location == null) text else "$location: $text")
  }

  @Suppress("CyclomaticComplexMethod")
  private fun message(error: ReportDefinitionError): String {
    val a = { index: Int -> error.args.getOrElse(index) { "" } }
    return when (error.code) {
      ReportDefinitionErrorCode.YAML_SYNTAX -> reportMsg.reportErrorYamlSyntax(a(0))
      ReportDefinitionErrorCode.DUPLICATE_KEY -> reportMsg.reportErrorDuplicateKey(a(0))
      ReportDefinitionErrorCode.WRONG_TYPE -> reportMsg.reportErrorWrongType(a(0))
      ReportDefinitionErrorCode.UNKNOWN_KEY -> reportMsg.reportErrorUnknownKey(a(0), a(1))
      ReportDefinitionErrorCode.NOT_SUPPORTED_YET -> reportMsg.reportErrorNotSupportedYet(a(0))
      ReportDefinitionErrorCode.REQUIRED_KEY_MISSING -> reportMsg.reportErrorRequiredKeyMissing(a(0))
      ReportDefinitionErrorCode.INVALID_VALUE -> reportMsg.reportErrorInvalidValue(a(0), a(1))
      ReportDefinitionErrorCode.ENTITY_NOT_FOUND -> reportMsg.reportErrorEntityNotFound(a(0))
      ReportDefinitionErrorCode.FIELD_NOT_FOUND -> reportMsg.reportErrorFieldNotFound(a(0), a(1))
      ReportDefinitionErrorCode.REF_HOP_INVALID -> reportMsg.reportErrorRefHopInvalid(a(0))
      ReportDefinitionErrorCode.TIME_FIELD_INVALID -> reportMsg.reportErrorTimeFieldInvalid(a(0))
      ReportDefinitionErrorCode.TIME_FIELD_MISSING -> reportMsg.reportErrorTimeFieldMissing()
      ReportDefinitionErrorCode.QUERY_NOT_FOUND -> reportMsg.reportErrorQueryNotFound(a(0))
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY -> reportMsg.reportErrorColumnNotInQuery(a(0), a(1))
      ReportDefinitionErrorCode.PANEL_TYPE_MISSING -> reportMsg.reportErrorPanelTypeMissing(a(0))
      ReportDefinitionErrorCode.PANEL_TYPE_AMBIGUOUS -> reportMsg.reportErrorPanelTypeAmbiguous(a(0))
      ReportDefinitionErrorCode.FILTER_VALUE_MISSING -> reportMsg.reportErrorFilterValueMissing(a(0))
      ReportDefinitionErrorCode.INVALID_REGEX -> reportMsg.reportErrorInvalidRegex(a(0))
      ReportDefinitionErrorCode.INVALID_DATE -> reportMsg.reportErrorInvalidDate(a(0))
      ReportDefinitionErrorCode.INVALID_RANGE -> reportMsg.reportErrorInvalidRange()
      ReportDefinitionErrorCode.NUMBER_OUT_OF_RANGE -> reportMsg.reportErrorNumberOutOfRange(a(0), a(1), a(2))
    }
  }

  /** JSON for a `<script type="application/json">` block; `<` is escaped so data can never close the script element. */
  fun json(value: Any?): RawString = RawString(objectMapper.writeValueAsString(value).replace("<", "\\u003c"))
}
