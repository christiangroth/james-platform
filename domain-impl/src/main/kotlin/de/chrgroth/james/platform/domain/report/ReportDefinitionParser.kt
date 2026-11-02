package de.chrgroth.james.platform.domain.report

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.RelativePeriod
import de.chrgroth.james.platform.domain.model.app.ReportColumn
import de.chrgroth.james.platform.domain.model.app.ReportDefinition
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionError
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.ReportPanel
import de.chrgroth.james.platform.domain.model.app.ReportPanelType
import de.chrgroth.james.platform.domain.model.app.ReportQuery
import de.chrgroth.james.platform.domain.model.app.ReportRow
import de.chrgroth.james.platform.domain.model.app.ReportSort
import de.chrgroth.james.platform.domain.model.app.ReportTab
import de.chrgroth.james.platform.domain.model.app.ReportTablePanel
import de.chrgroth.james.platform.domain.model.app.ReportTextPanel
import de.chrgroth.james.platform.domain.model.app.ReportTimeRange
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import java.time.LocalDate

/**
 * Parses a report definition (YAML) into a [ReportDefinition] and validates it in the same pass: YAML syntax, the schema (unknown keys are
 * errors, never silently ignored; keys reserved for later steps answer "not supported yet"), and - when [parse] gets the version's entities -
 * the references: entities and fields exist, a time field is a DATE/DATETIME property, panels only use declared queries and their fields.
 * Every error carries its path and, where the YAML source locates it, its line.
 */
internal object ReportDefinitionParser {

  private val mapper: YAMLMapper = YAMLMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()

  const val MAX_LIMIT = 10_000

  private val ROOT_KEYS = setOf("queries", "tabs")
  private val ROOT_RESERVED = setOf("variables", "transforms", "checks")
  private val QUERY_KEYS = setOf("entity", "timeField", "range", "filter", "fields", "sort", "limit")
  private val QUERY_RESERVED = setOf("aggregate")
  private val RANGE_KEYS = setOf("from", "to")
  private val FILTER_KEYS = setOf("field", "op", "value", "mode", "includeAbsent")
  private val SORT_KEYS = setOf("field", "direction")
  private val TAB_KEYS = setOf("name", "rows")
  private val ROW_KEYS = setOf("panels")
  private val TABLE_KEYS = setOf("query", "columns", "limit")
  private val TEXT_KEYS = setOf("markdown")
  private val COLUMN_KEYS = setOf("field", "label")
  private val VALUELESS_OPERATORS = setOf(FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL)
  private val REGEX_OPERATORS = setOf(FilterOperator.MATCHES, FilterOperator.NOT_MATCHES)
  private const val DUPLICATE_FIELD_PREFIX = "Duplicate field '"

  data class Result(val definition: ReportDefinition?, val errors: List<ReportDefinitionError>)

  /** Parses and validates [text]; with [entities] null only syntax and schema are checked. A blank text is the empty report. */
  fun parse(text: String, entities: List<EntityDefinition>?): Result {
    if (text.isBlank()) return Result(ReportDefinition.EMPTY, emptyList())
    val root = try {
      mapper.readTree(text)
    } catch (e: JsonProcessingException) {
      return Result(null, listOf(syntaxError(e)))
    }
    if (root == null || root.isNull || root.isMissingNode) return Result(ReportDefinition.EMPTY, emptyList())
    val reader = Reader(lineIndex(text), entities)
    val definition = reader.definition(root)
    return if (reader.errors.isEmpty()) Result(definition, emptyList()) else Result(null, reader.errors.toList())
  }

  private fun syntaxError(e: JsonProcessingException): ReportDefinitionError {
    val line = e.location?.lineNr?.takeIf { it > 0 }
    val detail = e.originalMessage.orEmpty().lineSequence().first().trim()
    return if (detail.startsWith(DUPLICATE_FIELD_PREFIX)) {
      ReportDefinitionError(ReportDefinitionErrorCode.DUPLICATE_KEY, "", line, listOf(detail.removePrefix(DUPLICATE_FIELD_PREFIX).substringBefore('\'')))
    } else {
      ReportDefinitionError(ReportDefinitionErrorCode.YAML_SYNTAX, "", line, listOf(detail))
    }
  }

