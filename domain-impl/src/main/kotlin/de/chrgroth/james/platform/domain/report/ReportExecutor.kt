package de.chrgroth.james.platform.domain.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
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
import de.chrgroth.james.platform.domain.model.app.Granularity
import de.chrgroth.james.platform.domain.model.app.PanelSpec
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.PropertyValueFormat
import de.chrgroth.james.platform.domain.model.app.ReportAggregate
import de.chrgroth.james.platform.domain.model.app.ReportAggregateFunction
import de.chrgroth.james.platform.domain.model.app.ReportAggregateValue
import de.chrgroth.james.platform.domain.model.app.ReportAxis
import de.chrgroth.james.platform.domain.model.app.ReportChartPanel
import de.chrgroth.james.platform.domain.model.app.ReportColumn
import de.chrgroth.james.platform.domain.model.app.ReportDateRange
import de.chrgroth.james.platform.domain.model.app.ReportDefinition
import de.chrgroth.james.platform.domain.model.app.ReportPanel
import de.chrgroth.james.platform.domain.model.app.ReportPanelType
import de.chrgroth.james.platform.domain.model.app.ReportQuery
import de.chrgroth.james.platform.domain.model.app.ReportReduce
import de.chrgroth.james.platform.domain.model.app.ReportSeries
import de.chrgroth.james.platform.domain.model.app.ReportSort
import de.chrgroth.james.platform.domain.model.app.ReportStatPanel
import de.chrgroth.james.platform.domain.model.app.ReportTablePanel
import de.chrgroth.james.platform.domain.model.app.ReportTextPanel
import de.chrgroth.james.platform.domain.model.app.ReportUnitSpec
import de.chrgroth.james.platform.domain.model.app.ReportView
import de.chrgroth.james.platform.domain.model.app.ReportViewRow
import de.chrgroth.james.platform.domain.model.app.ReportViewTab
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.StatComparisonSpec
import de.chrgroth.james.platform.domain.model.app.StatPanelSpec
import de.chrgroth.james.platform.domain.model.app.TableCellSpec
import de.chrgroth.james.platform.domain.model.app.TableColumnSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import de.chrgroth.james.platform.domain.model.app.TextPanelSpec
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.decodeObjectValue
import de.chrgroth.james.platform.domain.model.app.formatUnitValue
import de.chrgroth.james.platform.domain.model.readmodel.encodeTimeBucket
import de.chrgroth.james.platform.domain.model.readmodel.formatTimeBucketLabel
import de.chrgroth.james.platform.domain.model.readmodel.nextTimeBucket
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.IdentityHashMap
import kotlin.math.abs

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
 */
