package de.chrgroth.james.platform.domain.model.app

import de.chrgroth.james.platform.domain.model.imports.FilterRule
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/**
 * The typed form of a [Report.definition] (YAML), see arc42 "Reports". Layout is report -> [tabs] -> rows -> panels;
 * panels display the result of named [queries], each optionally post-processed by its pipeline of [transforms] (keyed by query name).
 * [variables] are shown as controls on top of the report and referenced as `$name` in query ranges and filter values; [checks] produce the
 * findings of `warnings` panels. Later steps add further panel types next to the existing keys, so this model only ever grows.
 */
data class ReportDefinition(
  val queries: List<ReportQuery> = emptyList(),
  val tabs: List<ReportTab> = emptyList(),
  val variables: List<ReportVariable> = emptyList(),
  val transforms: Map<String, List<ReportTransform>> = emptyMap(),
  val checks: List<ReportCheck> = emptyList(),
) {
  fun query(name: String): ReportQuery? = queries.find { it.name == name }

  fun check(name: String): ReportCheck? = checks.find { it.name == name }

  fun variable(name: String): ReportVariable? = variables.find { it.name == name }

  companion object {
    val EMPTY = ReportDefinition()
  }
}

/**
 * A named query over one Entity of the installation, executed backend-side. [fields] may reach one `ref` hop (`schuhe.name`); an empty list
 * means all top-level properties and computed properties. [filter] reuses the import filter rule model, its `sourcePath` being the field.
 */
data class ReportQuery(
  val name: String,
  val entity: String,
  val timeField: String? = null,
  val range: ReportTimeRange? = null,
  val filter: List<FilterRule> = emptyList(),
  val fields: List<String> = emptyList(),
  val sort: List<ReportSort> = emptyList(),
  val limit: Int? = null,
  val aggregate: ReportAggregate? = null,
)

/**
 * Aggregates a query's (filtered, ranged) rows on the fly: one result row per time [bucket] of the query's `timeField` and/or per value of
 * [groupBy] (a property, a `ref` - labelled by the referenced display text - or a field reached through it). Unlike an
 * [AggregationDefinition], bucket and group may be combined. The result rows carry the fields [BUCKET_FIELD] (if bucketed), the [groupBy] path
 * (if grouped) and one field per [values] entry.
 */
data class ReportAggregate(
  val values: List<ReportAggregateValue>,
  val bucket: TimeBucket? = null,
  val periodStart: MonthDay? = null,
  val groupBy: String? = null,
) {
  /** Names of the result fields in their natural order: bucket, group, values. */
  val resultFields: List<String> get() = listOfNotNull(BUCKET_FIELD.takeIf { bucket != null }, groupBy) + values.map { it.name }

  companion object {
    const val BUCKET_FIELD = "bucket"
  }
}

/** One aggregated value named [name]: [function] over [field] (a numeric field, any field for COUNT; null for COUNT counts rows). */
data class ReportAggregateValue(val name: String, val function: ReportAggregateFunction, val field: String?)

/** LAST is the value of the latest row by the query's time field (by creation time without one). */
enum class ReportAggregateFunction(val key: String) {
  SUM("sum"),
  AVG("avg"),
  COUNT("count"),
  MIN("min"),
  MAX("max"),
  LAST("last"),
  ;

  companion object {
    fun byKey(key: String): ReportAggregateFunction? = entries.find { it.key.equals(key, ignoreCase = true) }
  }
}

data class ReportSort(val field: String, val direction: SortDirection = SortDirection.ASC)

/** Restricts a query to objects whose `timeField` (a DATE/DATETIME property) lies within the range, both bounds inclusive. */
sealed interface ReportTimeRange {
  fun resolve(today: LocalDate): ReportDateRange

  data class Relative(val period: RelativePeriod) : ReportTimeRange {
    override fun resolve(today: LocalDate): ReportDateRange = period.resolve(today)
  }

