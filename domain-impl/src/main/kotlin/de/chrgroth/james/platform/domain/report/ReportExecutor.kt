package de.chrgroth.james.platform.domain.report

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import de.chrgroth.james.platform.domain.imports.FilterEvaluator
import de.chrgroth.james.platform.domain.model.app.AppData
import de.chrgroth.james.platform.domain.model.app.DisplayTextResolver
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.PanelSpec
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyValueFormat
import de.chrgroth.james.platform.domain.model.app.ReportColumn
import de.chrgroth.james.platform.domain.model.app.ReportDefinition
import de.chrgroth.james.platform.domain.model.app.ReportPanel
import de.chrgroth.james.platform.domain.model.app.ReportQuery
import de.chrgroth.james.platform.domain.model.app.ReportTablePanel
import de.chrgroth.james.platform.domain.model.app.ReportTextPanel
import de.chrgroth.james.platform.domain.model.app.ReportUnitSpec
import de.chrgroth.james.platform.domain.model.app.ReportView
import de.chrgroth.james.platform.domain.model.app.ReportViewRow
import de.chrgroth.james.platform.domain.model.app.ReportViewTab
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.TableCellSpec
import de.chrgroth.james.platform.domain.model.app.TableColumnSpec
import de.chrgroth.james.platform.domain.model.app.TablePanelSpec
import de.chrgroth.james.platform.domain.model.app.TextPanelSpec
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.IdentityHashMap

/**
 * Executes a validated [ReportDefinition] against the data of exactly one installation ([allAppData] must only contain that installation's
 * objects - the sandbox boundary is drawn by the caller loading it) and builds the renderer-neutral [ReportView].
 *
 * A query selects the objects of its entity, keeps those whose time field lies in the range ([today] resolves relative ranges), applies the
 * filter rules via [FilterEvaluator] (same semantics as import filters, evaluated on a JSON view of the object: numbers as numbers, dates as
 * ISO strings, references as display text, a `ref` hop as nested object), sorts (without `sort`: newest first) and limits. Each query runs
 * at most once per report, however many panels use it.
 */
