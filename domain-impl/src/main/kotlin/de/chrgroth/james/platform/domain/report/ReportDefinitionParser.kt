package de.chrgroth.james.platform.domain.report

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.PropertyUnit
import de.chrgroth.james.platform.domain.model.app.RelativePeriod
import de.chrgroth.james.platform.domain.model.app.ReportAggregate
import de.chrgroth.james.platform.domain.model.app.ReportAggregateFunction
import de.chrgroth.james.platform.domain.model.app.ReportAggregateValue
import de.chrgroth.james.platform.domain.model.app.ReportAxis
import de.chrgroth.james.platform.domain.model.app.ReportCalendar
import de.chrgroth.james.platform.domain.model.app.ReportCalcTransform
import de.chrgroth.james.platform.domain.model.app.ReportChartPanel
import de.chrgroth.james.platform.domain.model.app.ReportCheck
import de.chrgroth.james.platform.domain.model.app.ReportCheckPeriod
import de.chrgroth.james.platform.domain.model.app.ReportCheckSeverity
import de.chrgroth.james.platform.domain.model.app.ReportColumn
import de.chrgroth.james.platform.domain.model.app.ReportCumulativeTransform
import de.chrgroth.james.platform.domain.model.app.ReportDefinition
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionError
import de.chrgroth.james.platform.domain.model.app.ReportDefinitionErrorCode
import de.chrgroth.james.platform.domain.model.app.ReportForecastTransform
import de.chrgroth.james.platform.domain.model.app.ReportGapCheck
import de.chrgroth.james.platform.domain.model.app.ReportMovingAverageTransform
import de.chrgroth.james.platform.domain.model.app.ReportPanel
import de.chrgroth.james.platform.domain.model.app.ReportPanelType
import de.chrgroth.james.platform.domain.model.app.ReportQuery
import de.chrgroth.james.platform.domain.model.app.ReportReduce
import de.chrgroth.james.platform.domain.model.app.ReportResampleTransform
import de.chrgroth.james.platform.domain.model.app.ReportRow
import de.chrgroth.james.platform.domain.model.app.ReportRuleCheck
import de.chrgroth.james.platform.domain.model.app.ReportSelectVariable
import de.chrgroth.james.platform.domain.model.app.ReportSeries
import de.chrgroth.james.platform.domain.model.app.ReportSeriesStyle
import de.chrgroth.james.platform.domain.model.app.ReportSort
import de.chrgroth.james.platform.domain.model.app.ReportStatPanel
import de.chrgroth.james.platform.domain.model.app.ReportTab
import de.chrgroth.james.platform.domain.model.app.ReportTablePanel
import de.chrgroth.james.platform.domain.model.app.ReportTextPanel
import de.chrgroth.james.platform.domain.model.app.ReportThreshold
import de.chrgroth.james.platform.domain.model.app.ReportTimeRange
import de.chrgroth.james.platform.domain.model.app.ReportTimeRangeVariable
import de.chrgroth.james.platform.domain.model.app.ReportTransform
import de.chrgroth.james.platform.domain.model.app.ReportVariable
import de.chrgroth.james.platform.domain.model.app.ReportWarningsPanel
import de.chrgroth.james.platform.domain.model.app.ReportYoyTransform
import de.chrgroth.james.platform.domain.model.app.SortDirection
import de.chrgroth.james.platform.domain.model.app.TimeBucket
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.granularityByName
import de.chrgroth.james.platform.domain.model.imports.FilterMode
import de.chrgroth.james.platform.domain.model.imports.FilterOperator
import de.chrgroth.james.platform.domain.model.imports.FilterRule
import java.time.LocalDate
import java.time.MonthDay

/**
 * Parses a report definition (YAML) into a [ReportDefinition] and validates it in the same pass: YAML syntax, the schema (unknown keys are
 * errors, never silently ignored; keys reserved for later steps answer "not supported yet"), and - when [parse] gets the version's entities -
 * the references: entities and fields exist, a time field is a DATE/DATETIME property, aggregated fields are numeric, panels only use declared
 * queries and their fields, chart series share their x axis and fit their y axis' unit. Every error carries its path and, where the YAML
 * source locates it, its line.
 */
@Suppress("LargeClass")
internal object ReportDefinitionParser {

  private val mapper: YAMLMapper = YAMLMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()

  const val MAX_LIMIT = 10_000

  private val ROOT_KEYS = setOf("variables", "queries", "transforms", "checks", "tabs")
  private val ROOT_RESERVED = emptySet<String>()
  private const val VARIABLE_TIME_RANGE = "timeRange"
  private const val VARIABLE_SELECT = "select"
  private val VARIABLE_TYPES = listOf(VARIABLE_TIME_RANGE, VARIABLE_SELECT)
  private val TIME_RANGE_VARIABLE_KEYS = setOf("type", "label", "default", "options")
  private val SELECT_VARIABLE_KEYS = setOf("type", "label", "query", "field", "all", "default")
  private val VARIABLE_NAME = Regex("""^\p{L}[\p{L}\d_]*$""")
  private const val VARIABLE_PREFIX = "$"
  private const val USE_RANGE = "range"
  private const val USE_FILTER = "filter"
  private val TRANSFORM_KEYS = mapOf(
    ReportCalcTransform.KEY to setOf("expr", "unit"),
    ReportCumulativeTransform.KEY to setOf("as"),
    ReportMovingAverageTransform.KEY to setOf("window", "as"),
    ReportYoyTransform.KEY to setOf("as"),
    ReportForecastTransform.KEY to setOf("as"),
    ReportResampleTransform.KEY to setOf("periodStart", "fill"),
  )
  private val TRANSFORM_STEPS = TRANSFORM_KEYS.keys.toList()
  private const val FILL_FORWARD = "forward"
  private const val FILL_NONE = "none"
  private val FILL_MODES = listOf(FILL_FORWARD, FILL_NONE)
  const val MAX_WINDOW = 1_000
  private val QUERY_KEYS = setOf("entity", "timeField", "range", "filter", "fields", "sort", "limit", "aggregate")
  private val FUNCTION_KEYS = ReportAggregateFunction.entries.map { it.key }.toSet()
  private val AGGREGATE_KEYS = setOf("values", "bucket", "periodStart", "groupBy") + FUNCTION_KEYS
  private val RANGE_KEYS = setOf("from", "to")
  private val FILTER_KEYS = setOf("field", "op", "value", "mode", "includeAbsent")
  private val SORT_KEYS = setOf("field", "direction")
  private val TAB_KEYS = setOf("name", "rows")
  private val ROW_KEYS = setOf("panels")
  private val TABLE_KEYS = setOf("query", "columns", "limit")
  private val TEXT_KEYS = setOf("markdown")
  private val COLUMN_KEYS = setOf("field", "label")
  private val CHART_KEYS = setOf("query", "field", "name", "style", "axis", "series", "stack", "zoom")
  private val SERIES_KEYS = setOf("query", "field", "name", "style", "axis")
  private val STAT_KEYS = setOf("query", "field", "reduce", "compare", "thresholds", "config")
  private val STAT_QUERY_KEYS = listOf("query", "field", "reduce", "compare")
  private val THRESHOLD_KEYS = setOf("value", "color")
  private val CHECK_COMMON_KEYS = setOf("message", "severity")
  private val GAP_KEYS = setOf(ReportGapCheck.KEY, "every", "exclude", "current") + CHECK_COMMON_KEYS
  private val RULE_KEYS = setOf(ReportRuleCheck.KEY, "field", "expr", "min", "max", "required", "when") + CHECK_COMMON_KEYS
  private val CHECK_TYPES = listOf(ReportGapCheck.KEY, ReportRuleCheck.KEY)
  private val CALENDAR_KEYS = setOf("query", "field", "to")
  private const val CALENDAR_FORMS = "config.<field>, { query, field, to }"
  private val WARNINGS_KEYS = setOf("checks")
  private const val COMPARE_PREVIOUS_PERIOD = "previous_period"
  private const val COUNT_ROWS = "*"
  private val PERIOD_START_BUCKETS = setOf(TimeBucket.JAHR, TimeBucket.QUARTAL)
  private val ISO_MONTH_DAY = Regex("""^(\d{2})-(\d{2})$""")
  private val GERMAN_MONTH_DAY = Regex("""^(\d{1,2})\.(\d{1,2})\.?$""")
  private val VALUELESS_OPERATORS = setOf(FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL)
  private val REGEX_OPERATORS = setOf(FilterOperator.MATCHES, FilterOperator.NOT_MATCHES)
  private const val DUPLICATE_FIELD_PREFIX = "Duplicate field '"