  data class Fixed(val from: LocalDate?, val to: LocalDate?) : ReportTimeRange {
    override fun resolve(today: LocalDate): ReportDateRange = ReportDateRange(from, to)
  }

  /**
   * `range: $name`: the range selected in the [ReportVariable] [name] (a time range variable, or a select variable over a time bucket meaning
   * that bucket's period). Only the executor knows the selection; resolved on its own it is the open range.
   */
  data class Variable(val name: String) : ReportTimeRange {
    override fun resolve(today: LocalDate): ReportDateRange = ReportDateRange(null, null)
  }
}

/** A resolved date range, bounds inclusive, a null bound meaning open. */
data class ReportDateRange(val from: LocalDate?, val to: LocalDate?) {
  operator fun contains(date: LocalDate): Boolean = (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))

  /**
   * The period of the same length right before this one, null for an open range. Whole calendar months (e.g. `this_month`, `this_year`) shift
   * by their number of months, so October precedes November rather than "the 30 days before"; any other range shifts by its number of days.
   */
  fun previous(): ReportDateRange? {
    val start = from ?: return null
    val end = to ?: return null
    if (start.dayOfMonth == 1 && end.dayOfMonth == end.lengthOfMonth()) {
      val months = ChronoUnit.MONTHS.between(start, end.plusDays(1))
      return ReportDateRange(start.minusMonths(months), start.minusDays(1))
    }
    val days = ChronoUnit.DAYS.between(start, end) + 1
    return ReportDateRange(start.minusDays(days), start.minusDays(1))
  }
}

/** Relative time ranges, resolved against "today" in the application time zone. `last_*` ranges end today. */
enum class RelativePeriod(val key: String) {
  LAST_7_DAYS("last_7_days"),
  LAST_30_DAYS("last_30_days"),
  LAST_90_DAYS("last_90_days"),
  LAST_12_MONTHS("last_12_months"),
  THIS_MONTH("this_month"),
  LAST_MONTH("last_month"),
  THIS_YEAR("this_year"),
  LAST_YEAR("last_year"),
  ;

  @Suppress("MagicNumber")
  fun resolve(today: LocalDate): ReportDateRange = when (this) {
    LAST_7_DAYS -> ReportDateRange(today.minusDays(6), today)
    LAST_30_DAYS -> ReportDateRange(today.minusDays(29), today)
    LAST_90_DAYS -> ReportDateRange(today.minusDays(89), today)
    LAST_12_MONTHS -> ReportDateRange(today.minusMonths(12).plusDays(1), today)
    THIS_MONTH -> ReportDateRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()))
    LAST_MONTH -> today.minusMonths(1).let { ReportDateRange(it.withDayOfMonth(1), it.withDayOfMonth(it.lengthOfMonth())) }
    THIS_YEAR -> ReportDateRange(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()))
    LAST_YEAR -> today.minusYears(1).let { ReportDateRange(it.withDayOfYear(1), it.withDayOfYear(it.lengthOfYear())) }
  }

  companion object {
    fun byKey(key: String): RelativePeriod? = entries.find { it.key == key }
  }
}

data class ReportTab(val name: String, val rows: List<ReportRow> = emptyList())

data class ReportRow(val panels: List<ReportPanel> = emptyList())

/**
 * Panel types by their YAML key. Only [supported] types can be used yet; the others are reserved for later steps so a definition using them
 * gets a clear "not supported yet" error instead of an unknown key error.
 */
enum class ReportPanelType(val key: String, val supported: Boolean) {
  TABLE("table", true),
  TEXT("text", true),
  STAT("stat", true),
  TIMESERIES("timeseries", true),
  BAR("bar", true),
  PIE("pie", true),
  HEATMAP("heatmap", true),
  WARNINGS("warnings", true),
  ;

  companion object {
    fun byKey(key: String): ReportPanelType? = entries.find { it.key == key }
  }
}

/** A panel; its YAML type key carries the [title], e.g. `table: Letzte Läufe`. */
sealed interface ReportPanel {
  val type: ReportPanelType
  val title: String
}