@Suppress("TooManyFunctions", "LargeClass")
internal class ReportExecutor(
  private val entities: List<EntityDefinition>,
  private val allAppData: List<AppData>,
  private val today: LocalDate,
  private val configuration: AppData? = null,
  private val computeValues: (EntityDefinition, Map<String, String?>) -> Map<String, String?>,
) {
  private val configEntity = entities.find { it.configuration }
  private val entityById = entities.associateBy { it.id.value }
  private val appDataById by lazy { allAppData.associateBy { it.id.value } }
  private val displayTexts = DisplayTextResolver(entityById, allAppData)
  private val computedCache = HashMap<String, Map<String, String?>>()
  private val results = HashMap<String, QueryResult>()

  /** One result row: the raw (storage form) value per result field, and its [order] key for LAST (time value, bucket key or creation time). */
  class ResultRow(val values: Map<String, String?>, val order: String)

  /** One query's result rows plus the result fields they carry. */
  class QueryResult(val columns: Map<String, ResultColumn>, val rows: List<ResultRow>)

  fun execute(reportId: String, name: String, definition: ReportDefinition): ReportView = ReportView(
    reportId = reportId,
    name = name,
    tabs = definition.tabs.map { tab ->
      ReportViewTab(tab.name, tab.rows.map { row -> ReportViewRow(row.panels.mapNotNull { panel(it, definition) }) })
    },
  )

  private fun panel(panel: ReportPanel, definition: ReportDefinition): PanelSpec? = when (panel) {
    is ReportTextPanel -> TextPanelSpec(panel.title, panel.markdown)
    is ReportTablePanel -> definition.query(panel.query)?.let { tablePanel(panel, it) }
    is ReportChartPanel -> chartPanel(panel, definition)
    is ReportStatPanel -> when (val config = panel.config) {
      null -> panel.query?.let { definition.query(it) }?.let { statPanel(panel, it) }
      else -> configStatPanel(panel, config)
    }
  }

  // region table

  private fun tablePanel(panel: ReportTablePanel, query: ReportQuery): PanelSpec? {
    val result = run(query) ?: return null
    val columns = panel.columns.ifEmpty { resultFieldPaths(query).filter { it in result.columns }.map { ReportColumn(it) } }
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
  private fun text(column: ResultColumn, raw: String?): String {
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
  private fun formatNumber(value: Double, unit: PropertyUnit?): String {
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

  /** Runs [query] restricted to [range] (default: the query's own range; cached per name and range); null if its entity no longer resolves. */
  fun run(query: ReportQuery, range: ReportDateRange? = query.range?.resolve(today)): QueryResult? {
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

    if (query.filter.isNotEmpty()) {
      val records = rows.map { row -> filterRecord(row, fields) to row }
      val rowByRecord = IdentityHashMap<JsonNode, Row>().apply { records.forEach { (record, row) -> put(record, row) } }
      val rules = query.filter.map { rule -> rule.copy(value = filterValue(rule.value, fields[rule.sourcePath])) }
      rows = FilterEvaluator.apply(records.map { it.first }, rules).map { rowByRecord.getValue(it) }
    }

    val orderOf = { row: Row -> timeField?.let { row.values[it.path] }?.takeIf { it.isNotEmpty() } ?: row.item.createdAt.toString() }
    val result = if (aggregate == null) {
      rows = rows.sortedWith(comparator(query.sort, fields.mapValues { ResultColumn.of(it.value) }))
      query.limit?.let { rows = rows.take(it) }
      QueryResult(fields.mapValues { ResultColumn.of(it.value) }, rows.map { ResultRow(it.values, orderOf(it)) })
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

  /** A configuration number converted into [unit] (the unit of the value it is compared with). */
  private fun configNumber(raw: String, unit: PropertyUnit?): Double? {
    val field = configField(ReportFields.configPath(raw) ?: return null) ?: return null
    val value = configRaw(field)?.toDoubleOrNull() ?: return null
    return ReportFields.convertUnit(value, field.unit, unit)
  }

  /**
   * A filter rule's value, a `config.<field>` value resolved as the filtered [target] field compares it: a reference by its display text, a
   * number converted into the target's unit. An unresolvable or empty configuration value never matches (a value no field carries).
   */
  private fun filterValue(raw: String?, target: ResolvedField?): String? {
    val path = ReportFields.configPath(raw) ?: return raw
    val field = configField(path) ?: return UNMATCHABLE
    val value = configRaw(field) ?: return UNMATCHABLE
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

    val grouped = rows.mapNotNull { row ->
      val bucketKey = aggregate.bucket?.let { bucket ->
        val date = timeField?.let { toDate(it.type, row.values[it.path]) } ?: return@mapNotNull null
        encodeTimeBucket(bucket, date, aggregate.periodStart)
      }
      val groupKey = groupField?.let { row.values[it.path]?.takeIf { value -> value.isNotEmpty() } }
      Triple(bucketKey, groupKey, row)
    }.groupBy({ it.first to it.second }, { it.third })

    var resultRows = grouped.map { (key, groupRows) ->
      val values = LinkedHashMap<String, String?>()
      if (aggregate.bucket != null) values[ReportAggregate.BUCKET_FIELD] = key.first
      groupField?.let { values[it.path] = key.second }
      aggregate.values.forEach { value -> values[value.name] = aggregateValue(value, groupRows, fields, columns.getValue(value.name), orderOf) }
      ResultRow(values, key.first ?: groupRows.maxOf(orderOf))
    }
    val groupColumn = groupField?.let { columns[it.path] }
    resultRows = if (query.sort.isEmpty()) {
      resultRows.sortedWith(
        compareBy<ResultRow> { it.values[ReportAggregate.BUCKET_FIELD] ?: "" }
          .thenBy(String.CASE_INSENSITIVE_ORDER) { row -> groupColumn?.let { text(it, row.values[it.path]) } ?: "" },
      )
    } else {
      val comparator = query.sort.mapNotNull { sort -> columns[sort.field]?.let { valueComparator(it, sort.direction) } }.reduceOrNull { acc, next -> acc.then(next) }
      comparator?.let { c -> resultRows.sortedWith { a, b -> c.compare(a.values, b.values) } } ?: resultRows
    }
    query.limit?.let { resultRows = resultRows.take(it) }
    return QueryResult(columns, resultRows)
  }

  private fun aggregateValue(
    value: ReportAggregateValue,
    rows: List<Row>,
    fields: Map<String, ResolvedField>,
    column: ResultColumn,
    orderOf: (Row) -> String,
  ): String? {
    val field = value.field?.let { fields[it] }
    if (value.function == ReportAggregateFunction.COUNT) {
      return (if (field == null) rows.size else rows.count { !it.values[field.path].isNullOrEmpty() }).toString()
    }
    field ?: return null
    val contributions = rows.mapNotNull { row -> row.values[field.path]?.toDoubleOrNull()?.let { row to it } }
    if (contributions.isEmpty()) return null
    val numbers = contributions.map { it.second }
    val result = when (value.function) {
      ReportAggregateFunction.SUM -> numbers.sum()
      ReportAggregateFunction.AVG -> numbers.average()
      ReportAggregateFunction.MIN -> numbers.min()
      ReportAggregateFunction.MAX -> numbers.max()
      ReportAggregateFunction.LAST -> contributions.maxBy { orderOf(it.first) }.second
      ReportAggregateFunction.COUNT -> numbers.size.toDouble()
    }
    return rawNumber(result, column.type)
  }

  private fun rawNumber(value: Double, type: PropertyType?): String =
    if (type == PropertyType.LONG && value == Math.rint(value)) value.toLong().toString() else value.toString()

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
      val result = run(query) ?: return@mapNotNull null
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

    val first = data.firstOrNull()?.query?.aggregate
    val (xAxis, series) = when {
      data.isEmpty() -> ChartXAxisSpec(ChartXAxisType.CATEGORY) to emptyList()
      panel.type == ReportPanelType.TIMESERIES && first == null -> timeSeries(data, ::point, ::seriesSpec)
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
    val bucket = data.firstNotNullOfOrNull { it.query.aggregate?.bucket } ?: return ChartXAxisSpec(ChartXAxisType.CATEGORY) to emptyList()
    val keys = consecutiveBuckets(bucket, data.flatMap { item -> item.result.rows.mapNotNull { it.values[ReportAggregate.BUCKET_FIELD] } }.toSortedSet())
    val series = data.flatMap { item ->
      val groupColumn = item.query.aggregate?.groupBy?.let { item.result.columns[it] }
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
      val groupColumn = item.query.aggregate?.groupBy?.let { item.result.columns[it] } ?: return@forEach
      item.result.rows.forEach { row ->
        val key = row.values[groupColumn.path].orEmpty()
        labels.putIfAbsent(key, text(groupColumn, key).ifEmpty { EMPTY_GROUP })
      }
    }
    val keys = labels.keys.toList()
    val series = data.flatMap { item ->
      val groupPath = item.query.aggregate?.groupBy ?: return@flatMap emptyList()
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

  // region stat

  private fun statPanel(panel: ReportStatPanel, query: ReportQuery): PanelSpec? {
    val range = query.range?.resolve(today)
    val result = run(query, range) ?: return null
    val column = panel.field?.let { result.columns[it] }
    val unit = column?.unit?.takeIf { panel.reduce != ReportReduce.COUNT }
    val value = reduce(panel.reduce, result, column)
    val color = thresholdColor(panel, value, unit)
    val comparison = if (panel.compare) range?.previous()?.let { previousRange -> comparison(panel, query, previousRange, value, unit) } else null
    return StatPanelSpec(panel.title, value, value?.let { formatNumber(it, unit) } ?: NO_VALUE, color, comparison)
  }

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
    val previousResult = run(query, previousRange) ?: return null
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

    private const val YES = "ja"
    private const val NO = "nein"
    private const val NO_VALUE = "–"
    private const val EMPTY_GROUP = "–"
    private const val PERCENT = 100.0

    /** Filter value of an unresolvable `config.<field>` reference: control characters no stored text contains. */
    private const val UNMATCHABLE = "\u0000config\u0000"
  }
}