  data class Result(val definition: ReportDefinition?, val errors: List<ReportDefinitionError>)

  /** The variable name of a `$name` reference, null for any other value. */
  fun variableName(raw: String?): String? = raw?.trim()?.takeIf { it.startsWith(VARIABLE_PREFIX) }?.removePrefix(VARIABLE_PREFIX)

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

  /** Parses a period start as `MM-DD` or `DD.MM.`; 29.02. is rejected (it does not exist most years). */
  fun parsePeriodStart(raw: String): MonthDay? {
    val trimmed = raw.trim()
    val (month, day) = ISO_MONTH_DAY.matchEntire(trimmed)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
      ?: GERMAN_MONTH_DAY.matchEntire(trimmed)?.let { it.groupValues[2].toInt() to it.groupValues[1].toInt() }
      ?: return null
    val monthDay = runCatching { MonthDay.of(month, day) }.getOrNull() ?: return null
    return monthDay.takeUnless { it == MonthDay.of(2, 29) }
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

  /**
   * A declared query as panels see it (after its transforms): [columns] null when its fields are unknown (unresolved entity), so panels skip
   * follow-up errors; [resample] the resampling step turning its x axis into buckets.
   */
  private class QueryInfo(val query: ReportQuery, var columns: Map<String, ResultColumn>?, var resample: ReportResampleTransform? = null)

  /** A `$name` reference to a variable, checked once all variables and queries are known. */
  private class VariableUse(val name: String, val use: String, val path: Path)

  /** The state of a query's result while its transformation steps are read: its columns (null if unknown) and time bucket. */
  private class Pipeline(val info: QueryInfo?) {
    var columns: MutableMap<String, ResultColumn>? = info?.columns?.let { LinkedHashMap(it) }
    var resample: ReportResampleTransform? = null
    val queryName: String get() = info?.query?.name.orEmpty()
    val bucket: TimeBucket? get() = resample?.bucket ?: info?.query?.aggregate?.bucket
  }

  private class ParsedAggregate(val aggregate: ReportAggregate, val valuePaths: List<Path>)

  /** "No field" (COUNT counts rows) as a successful parse, told apart from a failed one (null). */
  private class OptionalField(val field: String?)

  /** A chart series with the paths its errors are reported at. */
  private class SeriesAt(val series: ReportSeries, val path: Path, val queryPath: Path)

  /** A check being read: its common keys and where it is declared. */
  private class CheckAt(
    val name: String,
    val query: String?,
    val info: QueryInfo?,
    val map: ObjectNode,
    val path: Path,
    val message: String?,
    val severity: ReportCheckSeverity,
  )

  @Suppress("TooManyFunctions", "LargeClass")
  private class Reader(private val lines: Map<String, Int>, private val entities: List<EntityDefinition>?) {
    val errors = mutableListOf<ReportDefinitionError>()

    /** Declared queries; the value is null for a query that could not be read at all. */
    private val queryInfos = mutableMapOf<String, QueryInfo?>()

    /** References to variables (`$name`), checked after all queries are read. */
    private val variableUses = mutableListOf<VariableUse>()

    /** Names of the declared checks (also unreadable ones), for warnings panels. */
    private val declaredChecks = mutableSetOf<String>()

    /** Declared variables (null: unreadable) with the path they are declared at. */
    private val declaredVariables = LinkedHashMap<String, Pair<ReportVariable?, Path>>()

    /** The App configuration Entity (ADR 0027) `config.<field>` values refer to. */
    private val configEntity = entities?.find { it.configuration }

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

    fun bool(node: JsonNode, path: Path): Boolean? =
      if (node.isBoolean) node.asBoolean() else null.also { error(ReportDefinitionErrorCode.WRONG_TYPE, path, "true/false") }

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

    /** An enum value by its lower-case YAML key (e.g. `column`), [keyOf] giving each entry's key. */
    fun <T> keyed(node: JsonNode, path: Path, entries: List<T>, keyOf: (T) -> String): T? {
      val raw = text(node, path)?.trim() ?: return null
      return entries.find { keyOf(it).equals(raw, ignoreCase = true) }
        ?: null.also { error(ReportDefinitionErrorCode.INVALID_VALUE, path, raw, entries.joinToString(", ") { keyOf(it) }) }
    }

    // endregion

    fun definition(root: JsonNode): ReportDefinition? {
      val node = obj(root, Path.ROOT) ?: return null
      checkKeys(node, Path.ROOT, ROOT_KEYS, ROOT_RESERVED)
      val variables = node.get("variables")?.let { variables(it, Path.ROOT.key("variables")) } ?: emptyList()
      val queries = node.get("queries")?.let { queries(it, Path.ROOT.key("queries")) } ?: emptyList()
      val transforms = node.get("transforms")?.let { transforms(it, Path.ROOT.key("transforms")) } ?: emptyMap()
      checkVariables()
      val checks = node.get("checks")?.let { checks(it, Path.ROOT.key("checks")) } ?: emptyList()
      val tabs = required(node, "tabs", Path.ROOT)?.let { tabs(it, Path.ROOT.key("tabs")) } ?: emptyList()
      return ReportDefinition(queries, tabs, variables, transforms, checks)
    }

    // region variables

    private fun variables(node: JsonNode, path: Path): List<ReportVariable> {
      if (node.isNull) return emptyList()
      val map = obj(node, path) ?: return emptyList()
      return map.properties().mapNotNull { (name, variableNode) ->
        val variablePath = path.key(name)
        if (!VARIABLE_NAME.matches(name)) error(ReportDefinitionErrorCode.VARIABLE_NAME_INVALID, variablePath, name)
        val variable = variable(name, variableNode, variablePath)
        declaredVariables[name] = variable to variablePath
        variable
      }
    }

    private fun variable(name: String, node: JsonNode, path: Path): ReportVariable? {
      val map = obj(node, path) ?: return null
      val type = required(map, "type", path)?.let { keyed(it, path.key("type"), VARIABLE_TYPES) { t -> t } } ?: return null
      val label = map.get("label")?.let { text(it, path.key("label")) }?.trim()?.takeIf { it.isNotEmpty() }
      return if (type == VARIABLE_TIME_RANGE) timeRangeVariable(name, label, map, path) else selectVariable(name, label, map, path)
    }

    private fun timeRangeVariable(name: String, label: String?, map: ObjectNode, path: Path): ReportVariable {
      checkKeys(map, path, TIME_RANGE_VARIABLE_KEYS)
      val allowed = ReportTimeRangeVariable.DEFAULT_OPTIONS
      val options = map.get("options")?.let { node ->
        list(node, path.key("options"))?.mapIndexedNotNull { i, option ->
          val optionPath = path.key("options").index(i)
          val raw = nonBlankText(option, optionPath, "options") ?: return@mapIndexedNotNull null
          raw.takeIf { it in allowed } ?: null.also { error(ReportDefinitionErrorCode.INVALID_VALUE, optionPath, raw, allowed.joinToString(", ")) }
        }
      }?.distinct()?.ifEmpty { null } ?: allowed
      val default = map.get("default")?.let { node ->
        val raw = nonBlankText(node, path.key("default"), "default") ?: return@let null
        raw.takeIf { it in options } ?: null.also { error(ReportDefinitionErrorCode.INVALID_VALUE, path.key("default"), raw, options.joinToString(", ")) }
      } ?: options.first()
      return ReportTimeRangeVariable(name, label, options, default)
    }

    private fun selectVariable(name: String, label: String?, map: ObjectNode, path: Path): ReportVariable? {
      checkKeys(map, path, SELECT_VARIABLE_KEYS)
      val query = required(map, "query", path)?.let { nonBlankText(it, path.key("query"), "query") }
      val field = required(map, "field", path)?.let { nonBlankText(it, path.key("field"), "field") }
      val includeAll = map.get("all")?.let { bool(it, path.key("all")) } ?: false
      val default = map.get("default")?.let { nonBlankText(it, path.key("default"), "default") } ?: ReportSelectVariable.FIRST
      if (default == ReportSelectVariable.ALL_VALUE && !includeAll) error(ReportDefinitionErrorCode.KEY_CONFLICT, path.key("default"), "default: all", "all: false")
      if (query == null || field == null) return null
      return ReportSelectVariable(name, label, query, field, includeAll, default)
    }

    /** Records a `$name` range or filter value for [checkVariables]. */
    private fun variableUse(raw: String, use: String, path: Path) {
      variableName(raw)?.let { variableUses += VariableUse(it, use, path) }
    }

    /** Checks variable references and the queries feeding select variables, once all variables, queries and transforms are read. */
    private fun checkVariables() {
      declaredVariables.values.forEach { (variable, path) ->
        val select = variable as? ReportSelectVariable ?: return@forEach
        val info = declaredQuery(select.query, path.key("query")) ?: return@forEach
        info.columns?.let { columns -> if (select.field !in columns) error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, path.key("field"), select.field, select.query) }
        val usesVariable = info.query.range is ReportTimeRange.Variable || info.query.filter.any { variableName(it.value) != null }
        if (usesVariable) error(ReportDefinitionErrorCode.VARIABLE_OPTIONS_QUERY_INVALID, path.key("query"), select.name, select.query)
      }
      variableUses.forEach { use ->
        if (!declaredVariables.containsKey(use.name)) return@forEach error(ReportDefinitionErrorCode.VARIABLE_NOT_FOUND, use.path, VARIABLE_PREFIX + use.name)
        val fits = when (val variable = declaredVariables[use.name]?.first ?: return@forEach) {
          is ReportTimeRangeVariable -> use.use == USE_RANGE
          is ReportSelectVariable -> use.use == USE_FILTER || selectsBucket(variable)
        }
        if (!fits) error(ReportDefinitionErrorCode.VARIABLE_TYPE_MISMATCH, use.path, VARIABLE_PREFIX + use.name, use.use)
      }
    }

    /** Whether the select variable's field is a time bucket (unknown columns: whether it is named `bucket`). */
    private fun selectsBucket(variable: ReportSelectVariable): Boolean {
      val info = queryInfos[variable.query] ?: return true
      val columns = info.columns ?: return variable.field == ReportAggregate.BUCKET_FIELD
      return columns[variable.field]?.bucket != null
    }

    // endregion

    // region transforms

    private fun transforms(node: JsonNode, path: Path): Map<String, List<ReportTransform>> {
      if (node.isNull) return emptyMap()
      val map = obj(node, path) ?: return emptyMap()
      val result = LinkedHashMap<String, List<ReportTransform>>()
      map.properties().forEach { (queryName, stepsNode) ->
        val stepsPath = path.key(queryName)
        val info = declaredQuery(queryName, stepsPath)
        val steps = list(stepsNode, stepsPath) ?: return@forEach
        val pipeline = Pipeline(info)
        result[queryName] = steps.mapIndexedNotNull { i, step -> transformStep(step, stepsPath.index(i), pipeline) }
        info?.columns = pipeline.columns
        info?.resample = pipeline.resample
      }
      return result
    }

    private fun transformStep(node: JsonNode, path: Path, pipeline: Pipeline): ReportTransform? {
      val map = obj(node, path) ?: return null
      val steps = map.fieldNames().asSequence().filter { it in TRANSFORM_STEPS }.toList()
      when {
        steps.isEmpty() -> return null.also { error(ReportDefinitionErrorCode.TRANSFORM_STEP_MISSING, path, TRANSFORM_STEPS.joinToString(", ")) }
        steps.size > 1 -> return null.also { error(ReportDefinitionErrorCode.TRANSFORM_STEP_AMBIGUOUS, path, steps.joinToString(", ")) }
      }
      val step = steps.single()
      checkKeys(map, path, TRANSFORM_KEYS.getValue(step) + step)
      val value = map.get(step)
      return when (step) {
        ReportCalcTransform.KEY -> calcStep(map, value, path, pipeline)
        ReportResampleTransform.KEY -> resampleStep(map, value, path, pipeline)
        else -> fieldStep(step, map, value, path, pipeline)
      }
    }

    private fun calcStep(map: ObjectNode, value: JsonNode, path: Path, pipeline: Pipeline): ReportTransform? {
      val namePath = path.key(ReportCalcTransform.KEY)
      val name = nonBlankText(value, namePath, ReportCalcTransform.KEY)
      val exprPath = path.key("expr")
      val source = required(map, "expr", path)?.let { nonBlankText(it, exprPath, "expr") }
      val expression = source?.let { expression(it, exprPath) }
      val unit = map.get("unit")?.let { unit(it, path.key("unit")) }
      expression?.identifiers?.forEach { identifier ->
        if (ReportFields.configPath(identifier) != null) {
          val field = configField(identifier, exprPath)
          if (field != null && !ReportFields.isNumericType(field.type)) error(ReportDefinitionErrorCode.FIELD_NOT_NUMERIC, exprPath, identifier)
        } else {
          sourceColumn(identifier, exprPath, pipeline)
        }
      }
      if (name != null) output(name, null, namePath, pipeline, ResultColumn(name, ColumnRole.VALUE, PropertyType.DOUBLE, unit))
      if (name == null || source == null || expression == null || (map.has("unit") && unit == null)) return null
      return ReportCalcTransform(name, source, unit)
    }

    private fun expression(source: String, path: Path): ReportExpression? = try {
      ReportExpression.parse(source)
    } catch (e: ReportExpression.ParseException) {
      val function = e.unknownFunction
      if (function != null) {
        error(ReportDefinitionErrorCode.EXPRESSION_UNKNOWN_FUNCTION, path, function, ReportExpression.FUNCTIONS.keys.joinToString(", "))
      } else {
        error(ReportDefinitionErrorCode.EXPRESSION_INVALID, path, source, e.position.toString(), e.detail)
      }
      null
    }

    /** A granularity name of any unit family, e.g. `SECONDS` or `kilograms`; the value is stored and displayed in that granularity. */
    private fun unit(node: JsonNode, path: Path): PropertyUnit? {
      val raw = nonBlankText(node, path, "unit") ?: return null
      val granularity = UnitFamily.entries.firstNotNullOfOrNull { granularityByName(it, raw) }
        ?: return null.also { error(ReportDefinitionErrorCode.INVALID_VALUE, path, raw, ReportFields.GRANULARITY_NAMES.joinToString(", ")) }
      return PropertyUnit(granularity.family, granularity, granularity)
    }

    /** cumulative, movingAverage, yoy and forecast: a numeric source field and an output field (`as`). */
    @Suppress("CyclomaticComplexMethod")
    private fun fieldStep(step: String, map: ObjectNode, value: JsonNode, path: Path, pipeline: Pipeline): ReportTransform? {
      val field = nonBlankText(value, path.key(step), step) ?: return null
      val source = sourceColumn(field, path.key(step), pipeline)
      val explicitOutput = map.get("as")?.let { nonBlankText(it, path.key("as"), "as") }
      val window = if (step == ReportMovingAverageTransform.KEY) required(map, "window", path)?.let { int(it, path.key("window"), 1, MAX_WINDOW) } ?: return null else 0
      val query = pipeline.info?.query
      if (step == ReportYoyTransform.KEY && query != null && pipeline.bucket == null) {
        error(ReportDefinitionErrorCode.TRANSFORM_NEEDS_BUCKET, path.key(step), step, query.name)
      }
      if (step == ReportForecastTransform.KEY && query != null && pipeline.bucket == null && !closedRange(query)) {
        error(ReportDefinitionErrorCode.FORECAST_NEEDS_PERIOD, path.key(step), query.name)
      }
      val (transform, output) = when (step) {
        ReportCumulativeTransform.KEY -> (explicitOutput ?: field).let { ReportCumulativeTransform(field, it) to it }
        ReportMovingAverageTransform.KEY -> (explicitOutput ?: field).let { ReportMovingAverageTransform(field, window, it) to it }
        ReportYoyTransform.KEY -> (explicitOutput ?: (field + ReportYoyTransform.SUFFIX)).let { ReportYoyTransform(field, it) to it }
        else -> (explicitOutput ?: (field + ReportForecastTransform.SUFFIX)).let { ReportForecastTransform(field, it) to it }
      }
      // A running total and last year's value keep the source type; averages and forecasts have fractions.
      val type = if (step == ReportCumulativeTransform.KEY || step == ReportYoyTransform.KEY) source?.type else source?.type?.let { PropertyType.DOUBLE }
      output(output, field, if (explicitOutput != null) path.key("as") else path.key(step), pipeline, ResultColumn(output, ColumnRole.VALUE, type, source?.unit))
      return transform
    }

    private fun resampleStep(map: ObjectNode, value: JsonNode, path: Path, pipeline: Pipeline): ReportTransform? {
      val stepPath = path.key(ReportResampleTransform.KEY)
      val bucket = enumValue<TimeBucket>(value, stepPath)
      val periodStart = map.get("periodStart")?.let { node ->
        val raw = text(node, path.key("periodStart"))?.trim() ?: return@let null
        parsePeriodStart(raw) ?: null.also { error(ReportDefinitionErrorCode.PERIOD_START_INVALID, path.key("periodStart"), raw) }
      }
      if (map.has("periodStart") && bucket != null && bucket !in PERIOD_START_BUCKETS) error(ReportDefinitionErrorCode.PERIOD_START_NOT_APPLICABLE, path.key("periodStart"))
      val fill = map.get("fill")?.let { keyed(it, path.key("fill"), FILL_MODES) { mode -> mode } } ?: FILL_FORWARD
      pipeline.info?.query?.let { query ->
        if (query.aggregate?.groupBy != null) error(ReportDefinitionErrorCode.KEY_CONFLICT, stepPath, ReportResampleTransform.KEY, "groupBy")
        if (query.timeField == null && pipeline.bucket == null) error(ReportDefinitionErrorCode.RESAMPLE_NEEDS_TIME, stepPath, query.name)
      }
      bucket ?: return null
      val transform = ReportResampleTransform(bucket, periodStart, fill == FILL_FORWARD)
      pipeline.columns = pipeline.columns?.let { columns ->
        val resampled = LinkedHashMap<String, ResultColumn>()
        resampled[ReportAggregate.BUCKET_FIELD] = ResultColumn(ReportAggregate.BUCKET_FIELD, ColumnRole.BUCKET, PropertyType.STRING, bucket = bucket)
        columns.filterKeys { it != ReportAggregate.BUCKET_FIELD }.forEach { (key, column) -> resampled[key] = column }
        resampled
      }
      pipeline.resample = transform
      return transform
    }

    /** A numeric field of the result so far; null (with an error) if missing or not numeric, or if the columns are unknown. */
    private fun sourceColumn(field: String, path: Path, pipeline: Pipeline): ResultColumn? {
      val columns = pipeline.columns ?: return null
      val column = columns[field] ?: return null.also { error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, path, field, pipeline.queryName) }
      if (column.type != null && !column.numeric) return null.also { error(ReportDefinitionErrorCode.FIELD_NOT_NUMERIC, path, field) }
      return column
    }

