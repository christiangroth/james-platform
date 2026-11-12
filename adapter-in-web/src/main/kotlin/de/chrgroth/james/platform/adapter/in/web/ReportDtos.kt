package de.chrgroth.james.platform.adapter.`in`.web

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import de.chrgroth.james.platform.adapter.`in`.web.i18n.ReportAdvancedMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.ReportCheckMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.ReportExpressionMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.ReportExtensionMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.ReportMessages
import de.chrgroth.james.platform.adapter.`in`.web.i18n.ReportTransformMessages
import de.chrgroth.james.platform.domain.model.app.ChartPanelSpec
import de.chrgroth.james.platform.domain.model.app.ErrorPanelSpec
import de.chrgroth.james.platform.domain.model.app.HeatmapPanelSpec
import de.chrgroth.james.platform.domain.model.app.PanelSpec
import de.chrgroth.james.platform.domain.model.app.PiePanelSpec
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionError
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.ReportView
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import de.chrgroth.james.platform.domain.model.app.TextPanelSpec
import de.chrgroth.james.platform.domain.model.app.WarningsPanelSpec
import io.quarkus.qute.RawString
import jakarta.enterprise.context.ApplicationScoped

// Renderer-neutral panel spec of an executed report (see arc42 "Reports"), as served to the browser renderer (report-renderer.js)
// and by `GET /api/v1/user/apps/{installedAppId}/reports/{reportId}`. One panel DTO for all panel types: fields not used by a type are
// omitted, so new panel types only add fields.

data class ApiReportViewDto(
  val reportId: String,
  val name: String,
  val tabs: List<ApiReportTabDto>,
  val variables: List<ApiReportVariableDto> = emptyList(),
  val notices: List<ApiReportNoticeDto> = emptyList(),
  val totalNotices: Int = 0,
)

/** A report variable's control: [type] `timeRange`/`select`, its options (an empty value meaning "all") and the [selected] value. */
data class ApiReportVariableDto(val name: String, val label: String, val type: String, val options: List<ApiReportVariableOptionDto>, val selected: String)

data class ApiReportVariableOptionDto(val value: String, val label: String)

/** [warnings] counts the findings of the tab's warnings panels, shown on the tab. */
data class ApiReportTabDto(val name: String, val rows: List<ApiReportRowDto>, val warnings: Int = 0)

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
  // Explicit names: Jackson would derive "xaxis"/"yaxes" from the getters getXAxis()/getYAxes().
  @get:JsonProperty("xAxis") val xAxis: ApiChartXAxisDto? = null,
  @get:JsonProperty("yAxes") val yAxes: List<ApiChartYAxisDto>? = null,
  val series: List<ApiChartSeriesDto>? = null,
  val stack: Boolean? = null,
  val zoom: Boolean? = null,
  val horizontal: Boolean? = null,
  val value: Double? = null,
  val text: String? = null,
  val color: String? = null,
  val comparison: ApiStatComparisonDto? = null,
  val goal: ApiStatGoalDto? = null,
  val warnings: List<ApiReportWarningDto>? = null,
  val totalWarnings: Int? = null,
  val slices: List<ApiPieSliceDto>? = null,
  val donut: Boolean? = null,
  val unit: String? = null,
  val heatmap: ApiHeatmapDto? = null,
  /** Set instead of the type's data when the panel's query failed at runtime (a `kotlin` step): the German reason. */
  val error: String? = null,
)

/** One pie slice: [value] in the panel's display unit, [text] formatted, [percentText] its share. */
data class ApiPieSliceDto(val label: String, val value: Double, val text: String, val percentText: String)