  /** Maps JSON pointers of the document (`/queries/laeufe/entity`) to the 1-based line they start on. */
  private fun lineIndex(text: String): Map<String, Int> {
    val lines = HashMap<String, Int>()
    runCatching {
      mapper.factory.createParser(text).use { parser ->
        while (parser.nextToken() != null) {
          lines.putIfAbsent(parser.parsingContext.pathAsPointer().toString(), parser.currentTokenLocation().lineNr)
        }
      }
    }
    return lines
  }

  private class Path(val segments: List<Any>) {
    fun key(key: String) = Path(segments + key)
    fun index(index: Int) = Path(segments + index)

    fun display(): String = buildString {
      segments.forEach { segment ->
        if (segment is Int) {
          append('[').append(segment).append(']')
        } else {
          if (isNotEmpty()) append('.')
          append(segment)
        }
      }
    }

    fun pointers(): Sequence<String> = generateSequence(segments) { if (it.isEmpty()) null else it.dropLast(1) }
      .map { list -> list.joinToString("") { "/" + it.toString().replace("~", "~0").replace("/", "~1") } }

    companion object {
      val ROOT = Path(emptyList())
    }
  }

  @Suppress("TooManyFunctions")
  private class Reader(private val lines: Map<String, Int>, private val entities: List<EntityDefinition>?) {
    val errors = mutableListOf<ReportDefinitionError>()

    /** Fields each query offers to panels (null for a query whose entity could not be resolved, so panels do not report follow-up errors). */
    private val queryFields = mutableMapOf<String, Set<String>?>()

    fun error(code: ReportDefinitionErrorCode, path: Path, vararg args: String) {
      val line = path.pointers().firstNotNullOfOrNull { lines[it] }
      errors += ReportDefinitionError(code, path.display(), line, args.toList())
    }

    // region generic node access

    fun obj(node: JsonNode, path: Path): ObjectNode? = (node as? ObjectNode) ?: null.also { error(ReportDefinitionErrorCode.WRONG_TYPE, path, "map") }

    fun list(node: JsonNode, path: Path): ArrayNode? = (node as? ArrayNode) ?: null.also { error(ReportDefinitionErrorCode.WRONG_TYPE, path, "list") }

    fun text(node: JsonNode, path: Path): String? = when {
      node.isNull -> ""
      node.isValueNode -> node.asText()
      else -> null.also { error(ReportDefinitionErrorCode.WRONG_TYPE, path, "text") }
    }

    fun nonBlankText(node: JsonNode, path: Path, key: String): String? {
      val value = text(node, path) ?: return null
      return value.trim().takeIf { it.isNotEmpty() } ?: null.also { error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, key) }
    }

    fun int(node: JsonNode, path: Path, min: Int, max: Int): Int? {
      if (!node.isIntegralNumber) return null.also { error(ReportDefinitionErrorCode.WRONG_TYPE, path, "number") }
      val value = node.asLong()
      if (value < min || value > max) return null.also { error(ReportDefinitionErrorCode.NUMBER_OUT_OF_RANGE, path, value.toString(), min.toString(), max.toString()) }
      return value.toInt()
    }