data class ReportTablePanel(
  override val title: String,
  val query: String,
  val columns: List<ReportColumn> = emptyList(),
  val limit: Int? = null,
) : ReportPanel {
  override val type: ReportPanelType get() = ReportPanelType.TABLE
}

data class ReportColumn(val field: String, val label: String? = null)

data class ReportTextPanel(
  override val title: String,
  val markdown: String,
) : ReportPanel {
  override val type: ReportPanelType get() = ReportPanelType.TEXT
}

/**
 * A chart of one or more [series], [type] being [ReportPanelType.TIMESERIES] (x = the time bucket of an aggregate query, or the time field of a
 * plain query) or [ReportPanelType.BAR] (x = the group of an aggregate query, or its bucket without a group). A grouped timeseries query (or a
 * bucketed and grouped bar query) splits each series into one per group (per bucket). [horizontal] only applies to bars.
 */
data class ReportChartPanel(
  override val type: ReportPanelType,
  override val title: String,
  val series: List<ReportSeries>,
  val stack: Boolean = false,
  val zoom: Boolean = type == ReportPanelType.TIMESERIES,
  val horizontal: Boolean = false,
) : ReportPanel

data class ReportSeries(
  val query: String,
  val field: String,
  val name: String? = null,
  val style: ReportSeriesStyle = ReportSeriesStyle.LINE,
  val axis: ReportAxis = ReportAxis.LEFT,
)

enum class ReportSeriesStyle(val key: String) {
  LINE("line"),
  COLUMN("column"),
  AREA("area"),
}

enum class ReportAxis(val key: String) {
  LEFT("left"),
  RIGHT("right"),
}

/**
 * A single value: [field] of [query] reduced over all its rows ([field] null with [ReportReduce.COUNT] counts rows), or - instead of a query -
 * the [config] field of the App configuration (ADR 0027) as a constant. [compare] adds the value of the previous period of the same length (the
 * query needs a closed range). [thresholds] color the value: the last threshold (in ascending order) whose value is at most the value wins.
 * [goal] (a number, unit text, `config.<field>` or a numeric field of the query, its latest value) shows the progress towards a target.
 */
data class ReportStatPanel(
  override val title: String,
  val query: String?,
  val field: String?,
  val reduce: ReportReduce = ReportReduce.LAST,
  val compare: Boolean = false,
  val thresholds: List<ReportThreshold> = emptyList(),
  val config: String? = null,
  val goal: String? = null,
) : ReportPanel {
  override val type: ReportPanelType get() = ReportPanelType.STAT
}

/**
 * `{ pie: Verkehrsmittel, query: wege, field: anzahl, category: verkehrsmittel, donut: true }`: the shares of [field] per [category] (default:
 * the group of a grouped aggregate, else its time bucket); rows of the same category are summed, categories without a positive sum are left
 * out. [field] defaults to the single value of an aggregate query. [donut] draws a ring instead of a full circle.
 */
data class ReportPiePanel(
  override val title: String,
  val query: String,
  val field: String,
  val category: String? = null,
  val donut: Boolean = false,
) : ReportPanel {
  override val type: ReportPanelType get() = ReportPanelType.PIE
}

/**
 * A heatmap of [field] of [query]: without [x]/[y] a **calendar** heatmap - one cell per day (the row's time field, or a `TAG` bucket) over the
 * query's range (else the days with data), the rows of a day reduced by [reduce]; with [x] and [y] a **matrix** heatmap - one cell per pair of
 * the two fields' values (e.g. bucket vs. category), reduced the same way. [thresholds] (like stat thresholds) color the cells in steps;
 * without them a continuous color scale from the smallest to the largest value is used. [field] null with [ReportReduce.COUNT] counts rows.
 */