    /** Adds the output field [name]: replacing [source] in place is fine, taking another existing field's name is not. */
    private fun output(name: String, source: String?, path: Path, pipeline: Pipeline, column: ResultColumn) {
      val columns = pipeline.columns ?: return
      if (name != source && name in columns) return error(ReportDefinitionErrorCode.DUPLICATE_RESULT_FIELD, path, name)
      columns[name] = column
    }

    // endregion

    // region checks

    private fun checks(node: JsonNode, path: Path): List<ReportCheck> {
      if (node.isNull) return emptyList()
      val map = obj(node, path) ?: return emptyList()
      return map.properties().mapNotNull { (name, checkNode) ->
        declaredChecks += name
        check(name, checkNode, path.key(name))
      }
    }

    private fun check(name: String, node: JsonNode, path: Path): ReportCheck? {
      val map = obj(node, path) ?: return null
      val types = map.fieldNames().asSequence().filter { it in CHECK_TYPES }.toList()
      when {
        types.isEmpty() -> return null.also { error(ReportDefinitionErrorCode.CHECK_TYPE_MISSING, path, CHECK_TYPES.joinToString(", ")) }
        types.size > 1 -> return null.also { error(ReportDefinitionErrorCode.CHECK_TYPE_AMBIGUOUS, path, types.joinToString(", ")) }
      }
      val type = types.single()
      checkKeys(map, path, if (type == ReportGapCheck.KEY) GAP_KEYS else RULE_KEYS)
      val query = nonBlankText(map.get(type), path.key(type), type)
      val info = query?.let { declaredQuery(it, path.key(type)) }
      val message = map.get("message")?.let { text(it, path.key("message")) }?.trim()?.takeIf { it.isNotEmpty() }
      val severity = map.get("severity")?.let { keyed(it, path.key("severity"), ReportCheckSeverity.entries) { s -> s.key } } ?: ReportCheckSeverity.WARNING
      val at = CheckAt(name, query, info, map, path, message, severity)
      return if (type == ReportGapCheck.KEY) gapCheck(at) else ruleCheck(at)
    }