/**
 * A heatmap: [mode] `calendar` (cells per ISO day between [from] and [to]) or `matrix` (cells per [xCategories]/[yCategories] pair); [min]/[max]
 * span the values, [thresholds] (ascending) color them in steps, else a continuous scale is used.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiHeatmapDto(
  val mode: String,
  val cells: List<ApiHeatmapCellDto>,
  val min: Double?,
  val max: Double?,
  val thresholds: List<ApiHeatmapThresholdDto>,
  val from: String? = null,
  val to: String? = null,
  val xCategories: List<String>? = null,
  val yCategories: List<String>? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiHeatmapCellDto(val x: String, val y: String?, val value: Double, val text: String)

data class ApiHeatmapThresholdDto(val value: Double, val color: String)

/** One notice of the report (header indicator and modal): [level] `info`/`warning`/`error`, [check] the check it stems from (null: a failed panel), [label] what it is about. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiReportNoticeDto(val level: String, val check: String?, val label: String?, val message: String)

/** One finding of a warnings panel: [severity] `warning`/`error`, [label] the period or row it is about. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiReportWarningDto(val check: String, val severity: String, val label: String?, val message: String)

/** [type] `category` (with [categories] as labels) or `time` (points carry ISO dates). */
data class ApiChartXAxisDto(val type: String, val categories: List<String>)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ApiChartYAxisDto(val position: String, val unit: String?)

data class ApiChartSeriesDto(val name: String, val style: String, @get:JsonProperty("yAxis") val yAxis: Int, val points: List<ApiChartPointDto>)

@JsonInclude(JsonInclude.Include.ALWAYS)
data class ApiChartPointDto(val x: String, val y: Double?, val text: String)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiStatComparisonDto(
  val previousValue: Double?,
  val previousText: String,
  val deltaText: String,
  val percentText: String?,
  val trend: String,
  val periodText: String,
)