data class ReportHeatmapPanel(
  override val title: String,
  val query: String,
  val field: String?,
  val reduce: ReportReduce = ReportReduce.SUM,
  val x: String? = null,
  val y: String? = null,
  val thresholds: List<ReportThreshold> = emptyList(),
) : ReportPanel {
  override val type: ReportPanelType get() = ReportPanelType.HEATMAP

  val calendar: Boolean get() = x == null
}

enum class ReportReduce(val key: String) {
  SUM("sum"),
  AVG("avg"),
  MIN("min"),
  MAX("max"),
  LAST("last"),
  COUNT("count"),
}

/**
 * [value] as written, a plain number in the field's storage unit, a unit text (`1000km`) or a configuration field (`config.zielKm`, ADR 0027);
 * [color] a color name or `#rrggbb`.
 */
data class ReportThreshold(val value: String, val color: String)

// region validation errors

/** What is wrong with a definition; [ReportDefinitionError.args] carries the details the message is built from (see each entry). */
enum class ReportDefinitionErrorCode {
  /** args: parser detail. */
  YAML_SYNTAX,

  /** args: key. */
  DUPLICATE_KEY,

  /** args: expected kind (map, list, text, number). */
  WRONG_TYPE,

  /** args: key, allowed keys (comma separated). */
  UNKNOWN_KEY,

  /** args: key or panel type. */
  NOT_SUPPORTED_YET,

  /** args: key. */
  REQUIRED_KEY_MISSING,

  /** args: value, allowed values (comma separated). */
  INVALID_VALUE,

  /** args: entity. */
  ENTITY_NOT_FOUND,

  /** args: field, entity. */
  FIELD_NOT_FOUND,

  /** args: field. More than one `ref` hop, or the hop is not a `ref` property. */
  REF_HOP_INVALID,

  /** args: field. The time field is no DATE/DATETIME property. */
  TIME_FIELD_INVALID,

  /** args: the key needing it (range, bucket). */
  TIME_FIELD_MISSING,

  /** args: query. */
  QUERY_NOT_FOUND,

  /** args: field, query. */
  COLUMN_NOT_IN_QUERY,

  /** args: allowed panel types (comma separated). */
  PANEL_TYPE_MISSING,

  /** args: the panel types found (comma separated). */
  PANEL_TYPE_AMBIGUOUS,

  /** args: operator. */
  FILTER_VALUE_MISSING,

  /** args: pattern. */
  INVALID_REGEX,

  /** args: value. */
  INVALID_DATE,

  /** args: none. `from` after `to`. */
  INVALID_RANGE,

  /** args: value, minimum, maximum. */
  NUMBER_OUT_OF_RANGE,

  /** args: key, conflicting key. Two keys that cannot be combined. */
  KEY_CONFLICT,

  /** args: value. Expected `MM-DD` or `DD.MM.`, not 29.02. */
  PERIOD_START_INVALID,

  /** args: none. A period start only applies to the buckets JAHR and QUARTAL. */
  PERIOD_START_NOT_APPLICABLE,

  /** args: field. The field is no LONG/DOUBLE (computed) property. */
  FIELD_NOT_NUMERIC,

  /** args: name. A result field name used twice (aggregate value vs. bucket/group). */
  DUPLICATE_RESULT_FIELD,

  /** args: query. The query offers no x axis for the chart (timeseries: bucket or time field; bar: group or bucket). */
  CHART_X_MISSING,

  /** args: query. The series' queries use different x axes (e.g. different buckets). */
  SERIES_X_MISMATCH,

  /** args: field. A series' unit does not fit the unit of the other series on its axis. */
  AXIS_UNIT_MISMATCH,

  /** args: query. Comparing to the previous period needs a closed time range on the query. */
  COMPARE_NEEDS_RANGE,

  /** args: value. */
  INVALID_NUMBER,

  /** args: value, allowed color names (comma separated). */
  INVALID_COLOR,

  /** args: the `config.<field>` reference. The App Version defines no configuration Entity (ADR 0027). */
  CONFIG_NOT_DEFINED,

