package de.chrgroth.james.platform.domain.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import de.chrgroth.james.platform.domain.expression.ExpressionValue
import de.chrgroth.james.platform.domain.imports.FilterEvaluator
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.ChartPanelSpec
import de.chrgroth.james.platform.domain.model.app.ChartPointSpec
import de.chrgroth.james.platform.domain.model.app.ChartSeriesSpec
import de.chrgroth.james.platform.domain.model.app.ChartXAxisSpec
import de.chrgroth.james.platform.domain.model.app.ChartXAxisType
import de.chrgroth.james.platform.domain.model.app.ChartYAxisSpec
import de.chrgroth.james.platform.domain.model.app.CurrencyGranularity
import de.chrgroth.james.platform.domain.model.app.DisplayTextResolver
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.ErrorPanelSpec
import de.chrgroth.james.platform.domain.model.app.Granularity
import de.chrgroth.james.platform.domain.model.app.HeatmapCellSpec
import de.chrgroth.james.platform.domain.model.app.HeatmapPanelSpec
import de.chrgroth.james.platform.domain.model.app.HeatmapThresholdSpec
import de.chrgroth.james.platform.domain.model.app.PanelSpec
import de.chrgroth.james.platform.domain.model.app.PiePanelSpec
import de.chrgroth.james.platform.domain.model.app.PieSliceSpec
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.PropertyValueFormat
import de.chrgroth.james.platform.domain.model.app.RelativePeriod
import de.chrgroth.james.platform.domain.model.app.ReportAggregate
import de.chrgroth.james.platform.domain.model.app.ReportAggregateTransform
import de.chrgroth.james.platform.domain.model.app.ReportAsOfTransform
import de.chrgroth.james.platform.domain.model.app.ReportAxis
import de.chrgroth.james.platform.domain.model.app.ReportCalcTransform
import de.chrgroth.james.platform.domain.model.app.ReportChartPanel
import de.chrgroth.james.platform.domain.model.app.ReportCheck
import de.chrgroth.james.platform.domain.model.app.ReportColumn
import de.chrgroth.james.platform.domain.model.app.ReportCumulativeTransform
import de.chrgroth.james.platform.domain.model.app.ReportDateRange
import de.chrgroth.james.platform.domain.model.app.ReportDefinition
import de.chrgroth.james.platform.domain.model.app.ReportDeltaTransform
import de.chrgroth.james.platform.domain.model.app.ReportForecastTransform
import de.chrgroth.james.platform.domain.model.app.ReportGoalTransform
import de.chrgroth.james.platform.domain.model.app.ReportHeatmapPanel
import de.chrgroth.james.platform.domain.model.app.ReportJoinTransform
import de.chrgroth.james.platform.domain.model.app.ReportKotlinTransform
import de.chrgroth.james.platform.domain.model.app.ReportMovingAverageTransform
import de.chrgroth.james.platform.domain.model.app.ReportPanel
import de.chrgroth.james.platform.domain.model.app.ReportPanelType
import de.chrgroth.james.platform.domain.model.app.ReportPiePanel
import de.chrgroth.james.platform.domain.model.app.ReportQuery
import de.chrgroth.james.platform.domain.model.app.ReportReduce
import de.chrgroth.james.platform.domain.model.app.ReportResampleTransform
import de.chrgroth.james.platform.domain.model.app.ReportSelectVariable
import de.chrgroth.james.platform.domain.model.app.ReportSeries
import de.chrgroth.james.platform.domain.model.app.ReportSort
import de.chrgroth.james.platform.domain.model.app.ReportStatPanel
import de.chrgroth.james.platform.domain.model.app.ReportTablePanel
import de.chrgroth.james.platform.domain.model.app.ReportTextPanel
import de.chrgroth.james.platform.domain.model.app.ReportTimeRange
import de.chrgroth.james.platform.domain.model.app.ReportTimeRangeVariable
import de.chrgroth.james.platform.domain.model.app.ReportTopTransform
import de.chrgroth.james.platform.domain.model.app.ReportTransform
import de.chrgroth.james.platform.domain.model.app.ReportUnitSpec
import de.chrgroth.james.platform.domain.model.app.ReportVariable
import de.chrgroth.james.platform.domain.model.app.ReportVariableOptionSpec
import de.chrgroth.james.platform.domain.model.app.ReportVariableSpec
import de.chrgroth.james.platform.domain.model.app.ReportView
import de.chrgroth.james.platform.domain.model.app.ReportViewRow
import de.chrgroth.james.platform.domain.model.app.ReportViewTab
import de.chrgroth.james.platform.domain.model.app.ReportWarningsPanel
import de.chrgroth.james.platform.domain.model.app.ReportYoyTransform
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.StatComparisonSpec
import de.chrgroth.james.platform.domain.model.app.StatGoalSpec
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import de.chrgroth.james.platform.domain.model.app.TableCellSpec
import de.chrgroth.james.platform.domain.model.app.TableColumnSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import de.chrgroth.james.platform.domain.model.app.TextPanelSpec
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.WarningsPanelSpec
import de.chrgroth.james.platform.domain.model.app.decodeListValue
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.model.app.formatUnitValue
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.readmodel.formatTimeBucketLabel
import de.chrgroth.james.platform.domain.model.readmodel.nextTimeBucket
import de.chrgroth.james.platform.domain.port.`in`.app.ReportScriptResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.round

/**
 * Executes a validated [ReportDefinition] against the data of exactly one installation ([allAppData] must only contain that installation's
 * objects - the sandbox boundary is drawn by the caller loading it) and builds the renderer-neutral [ReportView].
 *
 * A query selects the objects of its entity, keeps those whose time field lies in the range ([today] resolves relative ranges), applies the
 * filter rules via [FilterEvaluator] (same semantics as import filters, evaluated on a JSON view of the object: numbers as numbers, dates as
 * ISO strings, references as display text, a `ref` hop or an OBJECT property as nested object), then either sorts (without `sort`: newest
 * first) and limits, or aggregates (see [ReportAggregate]; sorted by bucket and group label unless sorted explicitly) and limits the aggregated
 * rows. Each query runs at most once per report and time range, however many panels use it.
 *
 * `config.<field>` values (filter values, stat thresholds, constant stat panels) read the App configuration object [configuration] (ADR 0027),
 * converted into the unit of the value they are compared with.
 *
 * Checks run through [ReportChecker] - only those a warnings panel shows; a tab counts the findings of its warnings panels.
 *
 * `kotlin` transformation steps run through [scripts] (ADR 0028); a failing script turns the panels reading its query into [ErrorPanelSpec]s.
 */
