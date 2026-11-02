package de.chrgroth.james.platform.domain.model.app

import de.chrgroth.james.platform.domain.model.imports.FilterRule
import java.time.LocalDate

/**
 * The typed form of a [Report.definition] (YAML), see docs/plans/reports.md and arc42 "Reports". Layout is report -> [tabs] -> rows -> panels;
 * panels display the result of named [queries]. Later steps add variables, transforms, checks, aggregations and further panel types next to
 * the existing keys, so this model only ever grows.
 */
data class ReportDefinition(
  val queries: List<ReportQuery> = emptyList(),
  val tabs: List<ReportTab> = emptyList(),
) {
  fun query(name: String): ReportQuery? = queries.find { it.name == name }

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
)

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
}

/** A resolved date range, bounds inclusive, a null bound meaning open. */
data class ReportDateRange(val from: LocalDate?, val to: LocalDate?) {
  operator fun contains(date: LocalDate): Boolean = (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to))
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
  STAT("stat", false),
  TIMESERIES("timeseries", false),
  BAR("bar", false),
  PIE("pie", false),
  HEATMAP("heatmap", false),
  WARNINGS("warnings", false),
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

  /** args: none. A range needs a time field. */
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
)

data class ReportViewTab(val name: String, val rows: List<ReportViewRow>)

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

data class TextPanelSpec(
  override val title: String,
  val markdown: String,
) : PanelSpec {
  override val type: ReportPanelType get() = ReportPanelType.TEXT
}

// endregion