  /** args: field, configuration entity. The configuration Entity has no such (computed) property. */
  CONFIG_FIELD_NOT_FOUND,

  /** args: field. The configuration field's unit does not fit the unit of the value it is compared with. */
  CONFIG_UNIT_MISMATCH,

  /** args: the `$name` reference. No variable of that name is declared. */
  VARIABLE_NOT_FOUND,

  /** args: variable, the expected use (`range`, `filter`). The variable cannot be used here (e.g. a select over no time bucket as range). */
  VARIABLE_TYPE_MISMATCH,

  /** args: variable, query. The query feeding a select variable's options must not use variables itself. */
  VARIABLE_OPTIONS_QUERY_INVALID,

  /** args: name. A variable name must start with a letter and consist of letters, digits and `_`. */
  VARIABLE_NAME_INVALID,

  /** args: allowed transformation steps (comma separated). */
  TRANSFORM_STEP_MISSING,

  /** args: the steps found (comma separated). */
  TRANSFORM_STEP_AMBIGUOUS,

  /** args: expression, 1-based position, detail (machine-oriented, e.g. `unexpected ')'`). */
  EXPRESSION_INVALID,

  /** args: function, allowed functions (comma separated). */
  EXPRESSION_UNKNOWN_FUNCTION,

  /**
   * args: operator, type (an expression type: NUMBER, TEXT, BOOLEAN, DATE, DATETIME, TIME). Arithmetic (`+ - * / % ^`, unary `-`) needs numbers or
   * yes/no values.
   */
  EXPRESSION_OPERAND_TYPE,

  /** args: function, 1-based argument, type (an expression type). The function cannot take a value of that type there, e.g. `minutes` a number. */
  EXPRESSION_ARGUMENT_TYPE,

  /** args: operator (or `case`), left type, right type (expression types). The values cannot be compared, e.g. a text with a number. */
  EXPRESSION_INCOMPARABLE,

  /** args: function (`if`, `case`, `coalesce`), types (comma separated expression types). The alternative results have different types. */
  EXPRESSION_MIXED_TYPES,

  /**
   * args: expected type, actual type (field or expression types). The expression's result does not fit where it is used: a `calc` field's `type`
   * or `unit`, or a value rule needing a number.
   */
  EXPRESSION_RESULT_TYPE,

  /** args: step, query. The step needs a time bucket (an aggregate with `bucket` or a preceding `resample`). */
  TRANSFORM_NEEDS_BUCKET,

  /** args: query. A forecast needs a time bucket or a closed time range on the query. */
  FORECAST_NEEDS_PERIOD,

  /** args: query. Resampling needs the query's time field or a time bucket. */
  RESAMPLE_NEEDS_TIME,

  /** args: step, query. The step needs a date per row: the query's time field or a time bucket. */
  TRANSFORM_NEEDS_TIME,

  /** args: query. The queries read by `asOf`/`join` steps refer to each other (or a query to itself). */
  TRANSFORM_QUERY_CYCLE,

  /** args: value. A goal target is no number, unit text, `config.<field>` or numeric result field. */
  GOAL_TARGET_INVALID,

  /** args: allowed check types (comma separated). */
  CHECK_TYPE_MISSING,

  /** args: the check types found (comma separated). */
  CHECK_TYPE_AMBIGUOUS,

  /** args: check. A warnings panel lists a check that is not declared. */
  CHECK_NOT_FOUND,

  /** args: the calendar value. A `config.<field>` calendar is no DATE/DATETIME field or LIST of dates. */
  CALENDAR_INVALID,

  /** args: placeholder, allowed placeholders (comma separated). */
  MESSAGE_PLACEHOLDER_UNKNOWN,

  /** args: query. A calendar heatmap needs a day per row: the query's time field or a `TAG` bucket. */
  HEATMAP_NEEDS_DAY,

  /** args: query. A pie chart needs a category: `category`, or the group or time bucket of an aggregate query. */
  PIE_CATEGORY_MISSING,