    private fun gapCheck(at: CheckAt): ReportCheck? {
      val (map, path) = at.map to at.path
      val every = required(map, "every", path)?.let { enumValue<ReportCheckPeriod>(it, path.key("every")) }
      val current = map.get("current")?.let { bool(it, path.key("current")) } ?: false
      at.info?.query?.let { q ->
        if (q.timeField == null) error(ReportDefinitionErrorCode.TIME_FIELD_MISSING, path.key(ReportGapCheck.KEY), ReportGapCheck.KEY)
        if (q.aggregate != null) error(ReportDefinitionErrorCode.KEY_CONFLICT, path.key(ReportGapCheck.KEY), ReportGapCheck.KEY, "aggregate")
      }
      val exclude = map.get("exclude")?.let { node ->
        val excludePath = path.key("exclude")
        if (node is ArrayNode) node.mapIndexedNotNull { i, item -> calendar(item, excludePath.index(i)) } else listOfNotNull(calendar(node, excludePath))
      }.orEmpty()
      at.message?.let { checkPlaceholders(it, path.key("message"), setOf(ReportCheck.PERIOD_PLACEHOLDER)) }
      if (at.query == null || every == null) return null
      return ReportGapCheck(at.name, at.query, every, exclude, current, at.message, at.severity)
    }