internal class ReportExecutor(
  private val entities: List<EntityDefinition>,
  private val allAppData: List<AppData>,
  private val today: LocalDate,
  private val computeValues: (EntityDefinition, Map<String, String?>) -> Map<String, String?>,
) {
  private val entityById = entities.associateBy { it.id.value }
  private val appDataById by lazy { allAppData.associateBy { it.id.value } }
  private val displayTexts = DisplayTextResolver(entityById, allAppData)
  private val computedCache = HashMap<String, Map<String, String?>>()
  private val results = HashMap<String, QueryResult>()

  /** One query's matching rows, each the raw (storage form) value per field path, plus the resolved fields. */
  class QueryResult(val fields: Map<String, ResolvedField>, val rows: List<Map<String, String?>>)

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
  }

  private fun tablePanel(panel: ReportTablePanel, query: ReportQuery): PanelSpec? {
    val result = run(query) ?: return null
    val columns = panel.columns.ifEmpty { queryFieldPaths(query).filter { it in result.fields }.map { ReportColumn(it) } }
      .mapNotNull { column -> result.fields[column.field]?.let { column to it } }
    val limited = panel.limit?.let { result.rows.take(it) } ?: result.rows
    val visible = limited.take(MAX_TABLE_ROWS)
    return TablePanelSpec(
      title = panel.title,
      columns = columns.map { (column, field) -> TableColumnSpec(field.path, column.label ?: field.path, field.type.name, unitSpec(field)) },
      rows = visible.map { row -> columns.map { (_, field) -> cell(field, row[field.path]) } },
      totalRows = result.rows.size,
      truncated = limited.size > visible.size,
    )
  }

  private fun unitSpec(field: ResolvedField): ReportUnitSpec? =
    field.unit?.let { ReportUnitSpec(it.family.name, (it.storageGranularity as Enum<*>).name, it.storageGranularity.symbol) }

  private fun cell(field: ResolvedField, raw: String?): TableCellSpec {
    if (raw.isNullOrEmpty()) return TableCellSpec(raw, "")
    val text = if (field.type == PropertyType.REF) displayTexts.displayTextOf(raw) else PropertyValueFormat.format(field.type, field.unit, raw, field.listItemType)
    return TableCellSpec(raw, text)
  }

  private fun queryFieldPaths(query: ReportQuery): List<String> {
    val entity = entities.find { it.name == query.entity } ?: return emptyList()
    return query.fields.ifEmpty { ReportFields.defaultFields(entity) }
  }

  /** Runs [query] (cached per name); null if its entity no longer resolves. */
  fun run(query: ReportQuery): QueryResult? {
    results[query.name]?.let { return it }
    val entity = entities.find { it.name == query.entity } ?: return null
    val paths = (queryFieldPaths(query) + query.filter.map { it.sourcePath } + query.sort.map { it.field } + listOfNotNull(query.timeField)).distinct()
    val fields = paths.mapNotNull { path -> (ReportFields.resolve(entity, path, entities) as? FieldResolution.Resolved)?.field?.let { path to it } }.toMap()

    val objects = allAppData.filter { it.entityType == entity.id }
    var rows = objects.map { item -> Row(item, fields.mapValues { (_, field) -> rawValue(item, field) }) }

    val timeField = query.timeField?.let { fields[it] }
    val range = query.range?.resolve(today)
    if (timeField != null && range != null) {
      rows = rows.filter { row -> row.values[timeField.path]?.let { toDate(timeField.type, it) }?.let { it in range } ?: false }
    }

    if (query.filter.isNotEmpty()) {
      val records = rows.map { row -> filterRecord(row, fields) to row }
      val rowByRecord = IdentityHashMap<JsonNode, Row>().apply { records.forEach { (record, row) -> put(record, row) } }
      rows = FilterEvaluator.apply(records.map { it.first }, query.filter).map { rowByRecord.getValue(it) }
    }

    rows = rows.sortedWith(comparator(query, fields))
    query.limit?.let { rows = rows.take(it) }
    return QueryResult(fields, rows.map { it.values }).also { results[query.name] = it }
  }

  private class Row(val item: AppData, val values: Map<String, String?>)

  private fun rawValue(item: AppData, field: ResolvedField): String? {
    val source = if (field.refProperty == null) {
      item
    } else {
      val refId = item.data[field.refProperty.id.value]?.takeIf { it.isNotEmpty() } ?: return null
      appDataById[refId] ?: return null
    }
    return if (field.isComputed) computed(source)[field.valueKey] else source.data[field.valueKey]
  }

  private fun computed(item: AppData): Map<String, String?> = computedCache.getOrPut(item.id.value) {
    entityById[item.entityType.value]?.let { computeValues(it, item.data) } ?: emptyMap()
  }

  private fun toDate(type: PropertyType, raw: String): LocalDate? = when (type) {
    PropertyType.DATE -> runCatching { LocalDate.parse(raw) }.getOrNull()
    PropertyType.DATETIME -> runCatching { LocalDateTime.parse(raw).toLocalDate() }.getOrNull()
    else -> null
  }

  /** The JSON view filter rules are evaluated on: `{field: value, ref: {field: value}}`. */
  private fun filterRecord(row: Row, fields: Map<String, ResolvedField>): ObjectNode {
    val record = JsonNodeFactory.instance.objectNode()
    // Plain fields first, so a `ref` hop (`schuhe.name`) nests its value below the reference rather than being overwritten by it.
    fields.values.sortedBy { it.refProperty != null }.forEach { field ->
      val target = if (field.refProperty == null) record else (record.get(field.refProperty.name) as? ObjectNode ?: record.putObject(field.refProperty.name))
      val key = field.path.substringAfter('.').takeIf { field.refProperty != null } ?: field.path
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

  private fun comparator(query: ReportQuery, fields: Map<String, ResolvedField>): Comparator<Row> {
    val newestFirst = compareByDescending<Row> { it.item.createdAt }.thenBy { it.item.id.value }
    if (query.sort.isEmpty()) return newestFirst
    return query.sort.mapNotNull { sort -> fields[sort.field]?.let { field -> fieldComparator(field, sort.direction) } }
      .reduceOrNull { acc, next -> acc.then(next) }
      ?.then(newestFirst) ?: newestFirst
  }

  private fun fieldComparator(field: ResolvedField, direction: SortDirection): Comparator<Row> {
    val values = Comparator<String> { a, b -> compareValues(field, a, b) }
    val directed = if (direction == SortDirection.DESC) values.reversed() else values
    // Empty values always last, whatever the direction.
    return Comparator { a, b ->
      val va = a.values[field.path]?.takeIf { it.isNotEmpty() }
      val vb = b.values[field.path]?.takeIf { it.isNotEmpty() }
      when {
        va == null && vb == null -> 0
        va == null -> 1
        vb == null -> -1
        else -> directed.compare(va, vb)
      }
    }
  }

  private fun compareValues(field: ResolvedField, a: String, b: String): Int = when (field.type) {
    PropertyType.LONG, PropertyType.DOUBLE -> {
      val da = a.toDoubleOrNull()
      val db = b.toDoubleOrNull()
      if (da != null && db != null) da.compareTo(db) else a.compareTo(b)
    }
    PropertyType.REF -> displayTexts.displayTextOf(a).compareTo(displayTexts.displayTextOf(b), ignoreCase = true)
    PropertyType.DATE, PropertyType.DATETIME, PropertyType.TIME -> a.compareTo(b)
    else -> a.compareTo(b, ignoreCase = true)
  }

  companion object {
    /** Platform cap for the rows one table panel ships to the browser, whatever the query/panel limit says. */
    const val MAX_TABLE_ROWS = 1_000
  }
}