  /** No args. The `kotlin` step was removed (ADR 0029); calculations use `calc` and the other declarative steps. */
  KOTLIN_STEP_REMOVED,
}

/** One validation error, located by [path] (e.g. `queries.laeufe.filter[0].op`) and, where known, the 1-based [line] in the YAML source. */
data class ReportDefinitionError(
  val code: ReportDefinitionErrorCode,
  val path: String,
  val line: Int? = null,
  val args: List<String> = emptyList(),
)

// endregion

// region renderer-neutral panel spec

/**
 * The executed report as renderer-neutral panel spec: the browser only renders it, it never sees raw data or definitions it has to evaluate.
 * Values are already formatted like display texts (dates `dd.MM.yyyy`, units via [formatUnitValue], references via their display text).
 */
data class ReportView(
  val reportId: String,
  val name: String,
  val tabs: List<ReportViewTab>,
  val variables: List<ReportVariableSpec> = emptyList(),
)

/**
 * A variable's control: [type] `timeRange` or `select`, its [options] (value and display label, an empty value meaning "all") and the
 * [selected] value the report was executed with. The renderer re-requests the report with `var.<name>=<value>` when the selection changes.
 */
data class ReportVariableSpec(val name: String, val label: String, val type: String, val options: List<ReportVariableOptionSpec>, val selected: String)

data class ReportVariableOptionSpec(val value: String, val label: String)

/** [warnings] is the number of findings shown in the tab's warnings panels (each check counted once), shown as counter on the tab. */
data class ReportViewTab(val name: String, val rows: List<ReportViewRow>, val warnings: Int = 0)

data class ReportViewRow(val panels: List<PanelSpec>)

sealed interface PanelSpec {
  val type: ReportPanelType
  val title: String
}

data class TablePanelSpec(
  override val title: String,
  val columns: List<TableColumnSpec>,
  val rows: List<List<TableCellSpec>>,
  /** Matching rows before the panel's limit and the platform's row cap. */
  val totalRows: Int,
  /** True when rows were cut off by the platform's row cap (not by an explicit limit). */
  val truncated: Boolean,
) : PanelSpec {
  override val type: ReportPanelType get() = ReportPanelType.TABLE
}

/** [type] is the [PropertyType] name; [unit] is set for LONG/DOUBLE properties with a unit, so renderers can align or chart values. */
data class TableColumnSpec(
  val key: String,
  val label: String,
  val type: String,
  val unit: ReportUnitSpec? = null,
)

/** [granularity] is the storage granularity name (e.g. `METERS`) raw values are expressed in, [symbol] its unit symbol. */
data class ReportUnitSpec(val family: String, val granularity: String, val symbol: String)

/** [value] is the raw (storage form) value, e.g. for sorting or charts; [text] the formatted value to display. */
data class TableCellSpec(val value: String?, val text: String)

/**
 * A chart ([type] TIMESERIES or BAR). With a [ChartXAxisType.CATEGORY] x axis every series has one point per category (in order, `y` null for a
 * gap); with a [ChartXAxisType.TIME] x axis points carry an ISO date/date-time `x`. Values are plotted in the display unit of their axis.
 */
data class ChartPanelSpec(
  override val type: ReportPanelType,
  override val title: String,
  val xAxis: ChartXAxisSpec,
  val yAxes: List<ChartYAxisSpec>,
  val series: List<ChartSeriesSpec>,
  val stack: Boolean,
  val zoom: Boolean,
  val horizontal: Boolean,
) : PanelSpec

enum class ChartXAxisType { CATEGORY, TIME }

/** [categories] are the labels of a CATEGORY axis (e.g. `09/2026`, a band name). */
data class ChartXAxisSpec(val type: ChartXAxisType, val categories: List<String> = emptyList())

/** [position] `left`/`right`; [unit] the symbol values on this axis are expressed in (e.g. `km`), null without unit. */
data class ChartYAxisSpec(val position: String, val unit: String?)