    /** `config.<field>` (a DATE field or LIST of dates of the configuration) or `{ query, field, to }` (time fields of a plain query). */
    private fun calendar(node: JsonNode, path: Path): ReportCalendar? {
      if (node.isValueNode) {
        val raw = nonBlankText(node, path, "exclude") ?: return null
        val configPath = ReportFields.configPath(raw)
          ?: return null.also { error(ReportDefinitionErrorCode.INVALID_VALUE, path, raw, CALENDAR_FORMS) }
        configField(raw, path)?.let { field ->
          val dates = ReportFields.isTimeType(field.type) || (field.type == PropertyType.LIST && field.listItemType?.let { ReportFields.isTimeType(it) } == true)
          if (!dates) error(ReportDefinitionErrorCode.CALENDAR_INVALID, path, raw.trim())
        }
        return ReportCalendar.Config(configPath)
      }
      val map = obj(node, path) ?: return null
      checkKeys(map, path, CALENDAR_KEYS)
      val query = required(map, "query", path)?.let { nonBlankText(it, path.key("query"), "query") }
      val field = map.get("field")?.let { nonBlankText(it, path.key("field"), "field") }
      val to = map.get("to")?.let { nonBlankText(it, path.key("to"), "to") }
      val info = query?.let { declaredQuery(it, path.key("query")) }
      info?.query?.let { q ->
        if (q.aggregate != null) error(ReportDefinitionErrorCode.KEY_CONFLICT, path.key("query"), "exclude", "aggregate")
        if (field == null && q.timeField == null) error(ReportDefinitionErrorCode.TIME_FIELD_MISSING, path, "exclude")
        val columns = info.columns ?: return@let
        listOfNotNull(field?.let { it to path.key("field") }, to?.let { it to path.key("to") }).forEach { (name, fieldPath) ->
          val column = columns[name] ?: return@forEach error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, fieldPath, name, q.name)
          if (column.type != null && !ReportFields.isTimeType(column.type)) error(ReportDefinitionErrorCode.TIME_FIELD_INVALID, fieldPath, name)
        }
      }
      return query?.let { ReportCalendar.Query(it, field, to) }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun ruleCheck(at: CheckAt): ReportCheck? {
      val (map, path) = at.map to at.path
      val columns = at.info?.columns
      val field = map.get("field")?.let { nonBlankText(it, path.key("field"), "field") }
      if (map.has("field") && map.has("expr")) error(ReportDefinitionErrorCode.KEY_CONFLICT, path.key("expr"), "expr", "field")
      val exprPath = path.key("expr")
      val source = map.get("expr")?.let { nonBlankText(it, exprPath, "expr") }
      val expression = source?.let { expression(it, exprPath) }
      expression?.identifiers?.forEach { identifier ->
        if (ReportFields.configPath(identifier) != null) {
          val configField = configField(identifier, exprPath)
          if (configField != null && !ReportFields.isNumericType(configField.type)) error(ReportDefinitionErrorCode.FIELD_NOT_NUMERIC, exprPath, identifier)
        } else {
          numericColumn(identifier, exprPath, columns, at.query)
        }
      }
      val unit = field?.let { numericColumn(it, path.key("field"), columns, at.query) }?.unit
      val hasValue = map.has("field") || map.has("expr")
      val min = map.get("min")?.let { bound(it, path.key("min"), "min", unit, hasValue) }
      val max = map.get("max")?.let { bound(it, path.key("max"), "max", unit, hasValue) }
      if (hasValue && !map.has("min") && !map.has("max")) error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "min/max")
      if (!hasValue && (map.has("min") || map.has("max"))) error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "field/expr")
      val minNumber = min?.takeIf { hasValue && ReportFields.configPath(it) == null }?.let { ReportValues.parseNumber(it, unit) }
      val maxNumber = max?.takeIf { hasValue && ReportFields.configPath(it) == null }?.let { ReportValues.parseNumber(it, unit) }
      if (minNumber != null && maxNumber != null && minNumber > maxNumber) error(ReportDefinitionErrorCode.INVALID_RANGE, path.key("min"))
      val required = fieldList(map, "required", path, columns, at.query)
      val whenPresent = fieldList(map, "when", path, columns, at.query)
      if (map.has("when") && !map.has("required")) error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "required")
      if (!hasValue && !map.has("required") && !map.has("min") && !map.has("max")) error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "field/expr/required")
      if (at.message != null && columns != null) {
        val placeholders = listOf(ReportCheck.VALUE_PLACEHOLDER, ReportCheck.MIN_PLACEHOLDER, ReportCheck.MAX_PLACEHOLDER, ReportCheck.MISSING_PLACEHOLDER)
        checkPlaceholders(at.message, path.key("message"), columns.keys + placeholders)
      }
      if (at.query == null || (map.has("field") && field == null) || (map.has("expr") && expression == null)) return null
      return ReportRuleCheck(at.name, at.query, field, source, min, max, required, whenPresent, at.message, at.severity)
    }

    /** A numeric field of a query's result (unknown columns: not checked); null (with an error) if missing or not numeric. */
    private fun numericColumn(field: String, path: Path, columns: Map<String, ResultColumn>?, query: String?): ResultColumn? {
      columns ?: return null
      val column = columns[field] ?: return null.also { error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, path, field, query.orEmpty()) }
      if (column.type != null && !column.numeric) return null.also { error(ReportDefinitionErrorCode.FIELD_NOT_NUMERIC, path, field) }
      return column
    }

    /** A rule's `min`/`max`: a number, a unit text in [unit] or a numeric `config.<field>`; only checked when the rule has a value ([check]). */
    private fun bound(node: JsonNode, path: Path, key: String, unit: PropertyUnit?, check: Boolean): String? {
      val raw = nonBlankText(node, path, key) ?: return null
      if (!check) return raw
      if (ReportFields.configPath(raw) != null) {
        checkConfigNumber(raw, path, unit)
      } else if (ReportValues.parseNumber(raw, unit) == null) {
        error(ReportDefinitionErrorCode.INVALID_NUMBER, path, raw)
      }
      return raw
    }

    /** `required`/`when`: a field or a list of fields of the query's result. */
    private fun fieldList(map: ObjectNode, key: String, path: Path, columns: Map<String, ResultColumn>?, query: String?): List<String> {
      val node = map.get(key) ?: return emptyList()
      val listPath = path.key(key)
      val fields = if (node is ArrayNode) {
        if (node.isEmpty) error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, listPath, key)
        node.mapIndexedNotNull { i, item -> nonBlankText(item, listPath.index(i), key)?.let { it to listPath.index(i) } }
      } else {
        listOfNotNull(nonBlankText(node, listPath, key)?.let { it to listPath })
      }
      if (columns != null) {
        fields.forEach { (field, fieldPath) -> if (field !in columns) error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, fieldPath, field, query.orEmpty()) }
      }
      return fields.map { it.first }
    }

    private fun checkPlaceholders(message: String, path: Path, allowed: Set<String>) {
      ReportCheck.PLACEHOLDER.findAll(message).map { it.groupValues[1] }.distinct().forEach { placeholder ->
        if (placeholder !in allowed) error(ReportDefinitionErrorCode.MESSAGE_PLACEHOLDER_UNKNOWN, path, placeholder, allowed.joinToString(", ") { "{$it}" })
      }
    }

    // endregion


    // region queries

    private fun queries(node: JsonNode, path: Path): List<ReportQuery> {
      if (node.isNull) return emptyList()
      val map = obj(node, path) ?: return emptyList()
      return map.properties().mapNotNull { (name, queryNode) -> query(name, queryNode, path.key(name)) }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun query(name: String, node: JsonNode, path: Path): ReportQuery? {
      queryInfos[name] = null
      val map = obj(node, path) ?: return null
      checkKeys(map, path, QUERY_KEYS)
      val entityName = required(map, "entity", path)?.let { nonBlankText(it, path.key("entity"), "entity") }
      val entity = entityName?.let { resolveEntity(it, path.key("entity")) }
      val timeField = map.get("timeField")?.let { nonBlankText(it, path.key("timeField"), "timeField") }
      val range = map.get("range")?.let { range(it, path.key("range")) }
      if (range != null && map.get("timeField") == null) error(ReportDefinitionErrorCode.TIME_FIELD_MISSING, path.key("range"), "range")
      val filter = map.get("filter")?.let { node -> list(node, path.key("filter"))?.mapIndexedNotNull { i, rule -> filterRule(rule, path.key("filter").index(i)) } }.orEmpty()
      val fields = map.get("fields")?.let { node -> list(node, path.key("fields"))?.mapIndexedNotNull { i, f -> nonBlankText(f, path.key("fields").index(i), "field") } }.orEmpty()
      val sort = map.get("sort")?.let { node -> list(node, path.key("sort"))?.mapIndexedNotNull { i, s -> sort(s, path.key("sort").index(i)) } }.orEmpty()
      val limit = map.get("limit")?.let { int(it, path.key("limit"), 1, MAX_LIMIT) }
      val aggregatePath = path.key("aggregate")
      val parsedAggregate = map.get("aggregate")?.let { aggregate(it, aggregatePath) }
      val aggregate = parsedAggregate?.aggregate
      if (map.get("aggregate") != null && map.get("fields") != null) error(ReportDefinitionErrorCode.KEY_CONFLICT, path.key("fields"), "fields", "aggregate")
      if (aggregate?.bucket != null && map.get("timeField") == null) error(ReportDefinitionErrorCode.TIME_FIELD_MISSING, aggregatePath.key("bucket"), "bucket")

      var columns: Map<String, ResultColumn>? = null
      if (entity != null) {
        timeField?.let { checkTimeField(entity, it, path.key("timeField")) }
        fields.forEachIndexed { i, field -> checkField(entity, field, path.key("fields").index(i)) }
        filter.forEachIndexed { i, rule -> checkField(entity, rule.sourcePath, path.key("filter").index(i).key("field")) }
        columns = if (parsedAggregate != null) {
          aggregateColumns(entity, parsedAggregate, aggregatePath)
        } else {
          sort.forEachIndexed { i, s -> checkField(entity, s.field, path.key("sort").index(i).key("field")) }
          fields.ifEmpty { ReportFields.defaultFields(entity) }
            .mapNotNull { field -> (ReportFields.resolve(entity, field, entities.orEmpty()) as? FieldResolution.Resolved)?.field }
            .associate { it.path to ResultColumn.of(it) }
        }
      } else if (entities == null) {
        // Schema-only check: panels can still be checked against the declared field list or the aggregate's result fields.
        columns = aggregate?.resultFields?.associateWith { ResultColumn(it, ColumnRole.VALUE, null) }
          ?: fields.takeIf { it.isNotEmpty() }?.associateWith { ResultColumn(it, ColumnRole.FIELD, null) }
      }
      if (aggregate != null && columns != null) {
        sort.forEachIndexed { i, s -> if (s.field !in columns) error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, path.key("sort").index(i).key("field"), s.field, name) }
      }
      val query = ReportQuery(name, entityName.orEmpty(), timeField, range, filter, fields, sort, limit, aggregate)
      queryInfos[name] = QueryInfo(query, columns)
      return query
    }

    private fun aggregate(node: JsonNode, path: Path): ParsedAggregate? {
      val map = obj(node, path) ?: return null
      checkKeys(map, path, AGGREGATE_KEYS)
      val bucket = map.get("bucket")?.let { enumValue<TimeBucket>(it, path.key("bucket")) }
      val periodStart = map.get("periodStart")?.let { node ->
        val raw = text(node, path.key("periodStart"))?.trim() ?: return@let null
        parsePeriodStart(raw) ?: null.also { error(ReportDefinitionErrorCode.PERIOD_START_INVALID, path.key("periodStart"), raw) }
      }
      if (map.get("periodStart") != null && (bucket == null || bucket !in PERIOD_START_BUCKETS)) {
        error(ReportDefinitionErrorCode.PERIOD_START_NOT_APPLICABLE, path.key("periodStart"))
      }
      val groupBy = map.get("groupBy")?.let { nonBlankText(it, path.key("groupBy"), "groupBy") }

      val values = mutableListOf<Pair<ReportAggregateValue, Path>>()
      val shorthand = FUNCTION_KEYS.filter { map.has(it) }
      shorthand.forEach { key ->
        val function = ReportAggregateFunction.byKey(key)!!
        aggregateField(function, map.get(key), path.key(key))?.let { values += ReportAggregateValue(key, function, it.field) to path.key(key) }
      }
      map.get("values")?.let { valuesNode ->
        if (shorthand.isNotEmpty()) error(ReportDefinitionErrorCode.KEY_CONFLICT, path.key("values"), "values", shorthand.first())
        val valuesMap = obj(valuesNode, path.key("values")) ?: return@let
        valuesMap.properties().forEach { (name, valueNode) ->
          aggregateValue(name, valueNode, path.key("values").key(name))?.let { values += it to path.key("values").key(name) }
        }
      }
      if (map.get("values") == null && shorthand.isEmpty()) error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "values")

      val taken = listOfNotNull(ReportAggregate.BUCKET_FIELD.takeIf { bucket != null }, groupBy).toMutableSet()
      values.forEach { (value, valuePath) -> if (!taken.add(value.name)) error(ReportDefinitionErrorCode.DUPLICATE_RESULT_FIELD, valuePath, value.name) }
      return ParsedAggregate(ReportAggregate(values.map { it.first }, bucket, periodStart, groupBy), values.map { it.second })
    }

    /** `name: count` (count rows) or `name: { <function>: <field> }`. */
    private fun aggregateValue(name: String, node: JsonNode, path: Path): ReportAggregateValue? {
      if (node.isValueNode) {
        val raw = text(node, path)?.trim() ?: return null
        if (raw.equals(ReportAggregateFunction.COUNT.key, ignoreCase = true)) return ReportAggregateValue(name, ReportAggregateFunction.COUNT, null)
        return null.also { error(ReportDefinitionErrorCode.INVALID_VALUE, path, raw, ReportAggregateFunction.COUNT.key) }
      }
      val map = obj(node, path) ?: return null
      checkKeys(map, path, FUNCTION_KEYS)
      val keys = FUNCTION_KEYS.filter { map.has(it) }
      when {
        keys.isEmpty() -> return null.also { error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, FUNCTION_KEYS.joinToString("/")) }
        keys.size > 1 -> return null.also { error(ReportDefinitionErrorCode.KEY_CONFLICT, path.key(keys[1]), keys[1], keys[0]) }
      }
      val key = keys.single()
      val function = ReportAggregateFunction.byKey(key)!!
      return aggregateField(function, map.get(key), path.key(key))?.let { ReportAggregateValue(name, function, it.field) }
    }

    private fun aggregateField(function: ReportAggregateFunction, node: JsonNode, path: Path): OptionalField? {
      if (function == ReportAggregateFunction.COUNT && (node.isNull || (node.isValueNode && node.asText().trim() == COUNT_ROWS))) return OptionalField(null)
      val field = nonBlankText(node, path, function.key) ?: return null
      if (field == COUNT_ROWS) return null.also { error(ReportDefinitionErrorCode.INVALID_VALUE, path, field, ReportAggregateFunction.COUNT.key) }
      return OptionalField(field)
    }

    private fun aggregateColumns(entity: EntityDefinition, parsed: ParsedAggregate, path: Path): Map<String, ResultColumn> {
      val aggregate = parsed.aggregate
      val columns = LinkedHashMap<String, ResultColumn>()
      aggregate.bucket?.let { columns[ReportAggregate.BUCKET_FIELD] = ResultColumn(ReportAggregate.BUCKET_FIELD, ColumnRole.BUCKET, null, bucket = it) }
      aggregate.groupBy?.let { groupBy ->
        checkField(entity, groupBy, path.key("groupBy"))?.let { columns[groupBy] = ResultColumn.of(it, ColumnRole.GROUP) }
      }
      aggregate.values.forEachIndexed { i, value ->
        val valuePath = parsed.valuePaths[i]
        val source = value.field?.let { field ->
          val resolved = checkField(entity, field, valuePath) ?: return@forEachIndexed
          if (value.function != ReportAggregateFunction.COUNT && !ReportFields.isNumericType(resolved.type)) {
            error(ReportDefinitionErrorCode.FIELD_NOT_NUMERIC, valuePath, field)
            return@forEachIndexed
          }
          ResultColumn.of(resolved)
        }
        columns.putIfAbsent(value.name, ResultColumn.value(value.name, value.function, source))
      }
      return columns
    }

    private fun resolveEntity(name: String, path: Path): EntityDefinition? {
      val all = entities ?: return null
      return all.find { it.name == name } ?: null.also { error(ReportDefinitionErrorCode.ENTITY_NOT_FOUND, path, name) }
    }

    /** Resolves a `config.<field>` value against the configuration Entity; null for any other value, schema-only checks or an error. */
    private fun configField(raw: String?, path: Path): ResolvedField? {
      val configPath = ReportFields.configPath(raw) ?: return null
      val all = entities ?: return null
      val entity = configEntity ?: return null.also { error(ReportDefinitionErrorCode.CONFIG_NOT_DEFINED, path, raw!!.trim()) }
      return when (val resolution = ReportFields.resolve(entity, configPath, all)) {
        is FieldResolution.Resolved -> resolution.field
        is FieldResolution.Failed -> null.also { error(ReportDefinitionErrorCode.CONFIG_FIELD_NOT_FOUND, path, configPath, entity.name) }
      }
    }

    /** A numeric `config.<field>` value compared with values in [unit]: the field must be numeric and its unit of the same family. */
    private fun checkConfigNumber(raw: String, path: Path, unit: PropertyUnit?) {
      val field = configField(raw, path) ?: return
      when {
        !ReportFields.isNumericType(field.type) -> error(ReportDefinitionErrorCode.FIELD_NOT_NUMERIC, path, raw.trim())
        unit != null && field.unit != null && unit.family != field.unit?.family -> error(ReportDefinitionErrorCode.CONFIG_UNIT_MISMATCH, path, raw.trim())
      }
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
        variableName(key)?.let { name ->
          variableUse(key, USE_RANGE, path)
          return ReportTimeRange.Variable(name)
        }
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
      val includeAbsent = map.get("includeAbsent")?.let { bool(it, path.key("includeAbsent")) } ?: false
      val value = map.get("value")?.takeIf { !it.isNull }?.let { text(it, path.key("value")) }
      if (operator != null && operator !in VALUELESS_OPERATORS && value == null) error(ReportDefinitionErrorCode.FILTER_VALUE_MISSING, path, operator.name)
      if (operator in REGEX_OPERATORS && value != null && runCatching { Regex(value) }.isFailure) error(ReportDefinitionErrorCode.INVALID_REGEX, path.key("value"), value)
      configField(value, path.key("value"))
      value?.let { variableUse(it, USE_FILTER, path.key("value")) }
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
        ReportPanelType.TIMESERIES, ReportPanelType.BAR -> chartPanel(type, map, path, title)
        ReportPanelType.STAT -> statPanel(map, path, title)
        ReportPanelType.WARNINGS -> warningsPanel(map, path, title)
        else -> null
      }
    }

    /** The declared query [name] (reporting QUERY_NOT_FOUND otherwise); null also for a declared but unreadable query. */
    private fun declaredQuery(name: String, path: Path): QueryInfo? {
      if (!queryInfos.containsKey(name)) return null.also { error(ReportDefinitionErrorCode.QUERY_NOT_FOUND, path, name) }
      return queryInfos[name]
    }

    private fun tablePanel(map: ObjectNode, path: Path, title: String): ReportPanel? {
      checkKeys(map, path, TABLE_KEYS + ReportPanelType.TABLE.key)
      val query = required(map, "query", path)?.let { nonBlankText(it, path.key("query"), "query") }
      val columns = map.get("columns")?.let { node -> list(node, path.key("columns"))?.mapIndexedNotNull { i, c -> column(c, path.key("columns").index(i)) } }.orEmpty()
      val limit = map.get("limit")?.let { int(it, path.key("limit"), 1, MAX_LIMIT) }
      if (query != null) {
        declaredQuery(query, path.key("query"))?.columns?.let { available ->
          columns.forEachIndexed { i, column ->
            if (column.field !in available) error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, path.key("columns").index(i), column.field, query)
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

    private fun warningsPanel(map: ObjectNode, path: Path, title: String): ReportPanel {
      checkKeys(map, path, WARNINGS_KEYS + ReportPanelType.WARNINGS.key)
      val checks = map.get("checks")?.let { node ->
        list(node, path.key("checks"))?.mapIndexedNotNull { i, item ->
          val itemPath = path.key("checks").index(i)
          nonBlankText(item, itemPath, "checks")?.also { if (it !in declaredChecks) error(ReportDefinitionErrorCode.CHECK_NOT_FOUND, itemPath, it) }
        }
      }.orEmpty()
      return ReportWarningsPanel(title, checks.distinct())
    }

    // endregion

    // region charts

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun chartPanel(type: ReportPanelType, map: ObjectNode, path: Path, title: String): ReportPanel? {
      checkKeys(map, path, CHART_KEYS + type.key + (if (type == ReportPanelType.BAR) setOf("horizontal") else emptySet()))
      val panelQuery = map.get("query")?.let { nonBlankText(it, path.key("query"), "query") }
      val defaultStyle = map.get("style")?.let { style(it, path.key("style")) }
        ?: if (type == ReportPanelType.BAR) ReportSeriesStyle.COLUMN else ReportSeriesStyle.LINE
      val stack = map.get("stack")?.let { bool(it, path.key("stack")) } ?: false
      val zoom = map.get("zoom")?.let { bool(it, path.key("zoom")) } ?: (type == ReportPanelType.TIMESERIES)
      val horizontal = map.get("horizontal")?.let { bool(it, path.key("horizontal")) } ?: false

      val series = mutableListOf<SeriesAt>()
      val seriesNode = map.get("series")
      if (seriesNode != null) {
        listOf("field", "name", "axis").filter { map.has(it) }.forEach { error(ReportDefinitionErrorCode.KEY_CONFLICT, path.key(it), it, "series") }
        list(seriesNode, path.key("series"))?.forEachIndexed { i, node ->
          val itemPath = path.key("series").index(i)
          seriesItem(node, itemPath, panelQuery, defaultStyle)?.let { item ->
            series += SeriesAt(item, itemPath, if ((node as? ObjectNode)?.has("query") == true) itemPath.key("query") else path.key("query"))
          }
        }
      } else if (panelQuery == null) {
        error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "query")
      } else {
        val name = map.get("name")?.let { text(it, path.key("name")) }?.trim()?.takeIf { it.isNotEmpty() }
        val axis = map.get("axis")?.let { axis(it, path.key("axis")) } ?: ReportAxis.LEFT
        val field = map.get("field")?.let { nonBlankText(it, path.key("field"), "field") }
        if (field != null) {
          series += SeriesAt(ReportSeries(panelQuery, field, name, defaultStyle, axis), path.key("field"), path.key("query"))
        } else if (!map.has("field")) {
          // Without a field, an aggregate query contributes one series per aggregated value.
          val info = declaredQuery(panelQuery, path.key("query"))
          val aggregate = info?.query?.aggregate
          if (aggregate != null) {
            aggregate.values.forEach { series += SeriesAt(ReportSeries(panelQuery, it.name, null, defaultStyle, axis), path.key("query"), path.key("query")) }
          } else if (info != null) {
            error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "field")
          }
        }
      }
      validateSeries(type, series)
      return ReportChartPanel(type, title, series.map { it.series }, stack, zoom, horizontal)
    }

    private fun seriesItem(node: JsonNode, path: Path, panelQuery: String?, defaultStyle: ReportSeriesStyle): ReportSeries? {
      val map = obj(node, path) ?: return null
      checkKeys(map, path, SERIES_KEYS)
      val ownQuery = map.get("query")?.let { nonBlankText(it, path.key("query"), "query") }
      if (!map.has("query") && panelQuery == null) error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "query")
      val query = if (map.has("query")) ownQuery else panelQuery
      val field = required(map, "field", path)?.let { nonBlankText(it, path.key("field"), "field") }
      val name = map.get("name")?.let { text(it, path.key("name")) }?.trim()?.takeIf { it.isNotEmpty() }
      val style = map.get("style")?.let { style(it, path.key("style")) } ?: defaultStyle
      val axis = map.get("axis")?.let { axis(it, path.key("axis")) } ?: ReportAxis.LEFT
      if (query == null || field == null) return null
      return ReportSeries(query, field, name, style, axis)
    }

    private fun style(node: JsonNode, path: Path): ReportSeriesStyle? = keyed(node, path, ReportSeriesStyle.entries) { it.key }

    private fun axis(node: JsonNode, path: Path): ReportAxis? = keyed(node, path, ReportAxis.entries) { it.key }

    /**
     * The x axis a query offers [type], null if none: timeseries - its bucket or (plain query) its time field; bar - its group, else its bucket.
     * A resampled query offers the resampling bucket.
     */
    private fun xAxisOf(type: ReportPanelType, info: QueryInfo): String? {
      info.resample?.let { return "bucket:${it.bucket}:${it.periodStart}" }
      val query = info.query
      val aggregate = query.aggregate
      val bucket = aggregate?.bucket?.let { "bucket:$it:${aggregate.periodStart}" }
      return when (type) {
        ReportPanelType.TIMESERIES -> if (aggregate == null) "time".takeIf { query.timeField != null } else bucket
        else -> if (aggregate?.groupBy != null) "group" else bucket
      }
    }

    private fun validateSeries(type: ReportPanelType, series: List<SeriesAt>) {
      var xAxis: String? = null
      val axisUnits = mutableMapOf<ReportAxis, String>()
      val reportedQueries = mutableSetOf<String>()
      series.forEach { at ->
        val item = at.series
        if (!queryInfos.containsKey(item.query)) {
          if (reportedQueries.add(item.query)) error(ReportDefinitionErrorCode.QUERY_NOT_FOUND, at.queryPath, item.query)
          return@forEach
        }
        val info = queryInfos[item.query] ?: return@forEach
        val x = xAxisOf(type, info)
        when {
          x == null -> if (reportedQueries.add(item.query)) error(ReportDefinitionErrorCode.CHART_X_MISSING, at.queryPath, item.query)
          xAxis == null -> xAxis = x
          x != xAxis -> error(ReportDefinitionErrorCode.SERIES_X_MISMATCH, at.queryPath, item.query)
        }
        val columns = info.columns ?: return@forEach
        val column = columns[item.field] ?: return@forEach error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, at.path, item.field, item.query)
        if (column.type == null) return@forEach
        if (!column.numeric) return@forEach error(ReportDefinitionErrorCode.FIELD_NOT_NUMERIC, at.path, item.field)
        val family = column.unit?.family?.name ?: "-"
        if (axisUnits.getOrPut(item.axis) { family } != family) error(ReportDefinitionErrorCode.AXIS_UNIT_MISMATCH, at.path, item.field)
      }
    }

    // endregion

    // region stat

    @Suppress("CyclomaticComplexMethod")
    private fun statPanel(map: ObjectNode, path: Path, title: String): ReportPanel? {
      checkKeys(map, path, STAT_KEYS + ReportPanelType.STAT.key)
      if (map.has("config")) return configStatPanel(map, path, title)
      val query = required(map, "query", path)?.let { nonBlankText(it, path.key("query"), "query") }
      val explicitField = map.get("field")?.let { nonBlankText(it, path.key("field"), "field") }
      val reduce = map.get("reduce")?.let { keyed(it, path.key("reduce"), ReportReduce.entries) { r -> r.key } } ?: ReportReduce.LAST
      val compare = map.get("compare")?.let { node ->
        val raw = text(node, path.key("compare"))?.trim() ?: return@let false
        (raw == COMPARE_PREVIOUS_PERIOD).also { if (!it) error(ReportDefinitionErrorCode.INVALID_VALUE, path.key("compare"), raw, COMPARE_PREVIOUS_PERIOD) }
      } ?: false
      val info = query?.let { declaredQuery(it, path.key("query")) }
      val field = explicitField ?: info?.query?.aggregate?.values?.singleOrNull()?.name
      if (field == null && !map.has("field") && reduce != ReportReduce.COUNT && info != null) error(ReportDefinitionErrorCode.REQUIRED_KEY_MISSING, path, "field")
      val column = if (field != null && query != null) {
        info?.columns?.let { columns -> columns[field] ?: null.also { error(ReportDefinitionErrorCode.COLUMN_NOT_IN_QUERY, path.key("field"), field, query) } }
      } else {
        null
      }
      if (column?.type != null && reduce != ReportReduce.COUNT && !column.numeric) error(ReportDefinitionErrorCode.FIELD_NOT_NUMERIC, path.key("field"), field!!)
      if (compare && info != null && !closedRange(info.query)) error(ReportDefinitionErrorCode.COMPARE_NEEDS_RANGE, path.key("compare"), info.query.name)
      val thresholdColumn = column?.takeIf { it.type != null && reduce != ReportReduce.COUNT }
      val thresholds = map.get("thresholds")?.let { node ->
        list(node, path.key("thresholds"))?.mapIndexedNotNull { i, item ->
          threshold(item, path.key("thresholds").index(i), thresholdColumn != null || reduce == ReportReduce.COUNT, thresholdColumn?.unit)
        }
      }.orEmpty()
      return query?.let { ReportStatPanel(title, it, field, reduce, compare, thresholds) }
    }

    /** `{ stat: Jahresziel, config: zielKm }`: a numeric configuration field shown as a constant, without query. */
    private fun configStatPanel(map: ObjectNode, path: Path, title: String): ReportPanel? {
      STAT_QUERY_KEYS.filter { map.has(it) }.forEach { error(ReportDefinitionErrorCode.KEY_CONFLICT, path.key(it), it, "config") }
      val name = nonBlankText(map.get("config"), path.key("config"), "config")?.removePrefix(ReportFields.CONFIG_PREFIX)
      val field = name?.let { configField(ReportFields.CONFIG_PREFIX + it, path.key("config")) }
      if (field != null && !ReportFields.isNumericType(field.type)) error(ReportDefinitionErrorCode.FIELD_NOT_NUMERIC, path.key("config"), name)
      val unit = field?.unit
      val thresholds = map.get("thresholds")?.let { node ->
        list(node, path.key("thresholds"))?.mapIndexedNotNull { i, item -> threshold(item, path.key("thresholds").index(i), field != null, unit) }
      }.orEmpty()
      return name?.let { ReportStatPanel(title, null, null, thresholds = thresholds, config = it) }
    }

    private fun closedRange(query: ReportQuery): Boolean = when (val range = query.range) {
      is ReportTimeRange.Relative -> true
      is ReportTimeRange.Fixed -> range.from != null && range.to != null
      is ReportTimeRange.Variable -> true
      null -> false
    }

    /** A threshold; with [checkNumbers] its value must be a number in [unit] (or a numeric `config.<field>` of a fitting unit). */
    private fun threshold(node: JsonNode, path: Path, checkNumbers: Boolean, unit: PropertyUnit?): ReportThreshold? {
      val map = obj(node, path) ?: return null
      checkKeys(map, path, THRESHOLD_KEYS)
      val value = required(map, "value", path)?.let { nonBlankText(it, path.key("value"), "value") }
      val color = required(map, "color", path)?.let { nonBlankText(it, path.key("color"), "color") }
      if (value != null && ReportFields.configPath(value) != null) {
        checkConfigNumber(value, path.key("value"), unit)
      } else if (value != null && checkNumbers && ReportValues.parseNumber(value, unit) == null) {
        error(ReportDefinitionErrorCode.INVALID_NUMBER, path.key("value"), value)
      }
      if (color != null && !ReportValues.isColor(color)) error(ReportDefinitionErrorCode.INVALID_COLOR, path.key("color"), color, ReportValues.COLOR_NAMES.joinToString(", "))
      if (value == null || color == null) return null
      return ReportThreshold(value, color)
    }

    // endregion
  }
}