/** A stat's target: [value] raw, [text] formatted, [percent] the stat value in percent of it (absent without value or for a target of 0). */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiStatGoalDto(val value: Double?, val text: String, val percent: Double?, val percentText: String?)

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
    tabs = view.tabs.map { tab -> ApiReportTabDto(tab.name, tab.rows.map { row -> ApiReportRowDto(row.panels.map { panel(it) }) }, tab.warnings) },
    variables = view.variables.map { variable ->
      ApiReportVariableDto(variable.name, variable.label, variable.type, variable.options.map { ApiReportVariableOptionDto(it.value, it.label) }, variable.selected)
    },
    notices = view.notices.map { ApiReportNoticeDto(it.level, it.check, it.label, it.message) },
    totalNotices = view.totalNotices,
  )

  /** Query (or form) parameter prefix carrying a variable selection, e.g. `var.zeitraum=last_month`. */
  const val VARIABLE_PARAMETER_PREFIX = "var."

  /** The variable selections among request parameters: `var.<name>` -> value (the first value of each). */
  fun variableSelections(parameters: Map<String, List<String>>): Map<String, String> = parameters
    .filterKeys { it.startsWith(VARIABLE_PARAMETER_PREFIX) && it.length > VARIABLE_PARAMETER_PREFIX.length }
    .mapNotNull { (key, values) -> values.firstOrNull()?.let { key.removePrefix(VARIABLE_PARAMETER_PREFIX) to it } }
    .toMap()

  private val selectionsMapper = ObjectMapper()

  /** Variable selections sent as a JSON object of strings (the editor preview); anything else is ignored. */
  fun variableSelections(json: String?): Map<String, String> {
    if (json.isNullOrBlank()) return emptyMap()
    val node = runCatching { selectionsMapper.readTree(json) }.getOrNull()?.takeIf { it.isObject } ?: return emptyMap()
    return node.properties().filter { it.value.isTextual }.associate { it.key to it.value.asText() }
  }

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
    is ChartPanelSpec -> ApiReportPanelDto(
      type = panel.type.key,
      title = panel.title,
      xAxis = ApiChartXAxisDto(panel.xAxis.type.name.lowercase(), panel.xAxis.categories),
      yAxes = panel.yAxes.map { ApiChartYAxisDto(it.position, it.unit) },
      series = panel.series.map { series -> ApiChartSeriesDto(series.name, series.style, series.yAxis, series.points.map { ApiChartPointDto(it.x, it.y, it.text) }) },
      stack = panel.stack,
      zoom = panel.zoom,
      horizontal = panel.horizontal,
    )
    is StatPanelSpec -> ApiReportPanelDto(
      type = panel.type.key,
      title = panel.title,
      value = panel.value,
      text = panel.text,
      color = panel.color,
      comparison = panel.comparison?.let { ApiStatComparisonDto(it.previousValue, it.previousText, it.deltaText, it.percentText, it.trend, it.periodText) },
      goal = panel.goal?.let { ApiStatGoalDto(it.value, it.text, it.percent, it.percentText) },
    )
    is WarningsPanelSpec -> ApiReportPanelDto(
      type = panel.type.key,
      title = panel.title,
      warnings = panel.warnings.map { ApiReportWarningDto(it.check, it.severity, it.label, it.message) },
      totalWarnings = panel.total,
      truncated = panel.truncated,
    )
    is PiePanelSpec -> ApiReportPanelDto(
      type = panel.type.key,
      title = panel.title,
      slices = panel.slices.map { ApiPieSliceDto(it.label, it.value, it.text, it.percentText) },
      donut = panel.donut,
      unit = panel.unit,
    )
    is HeatmapPanelSpec -> ApiReportPanelDto(
      type = panel.type.key,
      title = panel.title,
      unit = panel.unit,
      heatmap = ApiHeatmapDto(
        mode = if (panel.calendar) "calendar" else "matrix",
        cells = panel.cells.map { ApiHeatmapCellDto(it.x, it.y, it.value, it.text) },
        min = panel.min,
        max = panel.max,
        thresholds = panel.thresholds.map { ApiHeatmapThresholdDto(it.value, it.color) },
        from = panel.from,
        to = panel.to,
        xCategories = panel.xCategories.takeUnless { panel.calendar },
        yCategories = panel.yCategories.takeUnless { panel.calendar },
      ),
    )
    is ErrorPanelSpec -> ApiReportPanelDto(type = panel.type.key, title = panel.title, error = panel.message)
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
class ReportTexts(
  private val reportMsg: ReportMessages,
  private val transformMsg: ReportTransformMessages,
  private val checkMsg: ReportCheckMessages,
  private val advancedMsg: ReportAdvancedMessages,
  private val extensionMsg: ReportExtensionMessages,
  private val expressionMsg: ReportExpressionMessages,
) {

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
      ReportDefinitionErrorCode.TIME_FIELD_MISSING -> reportMsg.reportErrorTimeFieldMissing(a(0))
      ReportDefinitionErrorCode.QUERY_NOT_FOUND -> reportMsg.reportErrorQueryNotFound(a(0))
      ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY -> reportMsg.reportErrorColumnNotInQuery(a(0), a(1))
      ReportDefinitionErrorCode.PANEL_TYPE_MISSING -> reportMsg.reportErrorPanelTypeMissing(a(0))
      ReportDefinitionErrorCode.PANEL_TYPE_AMBIGUOUS -> reportMsg.reportErrorPanelTypeAmbiguous(a(0))
      ReportDefinitionErrorCode.FILTER_VALUE_MISSING -> reportMsg.reportErrorFilterValueMissing(a(0))
      ReportDefinitionErrorCode.INVALID_REGEX -> reportMsg.reportErrorInvalidRegex(a(0))
      ReportDefinitionErrorCode.INVALID_DATE -> reportMsg.reportErrorInvalidDate(a(0))
      ReportDefinitionErrorCode.INVALID_RANGE -> reportMsg.reportErrorInvalidRange()
      ReportDefinitionErrorCode.NUMBER_OUT_OF_RANGE -> reportMsg.reportErrorNumberOutOfRange(a(0), a(1), a(2))
      ReportDefinitionErrorCode.KEY_CONFLICT -> reportMsg.reportErrorKeyConflict(a(0), a(1))
      ReportDefinitionErrorCode.PERIOD_START_INVALID -> reportMsg.reportErrorPeriodStartInvalid(a(0))
      ReportDefinitionErrorCode.PERIOD_START_NOT_APPLICABLE -> reportMsg.reportErrorPeriodStartNotApplicable()
      ReportDefinitionErrorCode.FIELD_NOT_NUMERIC -> reportMsg.reportErrorFieldNotNumeric(a(0))
      ReportDefinitionErrorCode.DUPLICATE_RESULT_FIELD -> reportMsg.reportErrorDuplicateResultField(a(0))
      ReportDefinitionErrorCode.CHART_X_MISSING -> reportMsg.reportErrorChartXMissing(a(0))
      ReportDefinitionErrorCode.SERIES_X_MISMATCH -> reportMsg.reportErrorSeriesXMismatch(a(0))
      ReportDefinitionErrorCode.AXIS_UNIT_MISMATCH -> reportMsg.reportErrorAxisUnitMismatch(a(0))
      ReportDefinitionErrorCode.COMPARE_NEEDS_RANGE -> reportMsg.reportErrorCompareNeedsRange(a(0))
      ReportDefinitionErrorCode.INVALID_NUMBER -> reportMsg.reportErrorInvalidNumber(a(0))
      ReportDefinitionErrorCode.INVALID_COLOR -> reportMsg.reportErrorInvalidColor(a(0), a(1))
      ReportDefinitionErrorCode.CONFIG_NOT_DEFINED -> reportMsg.reportErrorConfigNotDefined(a(0))
      ReportDefinitionErrorCode.CONFIG_FIELD_NOT_FOUND -> reportMsg.reportErrorConfigFieldNotFound(a(0), a(1))
      ReportDefinitionErrorCode.CONFIG_UNIT_MISMATCH -> reportMsg.reportErrorConfigUnitMismatch(a(0))
      ReportDefinitionErrorCode.VARIABLE_NOT_FOUND -> transformMsg.reportErrorVariableNotFound(a(0))
      ReportDefinitionErrorCode.VARIABLE_TYPE_MISMATCH -> transformMsg.reportErrorVariableTypeMismatch(a(0), a(1))
      ReportDefinitionErrorCode.VARIABLE_OPTIONS_QUERY_INVALID -> transformMsg.reportErrorVariableOptionsQueryInvalid(a(0), a(1))
      ReportDefinitionErrorCode.VARIABLE_NAME_INVALID -> transformMsg.reportErrorVariableNameInvalid(a(0))
      ReportDefinitionErrorCode.TRANSFORM_STEP_MISSING -> transformMsg.reportErrorTransformStepMissing(a(0))
      ReportDefinitionErrorCode.TRANSFORM_STEP_AMBIGUOUS -> transformMsg.reportErrorTransformStepAmbiguous(a(0))
      ReportDefinitionErrorCode.EXPRESSION_INVALID -> transformMsg.reportErrorExpressionInvalid(a(0), a(1), a(2))
      ReportDefinitionErrorCode.EXPRESSION_UNKNOWN_FUNCTION -> transformMsg.reportErrorExpressionUnknownFunction(a(0), a(1))
      ReportDefinitionErrorCode.EXPRESSION_OPERAND_TYPE -> expressionMsg.reportErrorExpressionOperandType(a(0), typeName(a(1)))
      ReportDefinitionErrorCode.EXPRESSION_ARGUMENT_TYPE -> expressionMsg.reportErrorExpressionArgumentType(a(0), a(1), typeName(a(2)))
      ReportDefinitionErrorCode.EXPRESSION_INCOMPARABLE -> expressionMsg.reportErrorExpressionIncomparable(a(0), typeName(a(1)), typeName(a(2)))
      ReportDefinitionErrorCode.EXPRESSION_MIXED_TYPES ->
        expressionMsg.reportErrorExpressionMixedTypes(a(0), a(1).split(",").joinToString(", ") { typeName(it.trim()) })
      ReportDefinitionErrorCode.EXPRESSION_RESULT_TYPE -> expressionMsg.reportErrorExpressionResultType(typeName(a(0)), typeName(a(1)))
      ReportDefinitionErrorCode.EXPRESSION_LIST_FIELD_REQUIRED -> expressionMsg.reportErrorExpressionListFieldRequired(a(0))
      ReportDefinitionErrorCode.EXPRESSION_UNKNOWN_ITEM_FIELD -> expressionMsg.reportErrorExpressionUnknownItemField(a(0), a(1))
      ReportDefinitionErrorCode.TRANSFORM_NEEDS_BUCKET -> transformMsg.reportErrorTransformNeedsBucket(a(0), a(1))
      ReportDefinitionErrorCode.FORECAST_NEEDS_PERIOD -> transformMsg.reportErrorForecastNeedsPeriod(a(0))
      ReportDefinitionErrorCode.RESAMPLE_NEEDS_TIME -> transformMsg.reportErrorResampleNeedsTime(a(0))
      ReportDefinitionErrorCode.TRANSFORM_NEEDS_TIME -> advancedMsg.reportErrorTransformNeedsTime(a(0), a(1))
      ReportDefinitionErrorCode.TRANSFORM_QUERY_CYCLE -> advancedMsg.reportErrorTransformQueryCycle(a(0))
      ReportDefinitionErrorCode.GOAL_TARGET_INVALID -> advancedMsg.reportErrorGoalTargetInvalid(a(0))
      ReportDefinitionErrorCode.CHECK_TYPE_MISSING -> checkMsg.reportErrorCheckTypeMissing(a(0))
      ReportDefinitionErrorCode.CHECK_TYPE_AMBIGUOUS -> checkMsg.reportErrorCheckTypeAmbiguous(a(0))
      ReportDefinitionErrorCode.CHECK_NOT_FOUND -> checkMsg.reportErrorCheckNotFound(a(0))
      ReportDefinitionErrorCode.CALENDAR_INVALID -> checkMsg.reportErrorCalendarInvalid(a(0))
      ReportDefinitionErrorCode.MESSAGE_PLACEHOLDER_UNKNOWN -> checkMsg.reportErrorMessagePlaceholderUnknown("{" + a(0) + "}", a(1))
      ReportDefinitionErrorCode.HEATMAP_NEEDS_DAY -> extensionMsg.reportErrorHeatmapNeedsDay(a(0))
      ReportDefinitionErrorCode.PIE_CATEGORY_MISSING -> extensionMsg.reportErrorPieCategoryMissing(a(0))
      ReportDefinitionErrorCode.KOTLIN_STEP_REMOVED -> extensionMsg.reportErrorKotlinStepRemoved()
    }
  }

  /** The German name of an expression type (NUMBER, TEXT, ...) or field type (LONG, STRING, ...) as validation errors mention it. */
  fun typeName(type: String): String = when (type) {
    "NUMBER", "DOUBLE" -> expressionMsg.expressionTypeNumber()
    "LONG" -> expressionMsg.expressionTypeInteger()
    "TEXT", "STRING" -> expressionMsg.expressionTypeText()
    "BOOLEAN" -> expressionMsg.expressionTypeBoolean()
    "DATE" -> expressionMsg.expressionTypeDate()
    "DATETIME" -> expressionMsg.expressionTypeDateTime()
    "TIME" -> expressionMsg.expressionTypeTime()
    else -> type
  }

  /** JSON for a `<script type="application/json">` block; `<` is escaped so data can never close the script element. */
  fun json(value: Any?): RawString = RawString(objectMapper.writeValueAsString(value).replace("<", "\\u003c"))
}