/** [style] `line`/`column`/`area`; [yAxis] index into [ChartPanelSpec.yAxes]. */
data class ChartSeriesSpec(val name: String, val style: String, val yAxis: Int, val points: List<ChartPointSpec>)

/** [x] the category key or ISO date; [y] the value in the axis' display unit (null: gap); [text] the formatted value for tooltips. */
data class ChartPointSpec(val x: String, val y: Double?, val text: String)

/** A single value; [value] raw (storage form), [text] formatted; [color] of the matching threshold (name or `#rrggbb`). */
data class StatPanelSpec(
  override val title: String,
  val value: Double?,
  val text: String,
  val color: String? = null,
  val comparison: StatComparisonSpec? = null,
  val goal: StatGoalSpec? = null,
) : PanelSpec {
  override val type: ReportPanelType get() = ReportPanelType.STAT
}

/**
 * The previous period's value and the change towards it: [deltaText] signed and formatted (`+12km 500m`), [percentText] signed (`+5,3 %`, null
 * when the previous value is 0 or missing), [trend] `up`/`down`/`flat`, [periodText] the previous period (`01.10.2026 – 31.10.2026`).
 */
data class StatComparisonSpec(
  val previousValue: Double?,
  val previousText: String,
  val deltaText: String,
  val percentText: String?,
  val trend: String,
  val periodText: String,
)

/** A stat's target: [value] raw, [text] formatted, [percent] the stat value in percent of it (null without value or for a target of 0). */
data class StatGoalSpec(val value: Double?, val text: String, val percent: Double?, val percentText: String?)

data class TextPanelSpec(
  override val title: String,
  val markdown: String,
) : PanelSpec {
  override val type: ReportPanelType get() = ReportPanelType.TEXT
}

/** A pie chart: [slices] ordered by value (largest first), values in the display unit [unit] (symbol, null without unit); [donut] draws a ring. */
data class PiePanelSpec(
  override val title: String,
  val slices: List<PieSliceSpec>,
  val unit: String?,
  val donut: Boolean,
) : PanelSpec {
  override val type: ReportPanelType get() = ReportPanelType.PIE
}

/** One category: [value] in the panel's display unit, [text] formatted, [percentText] its share (`42,5 %`). */
data class PieSliceSpec(val label: String, val value: Double, val text: String, val percentText: String)

/**
 * A heatmap. [calendar]: cells are days (`x` an ISO date) between [from] and [to] (ISO dates); matrix: cells are pairs of [xCategories] and
 * [yCategories] labels. Values are in the display unit [unit]; [min]/[max] span the values (null without any), [thresholds] (ascending, empty
 * for a continuous scale) color the cells in steps: the highest threshold not above a value wins, values below the first stay uncolored.
 */
data class HeatmapPanelSpec(
  override val title: String,
  val calendar: Boolean,
  val cells: List<HeatmapCellSpec>,
  val unit: String?,
  val min: Double?,
  val max: Double?,
  val thresholds: List<HeatmapThresholdSpec> = emptyList(),
  val from: String? = null,
  val to: String? = null,
  val xCategories: List<String> = emptyList(),
  val yCategories: List<String> = emptyList(),
) : PanelSpec {
  override val type: ReportPanelType get() = ReportPanelType.HEATMAP
}

/** One cell: [x] the ISO date (calendar) or x category label, [y] the y category label (matrix only), [value] displayed, [text] formatted. */
data class HeatmapCellSpec(val x: String, val y: String?, val value: Double, val text: String)

/** A heatmap color step: [value] in the panel's display unit, [color] a color name or `#rrggbb`. */
data class HeatmapThresholdSpec(val value: Double, val color: String)

/**
 * A panel of [type] that could not be built because its data failed at runtime (e.g. a `kotlin` transformation step failed or timed out):
 * [message] says why (German); the rest of the report renders normally.
 */
data class ErrorPanelSpec(
  override val type: ReportPanelType,
  override val title: String,
  val message: String,
) : PanelSpec

// endregion