    fun required(node: ObjectNode, key: String, path: Path): JsonNode? =
      node.get(key) ?: null.also { error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, key) }

    fun checkKeys(node: ObjectNode, path: Path, allowed: Set<String>, reserved: Set<String> = emptySet()) {
      node.fieldNames().forEach { key ->
        when (key) {
          in allowed -> Unit
          in reserved -> error(ReportDefinitionErrorCode.NOT_SUPPORTED_YET, path.key(key), key)
          else -> error(ReportDefinitionErrorCode.UNKNOWN_KEY, path.key(key), key, allowed.joinToString(", "))
        }
      }
    }

    inline fun <reified T : Enum<T>> enumValue(node: JsonNode, path: Path): T? {
      val raw = text(node, path) ?: return null
      return enumValues<T>().find { it.name.equals(raw.trim(), ignoreCase = true) }
        ?: null.also { error(ReportDefinitionErrorCode.INVALID_VALUE, path, raw, enumValues<T>().joinToString(", ") { it.name }) }
    }

    // endregion

    fun definition(root: JsonNode): ReportDefinition? {
      val node = obj(root, Path.ROOT) ?: return null
      checkKeys(node, Path.ROOT, ROOT_KEYS, ROOT_RESERVED)
      val queries = node.get("queries")?.let { queries(it, Path.ROOT.key("queries")) } ?: emptyList()
      val tabs = required(node, "tabs", Path.ROOT)?.let { tabs(it, Path.ROOT.key("tabs")) } ?: emptyList()
      return ReportDefinition(queries, tabs)
    }

    // region queries

    private fun queries(node: JsonNode, path: Path): List<ReportQuery> {
      if (node.isNull) return emptyList()
      val map = obj(node, path) ?: return emptyList()
      return map.properties().mapNotNull { (name, queryNode) -> query(name, queryNode, path.key(name)) }
    }

    private fun query(name: String, node: JsonNode, path: Path): ReportQuery? {
      queryFields[name] = null
      val map = obj(node, path) ?: return null
      checkKeys(map, path, QUERY_KEYS, QUERY_RESERVED)
      val entityName = required(map, "entity", path)?.let { nonBlankText(it, path.key("entity"), "entity") }
      val entity = entityName?.let { resolveEntity(it, path.key("entity")) }
      val timeField = map.get("timeField")?.let { nonBlankText(it, path.key("timeField"), "timeField") }
      val range = map.get("range")?.let { range(it, path.key("range")) }
      if (range != null && map.get("timeField") == null) error(ReportDefinitionErrorCode.TIME_FIELD_MISSING, path.key("range"))
      val filter = map.get("filter")?.let { node -> list(node, path.key("filter"))?.mapIndexedNotNull { i, rule -> filterRule(rule, path.key("filter").index(i)) } }.orEmpty()
      val fields = map.get("fields")?.let { node -> list(node, path.key("fields"))?.mapIndexedNotNull { i, f -> nonBlankText(f, path.key("fields").index(i), "field") } }.orEmpty()
      val sort = map.get("sort")?.let { node -> list(node, path.key("sort"))?.mapIndexedNotNull { i, s -> sort(s, path.key("sort").index(i)) } }.orEmpty()
      val limit = map.get("limit")?.let { int(it, path.key("limit"), 1, MAX_LIMIT) }

      if (entity != null) {
        timeField?.let { checkTimeField(entity, it, path.key("timeField")) }
        fields.forEachIndexed { i, field -> checkField(entity, field, path.key("fields").index(i)) }
        filter.forEachIndexed { i, rule -> checkField(entity, rule.sourcePath, path.key("filter").index(i).key("field")) }
        sort.forEachIndexed { i, s -> checkField(entity, s.field, path.key("sort").index(i).key("field")) }
        queryFields[name] = fields.ifEmpty { ReportFields.defaultFields(entity) }.toSet()
      } else if (entities == null) {
        // Schema-only check: panels can still be checked against the declared field list.
        queryFields[name] = fields.toSet().takeIf { it.isNotEmpty() }
      }
      return ReportQuery(name, entityName.orEmpty(), timeField, range, filter, fields, sort, limit)
    }

    private fun resolveEntity(name: String, path: Path): EntityDefinition? {
      val all = entities ?: return null
      return all.find { it.name == name } ?: null.also { error(ReportDefinitionErrorCode.ENTITY_NOT_FOUND, path, name) }
    }

    private fun checkField(entity: EntityDefinition, field: String, path: Path): ResolvedField? =
      when (val resolution = ReportFields.resolve(entity, field, entities.orEmpty())) {
        is FieldResolution.Resolved -> resolution.field
        is FieldResolution.Failed -> null.also { error(resolution.code, path, *resolution.args.toTypedArray()) }
      }

    private fun checkTimeField(entity: EntityDefinition, field: String, path: Path) {
      val resolved = checkField(entity, field, path) ?: return
      if (!ReportFields.isTimeType(resolved.type)) error(ReportDefinitionErrorCode.TIME_FIELD_INVALID, path, field)
    }

    private fun range(node: JsonNode, path: Path): ReportTimeRange? {
      if (node.isValueNode) {
        val key = text(node, path)?.trim() ?: return null
        if (key.startsWith("$")) return null.also { error(ReportDefinitionErrorCode.NOT_SUPPORTED_YET, path, "variables") }
        return RelativePeriod.byKey(key)?.let { ReportTimeRange.Relative(it) }
          ?: null.also { error(ReportDefinitionErrorCode.INVALID_VALUE, path, key, RelativePeriod.entries.joinToString(", ") { it.key }) }
      }
      val map = obj(node, path) ?: return null
      checkKeys(map, path, RANGE_KEYS)
      if (map.get("from") == null && map.get("to") == null) return null.also { error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "from/to") }
      val from = map.get("from")?.let { date(it, path.key("from")) }
      val to = map.get("to")?.let { date(it, path.key("to")) }
      if (from != null && to != null && from.isAfter(to)) error(ReportDefinitionErrorCode.INVALID_RANGE, path)
      return ReportTimeRange.Fixed(from, to)
    }

    private fun date(node: JsonNode, path: Path): LocalDate? {
      val raw = text(node, path)?.trim() ?: return null
      return runCatching { LocalDate.parse(raw) }.getOrNull() ?: null.also { error(ReportDefinitionErrorCode.INVALID_DATE, path, raw) }
    }

    private fun filterRule(node: JsonNode, path: Path): FilterRule? {
      val map = obj(node, path) ?: return null
      checkKeys(map, path, FILTER_KEYS)
      val field = required(map, "field", path)?.let { nonBlankText(it, path.key("field"), "field") }
      val operator = required(map, "op", path)?.let { enumValue<FilterOperator>(it, path.key("op")) }
      val mode = map.get("mode")?.let { enumValue<FilterMode>(it, path.key("mode")) } ?: FilterMode.INCLUDE
      val includeAbsent = map.get("includeAbsent")?.let { node ->
        if (node.isBoolean) node.asBoolean() else null.also { error(ReportDefinitionErrorCode.WRONG_TYPE, path.key("includeAbsent"), "true/false") }
      } ?: false
      val value = map.get("value")?.takeIf { !it.isNull }?.let { text(it, path.key("value")) }
      if (operator != null && operator !in VALUELESS_OPERATORS && value == null) error(ReportDefinitionErrorCode.FILTER_VALUE_MISSING, path, operator.name)
      if (operator in REGEX_OPERATORS && value != null && runCatching { Regex(value) }.isFailure) error(ReportDefinitionErrorCode.INVALID_REGEX, path.key("value"), value)
      if (field == null || operator == null) return null
      return FilterRule(mode, field, operator, value, includeAbsent)
    }

    private fun sort(node: JsonNode, path: Path): ReportSort? {
      if (node.isValueNode) return nonBlankText(node, path, "field")?.let { ReportSort(it) }
      val map = obj(node, path) ?: return null
      checkKeys(map, path, SORT_KEYS)
      val field = required(map, "field", path)?.let { nonBlankText(it, path.key("field"), "field") }
      val direction = map.get("direction")?.let { enumValue<SortDirection>(it, path.key("direction")) } ?: SortDirection.ASC
      return field?.let { ReportSort(it, direction) }
    }

    // endregion

    // region layout

    private fun tabs(node: JsonNode, path: Path): List<ReportTab> =
      list(node, path)?.mapIndexedNotNull { i, tab -> tab(tab, path.index(i)) }.orEmpty()

    private fun tab(node: JsonNode, path: Path): ReportTab? {
      val map = obj(node, path) ?: return null
      checkKeys(map, path, TAB_KEYS)
      val name = required(map, "name", path)?.let { nonBlankText(it, path.key("name"), "name") }
      val rows = map.get("rows")?.takeIf { !it.isNull }?.let { rows -> list(rows, path.key("rows"))?.mapIndexedNotNull { i, row -> row(row, path.key("rows").index(i)) } }.orEmpty()
      return name?.let { ReportTab(it, rows) }
    }

    /** A row is a list of panels, or (to leave room for row options later) a map with `panels`. */
    private fun row(node: JsonNode, path: Path): ReportRow? {
      val (panels, panelsPath) = if (node is ObjectNode) {
        checkKeys(node, path, ROW_KEYS)
        (required(node, "panels", path) ?: return null) to path.key("panels")
      } else {
        node to path
      }
      val list = list(panels, panelsPath) ?: return null
      return ReportRow(list.mapIndexedNotNull { i, panel -> panel(panel, panelsPath.index(i)) })
    }

    private fun panel(node: JsonNode, path: Path): ReportPanel? {
      val map = obj(node, path) ?: return null
      val types = map.fieldNames().asSequence().mapNotNull { ReportPanelType.byKey(it) }.toList()
      when {
        types.isEmpty() -> return null.also {
          error(ReportDefinitionErrorCode.PANEL_TYPE_MISSING, path, ReportPanelType.entries.filter { it.supported }.joinToString(", ") { it.key })
        }
        types.size > 1 -> return null.also { error(ReportDefinitionErrorCode.PANEL_TYPE_AMBIGUOUS, path, types.joinToString(", ") { it.key }) }
      }
      val type = types.single()
      if (!type.supported) return null.also { error(ReportDefinitionErrorCode.NOT_SUPPORTED_YET, path.key(type.key), type.key) }
      val title = text(map.get(type.key), path.key(type.key))?.trim() ?: return null
      return when (type) {
        ReportPanelType.TABLE -> tablePanel(map, path, title)
        ReportPanelType.TEXT -> textPanel(map, path, title)
        else -> null
      }
    }

    private fun tablePanel(map: ObjectNode, path: Path, title: String): ReportPanel? {
      checkKeys(map, path, TABLE_KEYS + ReportPanelType.TABLE.key)
      val query = required(map, "query", path)?.let { nonBlankText(it, path.key("query"), "query") }
      val columns = map.get("columns")?.let { node -> list(node, path.key("columns"))?.mapIndexedNotNull { i, c -> column(c, path.key("columns").index(i)) } }.orEmpty()
      val limit = map.get("limit")?.let { int(it, path.key("limit"), 1, MAX_LIMIT) }
      if (query != null) {
        if (!queryFields.containsKey(query)) {
          error(ReportDefinitionErrorCode.QUERY_NOT_FOUND, path.key("query"), query)
        } else {
          queryFields[query]?.let { available ->
            columns.forEachIndexed { i, column ->
              if (column.field !in available) error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, path.key("columns").index(i), column.field, query)
            }
          }
        }
      }
      return query?.let { ReportTablePanel(title, it, columns, limit) }
    }

    private fun column(node: JsonNode, path: Path): ReportColumn? {
      if (node.isValueNode) return nonBlankText(node, path, "field")?.let { ReportColumn(it) }
      val map = obj(node, path) ?: return null
      checkKeys(map, path, COLUMN_KEYS)
      val field = required(map, "field", path)?.let { nonBlankText(it, path.key("field"), "field") }
      val label = map.get("label")?.let { text(it, path.key("label")) }?.trim()?.takeIf { it.isNotEmpty() }
      return field?.let { ReportColumn(it, label) }
    }

    private fun textPanel(map: ObjectNode, path: Path, title: String): ReportPanel? {
      checkKeys(map, path, TEXT_KEYS + ReportPanelType.TEXT.key)
      val markdown = required(map, "markdown", path)?.let { text(it, path.key("markdown")) } ?: return null
      return ReportTextPanel(title, markdown)
    }

    // endregion
  }
}