@Suppress("TooManyFunctions", "LargeClass")
internal class ReportExecutor(
  private val entities: List<EntityDefinition>,
  private val allAppData: List<AppData>,
  private val today: LocalDate,
  private val configuration: AppData? = null,
  private val scripts: ReportScriptRunner? = null,
  private val computeValues: (EntityDefinition, Map<String, String?>) -> Map<String, String?>,
) {
  private val configEntity = entities.find { it.configuration }
  private val entityById = entities.associateBy { it.id.value }
  private val appDataById by lazy { allAppData.associateBy { it.id.value } }
  private val displayTexts = DisplayTextResolver(entityById, allAppData)
  private val computedCache = HashMap<String, Map<String, String?>>()
  private val results = HashMap<String, QueryResult>()

  /**
   * One result row: the raw (storage form) value per result field, its [order] key for LAST (time value, bucket key or creation time) and, for a
   * row of a plain query, the [id] of its object (joins match references against it).
   */
  class ResultRow(val values: Map<String, String?>, val order: String, val id: String? = null)

  /**
   * One query's result rows plus the result fields they carry; [bucket] (with [periodStart]) the time bucket the rows are keyed by (aggregate or
   * resampled), [groupBy] the group field of a grouped aggregate. [error] says why a transformation step failed at runtime (a `kotlin` step); the
   * rows are empty then.
   */
  data class QueryResult(
    val columns: Map<String, ResultColumn>,
    val rows: List<ResultRow>,
    val bucket: TimeBucket? = null,
    val periodStart: MonthDay? = null,
    val groupBy: String? = null,
    val error: String? = null,
  )

  /** A variable's resolved selection: [value] as selected, [range] for `range: $name`, [filterValue] for filter values (null: drop the rule). */
  private class VariableState(val value: String, val range: ReportDateRange?, val filterValue: String?)

  private var definition: ReportDefinition = ReportDefinition.EMPTY
  private val variableStates = HashMap<String, VariableState>()
  private val transformed = HashMap<String, QueryResult>()
  private var checker: ReportChecker? = null

  /** Executes [definition]; [selections] holds the selected value per variable name (invalid or missing ones fall back to the default). */
  fun execute(reportId: String, name: String, definition: ReportDefinition, selections: Map<String, String> = emptyMap()): ReportView {
    this.definition = definition
    this.checker = ReportChecker(this, definition, today)
    val variables = definition.variables.map { variableSpec(it, selections[it.name]) }
    return ReportView(
      reportId = reportId,
      name = name,
      tabs = definition.tabs.map { tab ->
        val rows = tab.rows.map { row -> ReportViewRow(row.panels.mapNotNull { panel(it, definition) }) }
        val checks = tab.rows.flatMap { it.panels }.filterIsInstance<ReportWarningsPanel>().flatMap { panelChecks(it) }.distinctBy { it.name }
        ReportViewTab(tab.name, rows, checks.sumOf { warningsOf(it).size })
      },
      variables = variables,
    )
  }

  // region checks

  private fun panelChecks(panel: ReportWarningsPanel) = if (panel.checks.isEmpty()) definition.checks else panel.checks.mapNotNull { definition.check(it) }

  private fun warningsOf(check: ReportCheck) = checker?.warnings(check).orEmpty()

  private fun warningsPanel(panel: ReportWarningsPanel): PanelSpec {
    val warnings = panelChecks(panel).flatMap { warningsOf(it) }
    return WarningsPanelSpec(panel.title, warnings.take(MAX_WARNINGS), warnings.size, warnings.size > MAX_WARNINGS)
  }

  /** The dates of a configuration field (a DATE/DATETIME field or a LIST of dates), for gap check calendars. */
  internal fun configDates(path: String): List<LocalDate> {
    val field = configField(path) ?: return emptyList()
    val raw = configRaw(field) ?: return emptyList()
    val values = if (field.type == PropertyType.LIST) decodeListValue(raw) else listOf(raw)
    return values.mapNotNull { ReportFields.parseDate(it) }
  }

  // endregion

  // region variables

  private fun variableSpec(variable: ReportVariable, selection: String?): ReportVariableSpec = when (variable) {
    is ReportTimeRangeVariable -> {
      val value = selection?.takeIf { it in variable.options } ?: variable.default
      val range = RelativePeriod.byKey(value)?.resolve(today)
      variableStates[variable.name] = VariableState(value, range, null)
      val options = variable.options.map { ReportVariableOptionSpec(it, TIME_RANGE_LABELS[it] ?: it) }
      ReportVariableSpec(variable.name, variable.label ?: variable.name, TIME_RANGE_TYPE, options, value)
    }
    is ReportSelectVariable -> selectVariableSpec(variable, selection)
  }

  /** The options are the distinct values of the variable's field in its query's result; a bucket selected as range means that bucket's period. */
  private fun selectVariableSpec(variable: ReportSelectVariable, selection: String?): ReportVariableSpec {
    val result = definition.query(variable.query)?.let { result(it) }
    val column = result?.columns?.get(variable.field)
    val values = result?.rows.orEmpty().mapNotNull { it.values[variable.field]?.takeIf { value -> value.isNotEmpty() } }.distinct()
    val options = (if (variable.includeAll) listOf(ReportVariableOptionSpec("", ALL_LABEL)) else emptyList()) +
      values.map { value -> ReportVariableOptionSpec(value, column?.let { text(it, value) } ?: value) }
    val value = when {
      selection != null && options.any { it.value == selection } -> selection
      variable.default == ReportSelectVariable.ALL_VALUE && variable.includeAll -> ""
      variable.default == ReportSelectVariable.FIRST -> values.firstOrNull().orEmpty()
      variable.default == ReportSelectVariable.LAST -> values.lastOrNull().orEmpty()
      variable.default in values -> variable.default
      else -> values.firstOrNull().orEmpty()
    }
    val bucket = column?.bucket
    val range = if (bucket != null && value.isNotEmpty()) ReportBuckets.period(bucket, value, result.periodStart) else null
    val filterValue = value.takeIf { it.isNotEmpty() }?.let { if (column?.type == PropertyType.REF) displayTexts.displayTextOf(it) else it }
    variableStates[variable.name] = VariableState(value, range, filterValue)
    return ReportVariableSpec(variable.name, variable.label ?: variable.name, SELECT_TYPE, options, value)
  }

  /** The range a query runs with: a variable's selection, or its own (relative ranges resolved against today). */
  internal fun rangeOf(query: ReportQuery): ReportDateRange? = when (val range = query.range) {
    is ReportTimeRange.Variable -> variableStates[range.name]?.range
    else -> range?.resolve(today)
  }

  // endregion

  // region transforms

  /**
   * The query's result after its transformation steps (cached per name and range); null if its entity no longer resolves or if it is requested
   * again while being computed (an `asOf`/`join` cycle, rejected by validation anyway).
   */
  fun result(query: ReportQuery, range: ReportDateRange? = rangeOf(query)): QueryResult? {
    val cacheKey = "${query.name}|$range"
    transformed[cacheKey]?.let { return it }
    val steps = definition.transforms[query.name].orEmpty()
    val base = run(query, range) ?: return null
    if (!computing.add(query.name)) return null
    return try {
      transform(query, range, steps, base).also { transformed[cacheKey] = it }
    } finally {
      computing.remove(query.name)
    }
  }

  private fun transform(query: ReportQuery, range: ReportDateRange?, steps: List<ReportTransform>, base: QueryResult): QueryResult {
    if (steps.isEmpty()) return base
    val transformer = ReportTransformer(
      today = today,
      configNumber = ::configNumber,
      configValue = ::configValue,
      label = ::text,
      previousYear = { index ->
        // Year over year: the same query over the range one year earlier, with the steps before the yoy step; an open range covers it already.
        if (range == null || (range.from == null && range.to == null)) return@ReportTransformer null
        val previousRange = ReportDateRange(range.from?.minusYears(1), range.to?.minusYears(1))
        run(query, previousRange)?.let { transform(query, previousRange, steps.take(index), it) }
      },
      otherQuery = ::otherResult,
      script = { step, index, rows ->
        scripts?.run(step.script, rows, configValues, "${query.name}[$index]") ?: ReportScriptResult.Failure(SCRIPTS_UNAVAILABLE)
      },
    )
    return transformer.apply(query, range, steps, base)
  }

  /** The configuration's values by field name (top-level fields, raw storage values) for `kotlin` steps; empty without configuration. */
  private val configValues: Map<String, String?> by lazy {
    val entity = configEntity ?: return@lazy emptyMap()
    ReportFields.defaultFields(entity).associateWith { name -> configField(name)?.let { configRaw(it) } }
  }

  /** Queries whose result is being computed right now: guards `asOf`/`join` against cycles (rejected by validation anyway). */
  private val computing = mutableSetOf<String>()

  /** The transformed result of the query [name] read by an `asOf`/`join` step, with its own range; null if unknown or part of a cycle. */
  private fun otherResult(name: String): Pair<ReportQuery, QueryResult>? {
    val other = definition.query(name) ?: return null
    return result(other)?.let { other to it }
  }

  /** Names of the fields [steps] add, in step order; a resampled query starts with its bucket. */
  private fun transformFields(steps: List<ReportTransform>): List<String> = steps.flatMap { step ->
    when (step) {
      is ReportCalcTransform -> listOf(step.name)
      is ReportAggregateTransform -> step.aggregate.resultFields
      is ReportCumulativeTransform -> listOf(step.output)
      is ReportMovingAverageTransform -> listOf(step.output)
      is ReportYoyTransform -> listOf(step.output)
      is ReportForecastTransform -> listOf(step.output)
      is ReportResampleTransform -> listOf(ReportAggregate.BUCKET_FIELD)
      is ReportDeltaTransform -> if (step.bucket != null) listOfNotNull(ReportAggregate.BUCKET_FIELD, step.by, step.output) else listOf(step.output)
      is ReportAsOfTransform -> step.fields.map { step.prefix + it }
      is ReportJoinTransform -> step.fields.map { step.prefix + it }
      is ReportTopTransform -> listOfNotNull(ReportAggregate.BUCKET_FIELD.takeIf { step.bucket != null }, step.output)
      is ReportGoalTransform -> listOf(step.output, step.progress)
      is ReportKotlinTransform -> step.fields.map { it.name }
    }
  }

  // endregion

  private fun panel(panel: ReportPanel, definition: ReportDefinition): PanelSpec? {
    panelError(panel, definition)?.let { return ErrorPanelSpec(panel.type, panel.title, it) }
    return when (panel) {
      is ReportTextPanel -> TextPanelSpec(panel.title, panel.markdown)
      is ReportWarningsPanel -> warningsPanel(panel)
      is ReportTablePanel -> definition.query(panel.query)?.let { tablePanel(panel, it) }
      is ReportChartPanel -> chartPanel(panel, definition)
      is ReportStatPanel -> when (val config = panel.config) {
        null -> panel.query?.let { definition.query(it) }?.let { statPanel(panel, it) }
        else -> configStatPanel(panel, config)
      }
      is ReportPiePanel -> definition.query(panel.query)?.let { piePanel(panel, it) }
      is ReportHeatmapPanel -> definition.query(panel.query)?.let { heatmapPanel(panel, it) }
    }
  }

  /** Why a query the panel reads failed at runtime (a `kotlin` step), null if none did. */
  private fun panelError(panel: ReportPanel, definition: ReportDefinition): String? {
    val queries = when (panel) {
      is ReportTablePanel -> listOf(panel.query)
      is ReportChartPanel -> panel.series.map { it.query }.distinct()
      is ReportStatPanel -> listOfNotNull(panel.query)
      is ReportPiePanel -> listOf(panel.query)
      is ReportHeatmapPanel -> listOf(panel.query)
      is ReportTextPanel, is ReportWarningsPanel -> emptyList()
    }
    return queries.firstNotNullOfOrNull { name -> definition.query(name)?.let { result(it) }?.error?.let { "Abfrage „$name“: $it" } }
  }

  // region table

  private fun tablePanel(panel: ReportTablePanel, query: ReportQuery): PanelSpec? {
    val result = result(query) ?: return null
    val steps = definition.transforms[query.name].orEmpty()
    // After an `aggregate` step only its fields and those of the later steps are left.
    val regrouped = steps.indexOfLast { it is ReportAggregateTransform }
    val defaultFields = if (regrouped >= 0) {
      transformFields(steps.drop(regrouped)).distinct()
    } else {
      transformFields(steps).let { added ->
        val resampled = ReportAggregate.BUCKET_FIELD in added && query.aggregate?.bucket == null
        (if (resampled) listOf(ReportAggregate.BUCKET_FIELD) else emptyList()) + resultFieldPaths(query) + added
      }.distinct()
    }
    val columns = panel.columns.ifEmpty { defaultFields.filter { it in result.columns }.map { ReportColumn(it) } }
      .mapNotNull { column -> result.columns[column.field]?.let { column to it } }
    val limited = panel.limit?.let { result.rows.take(it) } ?: result.rows
    val visible = limited.take(MAX_TABLE_ROWS)
    return TablePanelSpec(
      title = panel.title,
      columns = columns.map { (column, field) -> TableColumnSpec(field.path, column.label ?: field.path, (field.type ?: PropertyType.STRING).name, unitSpec(field.unit)) },
      rows = visible.map { row -> columns.map { (_, field) -> cell(field, row.values[field.path]) } },
      totalRows = result.rows.size,
      truncated = limited.size > visible.size,
    )
  }

  private fun unitSpec(unit: PropertyUnit?): ReportUnitSpec? =
    unit?.let { ReportUnitSpec(it.family.name, (it.storageGranularity as Enum<*>).name, it.storageGranularity.symbol) }

  private fun cell(column: ResultColumn, raw: String?): TableCellSpec = TableCellSpec(raw, text(column, raw))

  /** The display text of a raw result value: bucket label, reference display text, ja/nein, or formatted like display texts. */
  internal fun text(column: ResultColumn, raw: String?): String {
    if (raw.isNullOrEmpty()) return ""
    column.bucket?.let { return formatTimeBucketLabel(it, raw) }
    return when (column.type) {
      PropertyType.REF -> displayTexts.displayTextOf(raw)
      PropertyType.BOOLEAN -> when (raw) {
        "true" -> YES
        "false" -> NO
        else -> raw
      }
      PropertyType.LONG, PropertyType.DOUBLE -> if (column.role == ColumnRole.VALUE) raw.toDoubleOrNull()?.let { formatNumber(it, column.unit) } ?: raw else format(column, raw)
      else -> format(column, raw)
    }
  }

  private fun format(column: ResultColumn, raw: String): String = PropertyValueFormat.format(column.type ?: PropertyType.STRING, column.unit, raw, column.listItemType)

  /**
   * A computed number formatted with its unit; composite units (time, distance) are rounded to their storage granularity, others to two
   * decimals, so averages do not show long fractions.
   */
  internal fun formatNumber(value: Double, unit: PropertyUnit?): String {
    if (unit == null) return PropertyValueFormat.formatNumber(value)
    val scale = if (unit.family.compositeFormat) 0 else 2
    return formatUnitValue(BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP), unit.storageGranularity)
  }

  /** Result fields of a query in their natural order: the aggregate's (bucket, group, values) or the queried fields. */
  private fun resultFieldPaths(query: ReportQuery): List<String> {
    query.aggregate?.let { return it.resultFields }
    val entity = entities.find { it.name == query.entity } ?: return emptyList()
    return query.fields.ifEmpty { ReportFields.defaultFields(entity) }
  }

  // endregion

  // region queries

  /**
   * Runs [query] (without its transforms) restricted to [range] (default: the query's own range or its variable's selection; cached per name
   * and range); null if its entity no longer resolves.
   */
  fun run(query: ReportQuery, range: ReportDateRange? = rangeOf(query)): QueryResult? {
    val cacheKey = "${query.name}|$range"
    results[cacheKey]?.let { return it }
    val entity = entities.find { it.name == query.entity } ?: return null
    val aggregate = query.aggregate
    val paths = if (aggregate == null) {
      resultFieldPaths(query) + query.sort.map { it.field }
    } else {
      listOfNotNull(aggregate.groupBy) + aggregate.values.mapNotNull { it.field }
    }.let { (it + query.filter.map { rule -> rule.sourcePath } + listOfNotNull(query.timeField)).distinct() }
    val fields = paths.mapNotNull { path -> (ReportFields.resolve(entity, path, entities) as? FieldResolution.Resolved)?.field?.let { path to it } }.toMap()

    var rows = allAppData.filter { it.entityType == entity.id }.map { item -> Row(item, fields.mapValues { (_, field) -> rawValue(item, field) }) }

    val timeField = query.timeField?.let { fields[it] }
    if (timeField != null && range != null) {
      rows = rows.filter { row -> toDate(timeField.type, row.values[timeField.path])?.let { it in range } ?: false }
    }

    // A `$name` value takes the variable's selection; a rule whose variable selects nothing ("Alle") is dropped.
    val activeFilter = query.filter.mapNotNull { rule ->
      val variable = ReportDefinitionParser.variableName(rule.value) ?: return@mapNotNull rule
      variableStates[variable]?.filterValue?.let { rule.copy(value = it) }
    }
    if (activeFilter.isNotEmpty()) {
      val records = rows.map { row -> filterRecord(row, fields) to row }
      val rowByRecord = IdentityHashMap<JsonNode, Row>().apply { records.forEach { (record, row) -> put(record, row) } }
      // A rule whose `config.<field>` value cannot be resolved never matches: an including rule keeps nothing, an excluding one drops nothing.
      val rules = activeFilter.map { rule -> rule to ReportFields.configPath(rule.value)?.let { configFilterValue(it, fields[rule.sourcePath]) } }
      val unresolved = rules.filter { (rule, value) -> ReportFields.configPath(rule.value) != null && value == null }.map { it.first }
      rows = if (unresolved.any { it.mode == FilterMode.INCLUDE }) {
        emptyList()
      } else {
        val effective = rules.filter { it.first !in unresolved }.map { (rule, value) -> if (value == null) rule else rule.copy(value = value) }
        FilterEvaluator.apply(records.map { it.first }, effective).map { rowByRecord.getValue(it) }
      }
    }

    val orderOf = { row: Row -> timeField?.let { row.values[it.path] }?.takeIf { it.isNotEmpty() } ?: row.item.createdAt.toString() }
    val result = if (aggregate == null) {
      rows = rows.sortedWith(comparator(query.sort, fields.mapValues { ResultColumn.of(it.value) }))
      query.limit?.let { rows = rows.take(it) }
      QueryResult(fields.mapValues { ResultColumn.of(it.value) }, rows.map { ResultRow(it.values, orderOf(it), it.item.id.value) })
    } else {
      aggregated(query, aggregate, fields, rows, orderOf)
    }
    return result.also { results[cacheKey] = it }
  }

  private class Row(val item: AppData, val values: Map<String, String?>)

  // region configuration

  private fun configField(path: String): ResolvedField? =
    configEntity?.let { entity -> (ReportFields.resolve(entity, path, entities) as? FieldResolution.Resolved)?.field }

  /** The raw (storage form) value of a configuration field, null without configuration object or value. */
  private fun configRaw(field: ResolvedField): String? = configuration?.let { rawValue(it, field) }?.takeIf { it.isNotEmpty() }

  /** A configuration number converted into [unit] (the unit of the value it is compared with; raw storage value without one). */
  internal fun configNumber(raw: String, unit: PropertyUnit?): Double? {
    val field = configField(ReportFields.configPath(raw) ?: return null) ?: return null
    val value = configRaw(field)?.toDoubleOrNull() ?: return null
    return ReportFields.convertUnit(value, field.unit, unit)
  }

  /** A configuration value ([path] being the field) as expressions read it: typed by the field, raw storage form, references as display text. */
  internal fun configValue(path: String): ExpressionValue? {
    val field = configField(path) ?: return null
    val raw = configRaw(field) ?: return null
    if (field.type == PropertyType.REF) return ExpressionValue.Text(displayTexts.displayTextOf(raw))
    return ExpressionValue.of(raw, ReportExpressions.typeOf(field.type))
  }

  /**
   * The value of a `config.<field>` filter value ([path] being the field) as the filtered [target] field compares it: a reference by its display
   * text, a number converted into the target's unit; null if the field or its value is missing.
   */
  private fun configFilterValue(path: String, target: ResolvedField?): String? {
    val field = configField(path) ?: return null
    val value = configRaw(field) ?: return null
    return when {
      field.type == PropertyType.REF -> displayTexts.displayTextOf(value)
      ReportFields.isNumericType(field.type) && target != null && ReportFields.isNumericType(target.type) -> {
        val number = value.toDoubleOrNull() ?: return value
        BigDecimal.valueOf(ReportFields.convertUnit(number, field.unit, target.unit)).stripTrailingZeros().toPlainString()
      }
      else -> value
    }
  }

  // endregion

  private fun rawValue(item: AppData, field: ResolvedField): String? {
    val source = if (field.refProperty == null) {
      item
    } else {
      val refId = item.data[field.refProperty.id.value]?.takeIf { it.isNotEmpty() } ?: return null
      appDataById[refId] ?: return null
    }
    if (field.objectPath.isNotEmpty()) return nestedValue(source, field)
    return if (field.isComputed) computed(source)[field.valueKey] else source.data[field.valueKey]
  }

  /** A value nested in OBJECT properties: objects are stored as JSON maps keyed by property id, nested scalar values as strings. */
  private fun nestedValue(item: AppData, field: ResolvedField): String? {
    var current: Map<*, *> = runCatching { decodeObjectValue(item.data[field.objectPath.first().id.value]) }.getOrNull() ?: return null
    for (parent in field.objectPath.drop(1)) {
      current = current[parent.id.value] as? Map<*, *> ?: return null
    }
    return current[field.valueKey]?.toString()
  }

  private fun computed(item: AppData): Map<String, String?> = computedCache.getOrPut(item.id.value) {
    entityById[item.entityType.value]?.let { computeValues(it, item.data) } ?: emptyMap()
  }

  private fun toDate(type: PropertyType, raw: String?): LocalDate? = when {
    raw.isNullOrEmpty() -> null
    type == PropertyType.DATE -> runCatching { LocalDate.parse(raw) }.getOrNull()
    type == PropertyType.DATETIME -> runCatching { LocalDateTime.parse(raw).toLocalDate() }.getOrNull()
    else -> null
  }

  /** The JSON view filter rules are evaluated on: `{field: value, ref: {field: value}, object: {nested: value}}`. */
  private fun filterRecord(row: Row, fields: Map<String, ResolvedField>): ObjectNode {
    val record = JsonNodeFactory.instance.objectNode()
    // Shallow fields first, so a nested path (`schuhe.name`) nests its value below the reference rather than being overwritten by it.
    fields.values.sortedBy { it.segments.size }.forEach { field ->
      var target = record
      field.segments.dropLast(1).forEach { segment -> target = target.get(segment) as? ObjectNode ?: target.putObject(segment) }
      val key = field.segments.last()
      val raw = row.values[field.path]
      if (raw == null) target.putNull(key) else target.set<JsonNode>(key, jsonValue(field, raw))
    }
    return record
  }

  private fun jsonValue(field: ResolvedField, raw: String): JsonNode = when (field.type) {
    PropertyType.LONG -> raw.toLongOrNull()?.let { JsonNodeFactory.instance.numberNode(it) }
    PropertyType.DOUBLE -> raw.toDoubleOrNull()?.let { JsonNodeFactory.instance.numberNode(it) }
    PropertyType.REF -> JsonNodeFactory.instance.textNode(displayTexts.displayTextOf(raw))
    else -> null
  } ?: JsonNodeFactory.instance.textNode(raw)

  // endregion

  // region aggregation

  private fun aggregated(
    query: ReportQuery,
    aggregate: ReportAggregate,
    fields: Map<String, ResolvedField>,
    rows: List<Row>,
    orderOf: (Row) -> String,
  ): QueryResult {
    val timeField = query.timeField?.let { fields[it] }
    val groupField = aggregate.groupBy?.let { fields[it] }
    val columns = LinkedHashMap<String, ResultColumn>()
    aggregate.bucket?.let { columns[ReportAggregate.BUCKET_FIELD] = ResultColumn(ReportAggregate.BUCKET_FIELD, ColumnRole.BUCKET, PropertyType.STRING, bucket = it) }
    groupField?.let { columns[it.path] = ResultColumn.of(it, ColumnRole.GROUP) }
    aggregate.values.forEach { value -> columns[value.name] = ResultColumn.value(value.name, value.function, value.field?.let { fields[it] }?.let { ResultColumn.of(it) }) }

    val inputs = rows.map { row -> ReportAggregation.Input(row.values, timeField?.let { toDate(it.type, row.values[it.path]) }, orderOf(row)) }
    var resultRows = ReportAggregation.rows(aggregate, columns, inputs)
    val groupColumn = groupField?.let { columns[it.path] }
    resultRows = if (query.sort.isEmpty()) {
      ReportAggregation.sorted(resultRows, groupColumn, ::text)
    } else {
      val comparator = query.sort.mapNotNull { sort -> columns[sort.field]?.let { valueComparator(it, sort.direction) } }.reduceOrNull { acc, next -> acc.then(next) }
      comparator?.let { c -> resultRows.sortedWith { a, b -> c.compare(a.values, b.values) } } ?: resultRows
    }
    query.limit?.let { resultRows = resultRows.take(it) }
    return QueryResult(columns, resultRows, aggregate.bucket, aggregate.periodStart, aggregate.groupBy)
  }

  // endregion

  // region sorting

  private fun comparator(sorts: List<ReportSort>, columns: Map<String, ResultColumn>): Comparator<Row> {
    val newestFirst = compareByDescending<Row> { it.item.createdAt }.thenBy { it.item.id.value }
    if (sorts.isEmpty()) return newestFirst
    return sorts.mapNotNull { sort -> columns[sort.field]?.let { column -> valueComparator(column, sort.direction) } }
      .map { values -> Comparator<Row> { a, b -> values.compare(a.values, b.values) } }
      .reduceOrNull { acc, next -> acc.then(next) }
      ?.then(newestFirst) ?: newestFirst
  }

  private fun valueComparator(column: ResultColumn, direction: SortDirection): Comparator<Map<String, String?>> {
    val values = Comparator<String> { a, b -> compareValues(column, a, b) }
    val directed = if (direction == SortDirection.DESC) values.reversed() else values
    // Empty values always last, whatever the direction.
    return Comparator { a, b ->
      val va = a[column.path]?.takeIf { it.isNotEmpty() }
      val vb = b[column.path]?.takeIf { it.isNotEmpty() }
      when {
        va == null && vb == null -> 0
        va == null -> 1
        vb == null -> -1
        else -> directed.compare(va, vb)
      }
    }
  }

  private fun compareValues(column: ResultColumn, a: String, b: String): Int = when (column.type) {
    PropertyType.LONG, PropertyType.DOUBLE -> {
      val da = a.toDoubleOrNull()
      val db = b.toDoubleOrNull()
      if (da != null && db != null) da.compareTo(db) else a.compareTo(b)
    }
    PropertyType.REF -> displayTexts.displayTextOf(a).compareTo(displayTexts.displayTextOf(b), ignoreCase = true)
    PropertyType.DATE, PropertyType.DATETIME, PropertyType.TIME -> a.compareTo(b)
    else -> if (column.bucket != null) a.compareTo(b) else a.compareTo(b, ignoreCase = true)
  }

  // endregion

  // region charts

  /** One series' data: its definition, its query and result, and the value column it plots. */
  private class SeriesData(val series: ReportSeries, val query: ReportQuery, val result: QueryResult, val column: ResultColumn)

  @Suppress("LongMethod")
  private fun chartPanel(panel: ReportChartPanel, definition: ReportDefinition): PanelSpec {
    val data = panel.series.mapNotNull { series ->
      val query = definition.query(series.query) ?: return@mapNotNull null
      val result = result(query) ?: return@mapNotNull null
      result.columns[series.field]?.let { SeriesData(series, query, result, it) }
    }
    val axes = ReportAxis.entries.filter { axis -> panel.series.any { it.axis == axis } }.ifEmpty { listOf(ReportAxis.LEFT) }
    val axisGranularity = axes.associateWith { axis -> data.firstOrNull { it.series.axis == axis && it.column.unit != null }?.column?.unit?.let { displayGranularity(it) } }
    val yAxes = axes.map { ChartYAxisSpec(it.key, axisGranularity[it]?.symbol) }
    val single = data.size == 1

    fun point(item: SeriesData, x: String, raw: String?): ChartPointSpec {
      val value = raw?.toDoubleOrNull() ?: return ChartPointSpec(x, null, "")
      return ChartPointSpec(x, toDisplay(value, item.column.unit, axisGranularity[item.series.axis]), text(item.column, raw))
    }

    fun seriesSpec(item: SeriesData, suffix: String?, points: List<ChartPointSpec>): ChartSeriesSpec {
      val base = item.series.name ?: item.series.field
      val name = when {
        suffix == null -> base
        single -> suffix
        else -> "$base – $suffix"
      }
      return ChartSeriesSpec(name, item.series.style.key, axes.indexOf(item.series.axis), points)
    }

    val first = data.firstOrNull()?.result
    val (xAxis, series) = when {
      data.isEmpty() -> ChartXAxisSpec(ChartXAxisType.CATEGORY) to emptyList()
      panel.type == ReportPanelType.TIMESERIES && first?.bucket == null -> timeSeries(data, ::point, ::seriesSpec)
      panel.type == ReportPanelType.BAR && first?.groupBy != null -> groupCategories(data, ::point, ::seriesSpec)
      else -> bucketCategories(data, ::point, ::seriesSpec)
    }
    return ChartPanelSpec(panel.type, panel.title, xAxis, yAxes, series, panel.stack, panel.zoom, panel.horizontal && panel.type == ReportPanelType.BAR)
  }

  /** Plain queries over a time axis: one point per row with a time value, oldest first. */
  private fun timeSeries(
    data: List<SeriesData>,
    point: (SeriesData, String, String?) -> ChartPointSpec,
    seriesSpec: (SeriesData, String?, List<ChartPointSpec>) -> ChartSeriesSpec,
  ): Pair<ChartXAxisSpec, List<ChartSeriesSpec>> = ChartXAxisSpec(ChartXAxisType.TIME) to data.map { item ->
    val timePath = item.query.timeField
    val points = item.result.rows.mapNotNull { row -> timePath?.let { row.values[it] }?.takeIf { it.isNotEmpty() }?.let { it to row.values[item.column.path] } }
      .filter { it.second?.toDoubleOrNull() != null }
      .sortedBy { it.first }
      .takeLast(MAX_CHART_POINTS)
      .map { (x, raw) -> point(item, x, raw) }
    seriesSpec(item, null, points)
  }

  /** Bucketed queries over consecutive buckets (gaps filled with empty points); grouped queries split into one series per group. */
  private fun bucketCategories(
    data: List<SeriesData>,
    point: (SeriesData, String, String?) -> ChartPointSpec,
    seriesSpec: (SeriesData, String?, List<ChartPointSpec>) -> ChartSeriesSpec,
  ): Pair<ChartXAxisSpec, List<ChartSeriesSpec>> {
    val bucket = data.firstNotNullOfOrNull { it.result.bucket } ?: return ChartXAxisSpec(ChartXAxisType.CATEGORY) to emptyList()
    val keys = consecutiveBuckets(bucket, data.flatMap { item -> item.result.rows.mapNotNull { it.values[ReportAggregate.BUCKET_FIELD] } }.toSortedSet())
    val series = data.flatMap { item ->
      val groupColumn = item.result.groupBy?.let { item.result.columns[it] }
      splitBy(item.result.rows, groupColumn).map { (group, rows) ->
        val byBucket = rows.associateBy({ it.values[ReportAggregate.BUCKET_FIELD] }, { it.values[item.column.path] })
        seriesSpec(item, group, keys.map { key -> point(item, key, byBucket[key]) })
      }
    }
    return ChartXAxisSpec(ChartXAxisType.CATEGORY, keys.map { formatTimeBucketLabel(bucket, it) }) to series
  }

  /** Grouped bar queries: one category per group (in result order); bucketed queries split into one series per bucket. */
  private fun groupCategories(
    data: List<SeriesData>,
    point: (SeriesData, String, String?) -> ChartPointSpec,
    seriesSpec: (SeriesData, String?, List<ChartPointSpec>) -> ChartSeriesSpec,
  ): Pair<ChartXAxisSpec, List<ChartSeriesSpec>> {
    val labels = LinkedHashMap<String, String>()
    data.forEach { item ->
      val groupColumn = item.result.groupBy?.let { item.result.columns[it] } ?: return@forEach
      item.result.rows.forEach { row ->
        val key = row.values[groupColumn.path].orEmpty()
        labels.putIfAbsent(key, text(groupColumn, key).ifEmpty { EMPTY_GROUP })
      }
    }
    val keys = labels.keys.toList()
    val series = data.flatMap { item ->
      val groupPath = item.result.groupBy ?: return@flatMap emptyList()
      val bucketColumn = item.result.columns[ReportAggregate.BUCKET_FIELD]
      splitBy(item.result.rows, bucketColumn).map { (bucketLabel, rows) ->
        val byGroup = rows.associateBy({ it.values[groupPath].orEmpty() }, { it.values[item.column.path] })
        seriesSpec(item, bucketLabel, keys.map { key -> point(item, key, byGroup[key]) })
      }
    }
    return ChartXAxisSpec(ChartXAxisType.CATEGORY, keys.map { labels.getValue(it) }) to series
  }

  /** Rows split by [column]'s value (label, rows), ordered by bucket key or label; a single unlabelled part without [column]. */
  private fun splitBy(rows: List<ResultRow>, column: ResultColumn?): List<Pair<String?, List<ResultRow>>> {
    column ?: return listOf(null to rows)
    val parts = rows.groupBy { it.values[column.path].orEmpty() }
    val ordered = if (column.bucket != null) parts.entries.sortedBy { it.key } else parts.entries.sortedBy { text(column, it.key).lowercase() }
    return ordered.map { (key, partRows) -> text(column, key).ifEmpty { EMPTY_GROUP } to partRows }
  }

  /** All bucket keys from the first to the last of [keys], so gaps show as gaps; [keys] itself if that would exceed [MAX_CATEGORIES]. */
  private fun consecutiveBuckets(bucket: TimeBucket, keys: Set<String>): List<String> {
    if (keys.isEmpty()) return emptyList()
    val last = keys.last()
    val filled = mutableListOf(keys.first())
    while (filled.last() < last) {
      val next = nextTimeBucket(bucket, filled.last()) ?: return keys.toList()
      if (filled.size >= MAX_CATEGORIES) return keys.toList()
      filled += next
    }
    return filled
  }

  /** The unit charts plot a value family in: euros for money, else the property's default (input) granularity, e.g. km for meters. */
  private fun displayGranularity(unit: PropertyUnit): Granularity =
    if (unit.family == UnitFamily.CURRENCY) CurrencyGranularity.EUROS else unit.defaultGranularity

  private fun toDisplay(value: Double, unit: PropertyUnit?, axisGranularity: Granularity?): Double {
    unit ?: return value
    val target = axisGranularity?.takeIf { it.family == unit.family } ?: displayGranularity(unit)
    return value * unit.storageGranularity.factorToSmallestUnit / target.factorToSmallestUnit
  }

  // endregion

  // region pie and heatmap

  /** Shares per category: the rows' values summed per category, positive sums only, largest first; values in the field's display unit. */
  private fun piePanel(panel: ReportPiePanel, query: ReportQuery): PanelSpec? {
    val result = result(query) ?: return null
    val column = result.columns[panel.field] ?: return null
    val categoryField = panel.category ?: result.groupBy ?: ReportAggregate.BUCKET_FIELD.takeIf { result.bucket != null } ?: return null
    val categoryColumn = result.columns[categoryField] ?: return null
    val sums = LinkedHashMap<String, Double>()
    result.rows.forEach { row ->
      val value = row.values[panel.field]?.toDoubleOrNull() ?: return@forEach
      sums.merge(row.values[categoryField].orEmpty(), value, Double::plus)
    }
    val positive = sums.filterValues { it > 0 }
    val total = positive.values.sum()
    val unit = column.unit
    val granularity = unit?.let { displayGranularity(it) }
    val slices = positive.entries.sortedByDescending { it.value }.map { (key, sum) ->
      PieSliceSpec(text(categoryColumn, key).ifEmpty { EMPTY_GROUP }, toDisplay(sum, unit, granularity), formatNumber(sum, unit), percentText(sum / total * PERCENT))
    }
    return PiePanelSpec(panel.title, slices, granularity?.symbol, panel.donut)
  }

  private fun percentText(percent: Double): String = PropertyValueFormat.formatNumber(round(percent * PERCENT_DIGITS) / PERCENT_DIGITS) + " %"

  /** A calendar (cells per day) or matrix (cells per x/y pair) heatmap; each cell reduces its rows, cells without value are left out. */
  private fun heatmapPanel(panel: ReportHeatmapPanel, query: ReportQuery): PanelSpec? {
    val result = result(query) ?: return null
    val column = panel.field?.let { result.columns[it] ?: return null }
    val unit = column?.unit?.takeIf { panel.reduce != ReportReduce.COUNT }
    val granularity = unit?.let { displayGranularity(it) }

    fun cell(x: String, y: String?, rows: List<ResultRow>): HeatmapCellSpec? {
      val value = reduce(panel.reduce, result.copy(rows = rows), column) ?: return null
      return HeatmapCellSpec(x, y, toDisplay(value, unit, granularity), formatNumber(value, unit))
    }

    val thresholds = panel.thresholds.mapNotNull { threshold ->
      val number = if (ReportFields.configPath(threshold.value) != null) configNumber(threshold.value, unit) else ReportValues.parseNumber(threshold.value, unit)
      number?.let { HeatmapThresholdSpec(toDisplay(it, unit, granularity), threshold.color) }
    }.sortedBy { it.value }

    val spec = if (panel.calendar) calendarHeatmap(panel, query, result, ::cell) else matrixHeatmap(panel, result, ::cell) ?: return null
    val values = spec.cells.map { it.value }
    return spec.copy(unit = granularity?.symbol, min = values.minOrNull(), max = values.maxOrNull(), thresholds = thresholds)
  }

  /** One cell per day with rows, within the query's range (open bounds: the days with data), at most the latest [MAX_HEATMAP_YEARS] years. */
  private fun calendarHeatmap(
    panel: ReportHeatmapPanel,
    query: ReportQuery,
    result: QueryResult,
    cell: (String, String?, List<ResultRow>) -> HeatmapCellSpec?,
  ): HeatmapPanelSpec {
    val byDay = result.rows.mapNotNull { row -> dayOf(query, result, row)?.let { it to row } }.groupBy({ it.first }, { it.second })
    val range = rangeOf(query)
    val to = range?.to ?: byDay.keys.maxOrNull()
    val earliest = to?.minusYears(MAX_HEATMAP_YEARS)?.plusDays(1)
    val from = (range?.from ?: byDay.keys.minOrNull())?.let { start -> earliest?.let { maxOf(start, it) } ?: start }
    val cells = byDay.entries.filter { (day, _) -> from != null && to != null && !day.isBefore(from) && !day.isAfter(to) }
      .sortedBy { it.key }
      .mapNotNull { (day, rows) -> cell(day.toString(), null, rows) }
    return HeatmapPanelSpec(panel.title, true, cells, null, null, null, from = from?.toString(), to = to?.toString())
  }

  /** The day a row stands for: its bucket's start (a `TAG` bucket), else its time field's value. */
  private fun dayOf(query: ReportQuery, result: QueryResult, row: ResultRow): LocalDate? {
    result.bucket?.let { bucket -> return row.values[ReportAggregate.BUCKET_FIELD]?.let { ReportBuckets.period(bucket, it, result.periodStart)?.from } }
    return ReportFields.parseDate(query.timeField?.let { row.values[it] })
  }

  /** One cell per pair of x and y values; categories in bucket order (time buckets) or order of appearance, labelled like table cells. */
  private fun matrixHeatmap(panel: ReportHeatmapPanel, result: QueryResult, cell: (String, String?, List<ResultRow>) -> HeatmapCellSpec?): HeatmapPanelSpec? {
    val xColumn = result.columns[panel.x ?: return null] ?: return null
    val yColumn = result.columns[panel.y ?: return null] ?: return null
    fun keys(column: ResultColumn): List<String> {
      val keys = result.rows.map { it.values[column.path].orEmpty() }.distinct()
      return if (column.bucket != null) keys.sorted() else keys
    }
    fun label(column: ResultColumn, key: String) = text(column, key).ifEmpty { EMPTY_GROUP }
    val xKeys = keys(xColumn)
    val yKeys = keys(yColumn)
    val byPair = result.rows.groupBy { it.values[xColumn.path].orEmpty() to it.values[yColumn.path].orEmpty() }
    val cells = xKeys.flatMap { x -> yKeys.mapNotNull { y -> byPair[x to y]?.let { rows -> cell(label(xColumn, x), label(yColumn, y), rows) } } }
    return HeatmapPanelSpec(
      panel.title,
      false,
      cells,
      null,
      null,
      null,
      xCategories = xKeys.map { label(xColumn, it) }.distinct(),
      yCategories = yKeys.map { label(yColumn, it) }.distinct(),
    )
  }

  // endregion

  // region stat

  private fun statPanel(panel: ReportStatPanel, query: ReportQuery): PanelSpec? {
    val range = rangeOf(query)
    val result = result(query, range) ?: return null
    val column = panel.field?.let { result.columns[it] }
    val unit = column?.unit?.takeIf { panel.reduce != ReportReduce.COUNT }
    val value = reduce(panel.reduce, result, column)
    val color = thresholdColor(panel, value, unit)
    val comparison = if (panel.compare) range?.previous()?.let { previousRange -> comparison(panel, query, previousRange, value, unit) } else null
    val goal = panel.goal?.let { goal(it, value, result, unit) }
    return StatPanelSpec(panel.title, value, value?.let { formatNumber(it, unit) } ?: NO_VALUE, color, comparison, goal)
  }

  /** A stat's target (a number, unit text, `config.<field>` or the latest value of a result field) and the value's progress towards it. */
  private fun goal(raw: String, value: Double?, result: QueryResult, unit: PropertyUnit?): StatGoalSpec {
    val target = goalTarget(raw, unit) ?: result.columns[raw.trim()]?.let { column ->
      reduce(ReportReduce.LAST, result, column)?.let { ReportFields.convertUnit(it, column.unit, unit) }
    }
    val percent = if (target != null && value != null && target != 0.0) value / target * PERCENT else null
    return StatGoalSpec(target, target?.let { formatNumber(it, unit) } ?: NO_VALUE, percent, percent?.let { PropertyValueFormat.formatNumber(round(it)) + " %" })
  }

  /** A constant goal target in [unit]: a `config.<field>` value or a number / unit text; null for anything else (a field name). */
  internal fun goalTarget(raw: String, unit: PropertyUnit?): Double? =
    if (ReportFields.configPath(raw) != null) configNumber(raw, unit) else ReportValues.parseNumber(raw, unit)

  /** A configuration field shown as a constant (`{ stat: Jahresziel, config: zielKm }`). */
  private fun configStatPanel(panel: ReportStatPanel, path: String): PanelSpec? {
    val field = configField(path) ?: return null
    val value = configRaw(field)?.toDoubleOrNull()
    return StatPanelSpec(panel.title, value, value?.let { formatNumber(it, field.unit) } ?: NO_VALUE, thresholdColor(panel, value, field.unit))
  }

  /** The color of the last threshold (ascending) at most [value]; threshold values may be numbers, unit texts or `config.<field>` values. */
  private fun thresholdColor(panel: ReportStatPanel, value: Double?, unit: PropertyUnit?): String? {
    val thresholds = panel.thresholds.mapNotNull { threshold ->
      val number = if (ReportFields.configPath(threshold.value) != null) configNumber(threshold.value, unit) else ReportValues.parseNumber(threshold.value, unit)
      number?.let { it to threshold.color }
    }.sortedBy { it.first }
    return value?.let { v -> thresholds.lastOrNull { it.first <= v }?.second }
  }

  private fun comparison(panel: ReportStatPanel, query: ReportQuery, previousRange: ReportDateRange, value: Double?, unit: PropertyUnit?): StatComparisonSpec? {
    val previousResult = result(query, previousRange) ?: return null
    val previous = reduce(panel.reduce, previousResult, panel.field?.let { previousResult.columns[it] })
    val delta = if (value != null && previous != null) value - previous else null
    val deltaText = delta?.let { (if (it < 0) "−" else "+") + formatNumber(abs(it), unit) } ?: NO_VALUE
    val percentText = if (delta != null && previous != null && previous != 0.0) {
      val percent = delta / abs(previous) * PERCENT
      (if (percent < 0) "−" else "+") + PropertyValueFormat.formatNumber(abs(percent)) + " %"
    } else {
      null
    }
    val trend = when {
      delta == null || delta == 0.0 -> "flat"
      delta > 0 -> "up"
      else -> "down"
    }
    val periodText = "${PropertyValueFormat.formatDate(previousRange.from!!)} – ${PropertyValueFormat.formatDate(previousRange.to!!)}"
    return StatComparisonSpec(previous, previous?.let { formatNumber(it, unit) } ?: NO_VALUE, deltaText, percentText, trend, periodText)
  }

  /** Reduces [column] over all rows; COUNT without a column counts rows. SUM of no rows is 0, the others have no value then. */
  private fun reduce(reduce: ReportReduce, result: QueryResult, column: ResultColumn?): Double? {
    if (reduce == ReportReduce.COUNT) {
      return (if (column == null) result.rows.size else result.rows.count { !it.values[column.path].isNullOrEmpty() }).toDouble()
    }
    column ?: return null
    val contributions = result.rows.mapNotNull { row -> row.values[column.path]?.toDoubleOrNull()?.let { row to it } }
    val numbers = contributions.map { it.second }
    return when (reduce) {
      ReportReduce.SUM -> numbers.sum()
      ReportReduce.AVG -> numbers.takeIf { it.isNotEmpty() }?.average()
      ReportReduce.MIN -> numbers.minOrNull()
      ReportReduce.MAX -> numbers.maxOrNull()
      ReportReduce.LAST -> contributions.maxByOrNull { it.first.order }?.second
      ReportReduce.COUNT -> numbers.size.toDouble()
    }
  }

  // endregion

  companion object {
    /** Platform cap for the rows one table panel ships to the browser, whatever the query/panel limit says. */
    const val MAX_TABLE_ROWS = 1_000

    /** Platform cap for the points of one time-axis series (the latest are kept). */
    const val MAX_CHART_POINTS = 5_000

    /** Above this many buckets gaps are not filled any more. */
    const val MAX_CATEGORIES = 2_000

    /** Platform cap for the findings one warnings panel ships to the browser. */
    const val MAX_WARNINGS = 500

    private const val YES = "ja"
    private const val NO = "nein"
    private const val NO_VALUE = "–"
    private const val EMPTY_GROUP = "–"
    private const val PERCENT = 100.0
    private const val PERCENT_DIGITS = 10.0
    private const val SCRIPTS_UNAVAILABLE = "Kotlin-Skripte sind hier nicht verfügbar."

    /** A calendar heatmap shows at most this many years (the latest). */
    const val MAX_HEATMAP_YEARS = 5L
    private const val TIME_RANGE_TYPE = "timeRange"
    private const val SELECT_TYPE = "select"
    private const val ALL_LABEL = "Alle"

    /** German labels of the time range variable options. */
    private val TIME_RANGE_LABELS = mapOf(
      RelativePeriod.LAST_7_DAYS.key to "Letzte 7 Tage",
      RelativePeriod.LAST_30_DAYS.key to "Letzte 30 Tage",
      RelativePeriod.LAST_90_DAYS.key to "Letzte 90 Tage",
      RelativePeriod.LAST_12_MONTHS.key to "Letzte 12 Monate",
      RelativePeriod.THIS_MONTH.key to "Dieser Monat",
      RelativePeriod.LAST_MONTH.key to "Letzter Monat",
      RelativePeriod.THIS_YEAR.key to "Dieses Jahr",
      RelativePeriod.LAST_YEAR.key to "Letztes Jahr",
      ReportTimeRangeVariable.ALL to "Gesamter Zeitraum",
    )
  }
}

/** Runs the script of a `kotlin` step (see [de.chrgroth.james.platform.domain.port.`in`.app.ReportScriptPort]); [label] names the step. */
internal fun interface ReportScriptRunner {
  fun run(script: String, rows: List<Map<String, String?>>, config: Map<String, String?>, label: String): ReportScriptResult
}
