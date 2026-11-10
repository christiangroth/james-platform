package de.chrgroth.james.platform.domain.expression

import de.chrgroth.james.platform.domain.expression.ExpressionValue.Companion.number
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Companion.toDate
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Companion.toDateTime
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Companion.toNumber
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Companion.toText
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Companion.toTime
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Companion.truthy
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The small, safe expression language shared by the platform's declarative calculations: report `calc` steps and value rules, computed properties
 * and smart defaults (ADR 0029, it replaced Kotlin scripting there). Pure Kotlin - no scripting engine, no reflection, no platform access: an
 * expression can only read the values its [ExpressionContext] hands out and compute a value from them, so it is safe and fast (also in a native
 * image).
 *
 * Syntax:
 * - literals: numbers (`1.5`), texts (`"krank"` or `'krank'`, `\` escapes the quote), `true`, `false`, `null` (no value);
 * - field names (letters incl. umlauts, digits, `_` and `.`, e.g. `gewicht`, `rad.name`, `garantiert.ablauf`) and `config.<field>`;
 * - operators by precedence (lowest first): `or`/`||`, `and`/`&&`, `not`/`!`, comparisons `== != < <= > >=` (not chained), `+ -`, `* / %`,
 *   unary `-`, `^` (power, right-associative, binding tighter than a unary minus on its left: `-2^2` = -4), parentheses;
 * - the functions in [FUNCTIONS]; [ENTRY_FUNCTIONS] (`last(field)`, `next_day(field)`) take a field name and read the entity's latest existing
 *   object, so only smart defaults offer them;
 * - the [LIST_FUNCTIONS] take a LIST field name (or a `config.<field>` LIST) and an expression evaluated per item (`sum_of(sessions, bahnlänge * bahnen)`):
 *   inside it, field names are the fields of a LIST of OBJECTs' item, or [ITEM] for the item value of a list of simple values; `config.<field>` as
 *   everywhere. `valid_at(list, key, at, value)` picks the item valid at a point: `key` and `value` are read per item, `at` outside the list
 *   (`valid_at(config.sollzeiten, ab, datum, sollzeit_pro_tag)`).
 *
 * Semantics: arithmetic works on numbers, yes/no values count as 1/0. A missing operand, a division by zero or a non-finite result give no value
 * (null); comparisons with a missing operand give no value too, except `==`/`!=` (no value equals only no value). `and`, `or`, `not` and the
 * condition of `if` treat no value as "no", `coalesce`, `if` and `case` provide defaults. List functions skip items whose expression has no
 * value: `sum_of` of no items is 0, `avg_of`/`min_of`/`max_of` of no items no value; `count_of(list[, condition])` counts the (matching) items;
 * `valid_at` gives `value` of the item with the greatest `key` not after `at` (the later item on equal keys), no value if there is none.
 */
internal class Expression private constructor(val source: String, private val root: Node) {

  /**
   * The names the expression reads: fields and `config.<field>` references (also the field names of [ENTRY_FUNCTIONS] and the LIST fields of
   * [LIST_FUNCTIONS], not the item fields read per item - see [itemIdentifiers]).
   */
  val identifiers: Set<String> by lazy { buildSet { collect(root, this, entries = true) } }

  /** Per LIST field of a [LIST_FUNCTIONS] call, the item field names its per-item expression reads (without `config.<field>` references). */
  val itemIdentifiers: Map<String, Set<String>> by lazy { LinkedHashMap<String, MutableSet<String>>().also { collectItems(root, it) } }

  /** The names the expression reads from the object itself, i.e. [identifiers] except those only used as field name of an entry function. */
  val directIdentifiers: Set<String> by lazy { buildSet { collect(root, this, entries = false) } }

  /** All functions the expression calls. */
  val functions: Set<String> by lazy { buildSet { collectFunctions(root, this) } }

  /** The [ENTRY_FUNCTIONS] the expression calls. */
  val entryFunctions: Set<String> get() = functions.filterTo(LinkedHashSet()) { it in ENTRY_FUNCTIONS }

  /** Evaluates the expression against [context]; null if it has no value. */
  fun evaluate(context: ExpressionContext): ExpressionValue? = Evaluator(context).eval(root)

  /** Evaluates the expression as number (yes/no as 1/0); null if it has no value or none that is a number. */
  fun evaluateNumber(context: ExpressionContext): Double? = toNumber(evaluate(context))?.takeIf { it.isFinite() }

  /**
   * Infers the result type with [typeOf] answering an identifier's type (null: unknown) and collects type problems, e.g. a text in arithmetic or
   * a date compared with a number. The result type is null when it cannot be told (unknown fields, `null`, problems).
   */
  fun check(typeOf: (String) -> ExpressionType?): TypeCheck = check(typeOf) { _, _ -> null }

  /** Like [check], with [itemTypeOf] answering the type of an item field read inside a list function (list field name, item field name). */
  fun check(typeOf: (String) -> ExpressionType?, itemTypeOf: (list: String, field: String) -> ExpressionType?): TypeCheck {
    val problems = mutableListOf<ExpressionProblem>()
    val type = TypeChecker(typeOf, itemTypeOf, problems).type(root)
    return TypeCheck(type, problems)
  }

  class TypeCheck(val type: ExpressionType?, val problems: List<ExpressionProblem>)

  /**
   * [unknownFunction]: the expression calls a function that does not exist; [fieldArgumentOf]: an [ENTRY_FUNCTIONS] function got something else
   * than a field name.
   */
  class ParseException(val position: Int, val detail: String, val unknownFunction: String? = null, val fieldArgumentOf: String? = null) : RuntimeException(detail)

  private sealed interface Node {
    data class Literal(val value: ExpressionValue?) : Node
    data class Ident(val name: String) : Node
    data class Unary(val op: String, val operand: Node) : Node
    data class Binary(val op: String, val left: Node, val right: Node) : Node
    data class Call(val function: String, val args: List<Node>) : Node
  }

  companion object {
    const val MAX_LENGTH = 500

    /** Prefix of a reference to the App configuration, e.g. `config.zielKm`. */
    const val CONFIG_PREFIX = "config."

    private const val ANY = Int.MAX_VALUE

    /** Function name to allowed argument counts. */
    val FUNCTIONS: Map<String, IntRange> = linkedMapOf(
      "abs" to 1..1,
      "sqrt" to 1..1,
      "round" to 1..2,
      "floor" to 1..1,
      "ceil" to 1..1,
      "min" to 1..ANY,
      "max" to 1..ANY,
      "coalesce" to 1..ANY,
      "if" to 3..3,
      "case" to 3..ANY,
      "present" to 1..1,
      "concat" to 1..ANY,
      "minutes" to 2..2,
      "days" to 2..2,
      "weekday" to 1..1,
      "day" to 1..1,
      "month" to 1..1,
      "year" to 1..1,
      "today" to 0..0,
      "now" to 0..0,
      "date" to 1..1,
      "time" to 1..1,
      "add_days" to 2..2,
      "end_of_month" to 1..1,
      "workdays" to 2..3,
      "last" to 1..1,
      "next_day" to 1..1,
      "sum_of" to 2..2,
      "avg_of" to 2..2,
      "min_of" to 2..2,
      "max_of" to 2..2,
      "count_of" to 1..2,
      "valid_at" to 4..4,
    )

    /** Functions taking a field name (not a value) and reading the entity's latest existing object - see [ExpressionContext.last]. */
    val ENTRY_FUNCTIONS: Set<String> = setOf("last", "next_day")

    /** Functions over the items of a LIST field: a LIST field name and an expression evaluated per item - see [ExpressionContext.items]. */
    val LIST_FUNCTIONS: Set<String> = setOf("sum_of", "avg_of", "min_of", "max_of", "count_of", "valid_at")

    /** The list function picking the item valid at a point: `valid_at(list, key, at, value)`. */
    private const val VALID_AT = "valid_at"

    /** The arguments of a [LIST_FUNCTIONS] call read per item (`at` of `valid_at` is read outside the list). */
    private fun itemArguments(node: Node.Call): List<Node> = if (node.function == VALID_AT) listOf(node.args[1], node.args[3]) else node.args.drop(1)

    /** The arguments of a [LIST_FUNCTIONS] call read outside the list, in the enclosing context. */
    private fun outerArguments(node: Node.Call): List<Node> = if (node.function == VALID_AT) listOf(node.args[2]) else emptyList()

    /** The item value inside a list function over a LIST of simple values, e.g. `sum_of(zeiten, item)`. */
    const val ITEM = "item"

    private val KEYWORDS = setOf("and", "or", "not", "true", "false", "null")
    private val ARITHMETIC = setOf("+", "-", "*", "/", "%", "^")
    private val COMPARISONS = listOf("==", "!=", "<=", ">=", "<", ">")
    private const val MAX_DECIMALS = 10
    private const val MINUTES_PER_DAY = 24 * 60
    private const val SECONDS_PER_MINUTE = 60.0
    private val HOLIDAY_SEPARATORS = Regex("[\\s,;\u001F]+")

    /** Parses [source]; throws [ParseException] with the 1-based position of the problem. */
    fun parse(source: String): Expression {
      if (source.length > MAX_LENGTH) throw ParseException(MAX_LENGTH, "longer than $MAX_LENGTH characters")
      val parser = Parser(source)
      val root = parser.expression()
      parser.expectEnd()
      return Expression(source, root)
    }

    /** The configuration field path of a `config.<field>` identifier, null for a field name. */
    fun configPath(identifier: String): String? = identifier.takeIf { it.startsWith(CONFIG_PREFIX) }?.removePrefix(CONFIG_PREFIX)?.takeIf { it.isNotBlank() }

    private fun collect(node: Node, into: MutableSet<String>, entries: Boolean) {
      when (node) {
        is Node.Literal -> Unit
        is Node.Ident -> into += node.name
        is Node.Unary -> collect(node.operand, into, entries)
        is Node.Binary -> {
          collect(node.left, into, entries)
          collect(node.right, into, entries)
        }
        is Node.Call -> when {
          node.function in LIST_FUNCTIONS -> {
            into += (node.args[0] as Node.Ident).name
            itemArguments(node).forEach { inner -> buildSet { collect(inner, this, entries) }.filterTo(into) { configPath(it) != null } }
            outerArguments(node).forEach { collect(it, into, entries) }
          }
          entries || node.function !in ENTRY_FUNCTIONS -> node.args.forEach { collect(it, into, entries) }
        }
      }
    }

    private fun collectItems(node: Node, into: MutableMap<String, MutableSet<String>>) {
      when (node) {
        is Node.Literal, is Node.Ident -> Unit
        is Node.Unary -> collectItems(node.operand, into)
        is Node.Binary -> {
          collectItems(node.left, into)
          collectItems(node.right, into)
        }
        is Node.Call -> if (node.function in LIST_FUNCTIONS) {
          val names = into.getOrPut((node.args[0] as Node.Ident).name) { LinkedHashSet() }
          itemArguments(node).forEach { inner -> buildSet { collect(inner, this, entries = true) }.filterTo(names) { configPath(it) == null } }
          outerArguments(node).forEach { collectItems(it, into) }
        } else {
          node.args.forEach { collectItems(it, into) }
        }
      }
    }

    private fun collectFunctions(node: Node, into: MutableSet<String>) {
      when (node) {
        is Node.Literal, is Node.Ident -> Unit
        is Node.Unary -> collectFunctions(node.operand, into)
        is Node.Binary -> {
          collectFunctions(node.left, into)
          collectFunctions(node.right, into)
        }
        is Node.Call -> {
          into += node.function
          node.args.forEach { collectFunctions(it, into) }
        }
      }
    }
  }

  // region evaluation

  private class Evaluator(private val context: ExpressionContext) {

    fun eval(node: Node): ExpressionValue? = when (node) {
      is Node.Literal -> node.value
      is Node.Ident -> configPath(node.name).let { path -> if (path != null) context.config(path) else context.field(node.name) }
      is Node.Unary -> if (node.op == "not") ExpressionValue.Bool(!truthy(eval(node.operand))) else toNumber(eval(node.operand))?.let { number(-it) }
      is Node.Binary -> binary(node)
      is Node.Call -> call(node)
    }

    private fun binary(node: Node.Binary): ExpressionValue? = when (node.op) {
      "and" -> ExpressionValue.Bool(truthy(eval(node.left)) && truthy(eval(node.right)))
      "or" -> ExpressionValue.Bool(truthy(eval(node.left)) || truthy(eval(node.right)))
      in ARITHMETIC -> arithmetic(node.op, toNumber(eval(node.left)), toNumber(eval(node.right)))
      else -> comparison(node.op, eval(node.left), eval(node.right))
    }

    private fun arithmetic(op: String, left: Double?, right: Double?): ExpressionValue? {
      if (left == null || right == null) return null
      return when (op) {
        "+" -> number(left + right)
        "-" -> number(left - right)
        "*" -> number(left * right)
        "/" -> if (right == 0.0) null else number(left / right)
        "%" -> if (right == 0.0) null else number(left % right)
        else -> number(left.pow(right))
      }
    }

    private fun comparison(op: String, left: ExpressionValue?, right: ExpressionValue?): ExpressionValue? {
      if (op == "==") return ExpressionValue.Bool(ExpressionValue.same(left, right))
      if (op == "!=") return ExpressionValue.Bool(!ExpressionValue.same(left, right))
      if (left == null || right == null) return null
      val order = ExpressionValue.compare(left, right) ?: return null
      return ExpressionValue.Bool(
        when (op) {
          "<" -> order < 0
          "<=" -> order <= 0
          ">" -> order > 0
          else -> order >= 0
        },
      )
    }

    @Suppress("CyclomaticComplexMethod")
    private fun call(node: Node.Call): ExpressionValue? {
      val args = node.args
      when (node.function) {
        "coalesce" -> return args.firstNotNullOfOrNull { eval(it) }
        "if" -> return if (truthy(eval(args[0]))) eval(args[1]) else eval(args[2])
        "case" -> return case(args)
        "present" -> return ExpressionValue.Bool(eval(args[0]).let { it != null && !(it is ExpressionValue.Text && it.value.isBlank()) })
        "concat" -> return ExpressionValue.Text(args.joinToString("") { toText(eval(it)).orEmpty() })
        "today" -> return ExpressionValue.Date(context.today)
        "now" -> return ExpressionValue.DateTime(context.now.truncatedTo(ChronoUnit.MINUTES))
        "last" -> return context.last(fieldName(args[0]))
        "next_day" -> return toDate(context.last(fieldName(args[0])))?.let { ExpressionValue.Date(it.plusDays(1)) }
        "workdays" -> return workdays(eval(args[0]), eval(args[1]), args.getOrNull(2)?.let { eval(it) })
        in LIST_FUNCTIONS -> return listFunction(node)
      }
      val values = args.map { eval(it) ?: return null }
      return when (node.function) {
        "minutes" -> minutes(values[0], values[1])
        "days" -> toDate(values[0])?.let { from -> toDate(values[1])?.let { to -> number(ChronoUnit.DAYS.between(from, to).toDouble()) } }
        "weekday" -> toDate(values[0])?.let { number(it.dayOfWeek.value.toDouble()) }
        "day" -> toDate(values[0])?.let { number(it.dayOfMonth.toDouble()) }
        "month" -> toDate(values[0])?.let { number(it.monthValue.toDouble()) }
        "year" -> toDate(values[0])?.let { number(it.year.toDouble()) }
        "date" -> toDate(values[0])?.let { ExpressionValue.Date(it) }
        "time" -> toTime(values[0])?.let { ExpressionValue.Time(it) }
        "add_days" -> toDate(values[0])?.let { date -> toNumber(values[1])?.let { ExpressionValue.Date(date.plusDays(it.toLong())) } }
        "end_of_month" -> toDate(values[0])?.let { ExpressionValue.Date(it.withDayOfMonth(it.lengthOfMonth())) }
        else -> numeric(node.function, values.map { toNumber(it) ?: return null })
      }
    }

    private fun fieldName(node: Node): String = (node as Node.Ident).name

    /** A [LIST_FUNCTIONS] call: its per-item expression evaluated in each item's context; no value if the field is no list. */
    private fun listFunction(node: Node.Call): ExpressionValue? {
      val list = fieldName(node.args[0])
      val configList = configPath(list)
      val items = (if (configList != null) context.configItems(configList) else context.items(list)) ?: return null
      if (node.function == VALID_AT) return validAt(items, node.args[1], eval(node.args[2]), node.args[3])
      val perItem = node.args.getOrNull(1)
      if (node.function == "count_of") {
        return number(items.count { item -> perItem == null || truthy(Evaluator(item).eval(perItem)) }.toDouble())
      }
      val values = items.mapNotNull { item -> perItem?.let { toNumber(Evaluator(item).eval(it)) } }
      return when (node.function) {
        "sum_of" -> number(values.sum())
        "avg_of" -> values.takeIf { it.isNotEmpty() }?.let { number(it.average()) }
        "min_of" -> values.minOrNull()?.let { number(it) }
        else -> values.maxOrNull()?.let { number(it) }
      }
    }

    /** `valid_at`: [value] of the item with the greatest [key] not after [at]; on equal keys the later item wins. */
    private fun validAt(items: List<ExpressionContext>, key: Node, at: ExpressionValue?, value: Node): ExpressionValue? {
      if (at == null) return null
      var best: Pair<ExpressionValue, ExpressionContext>? = null
      for (item in items) {
        val itemKey = Evaluator(item).eval(key) ?: continue
        val notAfter = ExpressionValue.compare(itemKey, at)?.let { it <= 0 } ?: false
        val later = best?.let { (bestKey, _) -> ExpressionValue.compare(itemKey, bestKey)?.let { it >= 0 } ?: false } ?: true
        if (notAfter && later) best = itemKey to item
      }
      return best?.let { (_, item) -> Evaluator(item).eval(value) }
    }

    /**
     * Mondays to Fridays from [from] to [to] (both inclusive; 0 if [to] lies before [from]) except the dates in [holidays]: a date, or a text
     * holding dates separated by commas, semicolons, spaces or the LIST separator (a configuration LIST of dates).
     */
    private fun workdays(from: ExpressionValue?, to: ExpressionValue?, holidays: ExpressionValue?): ExpressionValue? {
      val start = toDate(from) ?: return null
      val end = toDate(to) ?: return null
      val excluded = when (holidays) {
        null -> emptySet()
        is ExpressionValue.Text -> holidays.value.split(HOLIDAY_SEPARATORS).mapNotNullTo(HashSet()) { toDate(ExpressionValue.Text(it)) }
        else -> setOfNotNull(toDate(holidays))
      }
      var count = 0
      var day = start
      while (!day.isAfter(end)) {
        if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY && day !in excluded) count++
        day = day.plusDays(1)
      }
      return number(count.toDouble())
    }

    /** `case(x, k1, v1, k2, v2, ..., default)`: the value after the first key equal to x, else the default (no value without one). */
    private fun case(args: List<Node>): ExpressionValue? {
      val subject = eval(args[0])
      val pairs = args.drop(1)
      pairs.chunked(2).forEach { pair ->
        if (pair.size == 2 && ExpressionValue.same(subject, eval(pair[0]))) return eval(pair[1])
      }
      return if (pairs.size % 2 == 1) eval(pairs.last()) else null
    }

    /** Minutes from [from] to [to]: two times of day (an end before the start lies on the next day) or two date-times. */
    private fun minutes(from: ExpressionValue, to: ExpressionValue): ExpressionValue? {
      val startDateTime = toDateTime(from)
      val endDateTime = toDateTime(to)
      if (from !is ExpressionValue.Time && to !is ExpressionValue.Time && startDateTime != null && endDateTime != null) {
        return number(Duration.between(startDateTime, endDateTime).seconds / SECONDS_PER_MINUTE)
      }
      val start = toTime(from) ?: return null
      val end = toTime(to) ?: return null
      val minutes = Duration.between(start, end).seconds / SECONDS_PER_MINUTE
      return number(if (minutes < 0) minutes + MINUTES_PER_DAY else minutes)
    }

    private fun numeric(function: String, args: List<Double>): ExpressionValue? = when (function) {
      "abs" -> number(abs(args[0]))
      "sqrt" -> if (args[0] < 0) null else number(sqrt(args[0]))
      "round" -> round(args[0], args.getOrNull(1))
      "floor" -> number(floor(args[0]))
      "ceil" -> number(ceil(args[0]))
      "min" -> number(args.min())
      "max" -> number(args.max())
      else -> null
    }

    private fun round(value: Double, decimals: Double?): ExpressionValue? {
      val scale = (decimals ?: 0.0).toInt().coerceIn(0, MAX_DECIMALS)
      return number(BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toDouble())
    }
  }

  // endregion

  // region type check

  private class TypeChecker(
    private val typeOf: (String) -> ExpressionType?,
    private val itemTypeOf: (String, String) -> ExpressionType?,
    private val problems: MutableList<ExpressionProblem>,
  ) {

    fun type(node: Node): ExpressionType? = when (node) {
      is Node.Literal -> node.value?.type
      is Node.Ident -> typeOf(node.name)
      is Node.Unary -> if (node.op == "not") {
        type(node.operand)
        ExpressionType.BOOLEAN
      } else {
        numericOperand("-", type(node.operand))
        ExpressionType.NUMBER
      }
      is Node.Binary -> binary(node)
      is Node.Call -> call(node)
    }

    private fun binary(node: Node.Binary): ExpressionType? {
      val left = type(node.left)
      val right = type(node.right)
      return when (node.op) {
        "and", "or" -> ExpressionType.BOOLEAN
        in ARITHMETIC -> {
          numericOperand(node.op, left)
          numericOperand(node.op, right)
          ExpressionType.NUMBER
        }
        else -> {
          comparable(node.op, left, right)
          ExpressionType.BOOLEAN
        }
      }
    }

    private fun numericOperand(op: String, type: ExpressionType?) {
      if (type != null && !type.numeric) problems += ExpressionProblem(ExpressionProblem.Kind.OPERAND_TYPE, listOf(op, type.name))
    }

    private fun comparable(op: String, left: ExpressionType?, right: ExpressionType?) {
      if (left == null || right == null || left == right) return
      val ok = (left.numeric && right.numeric) ||
        (left.temporal && right.temporal) ||
        (left == ExpressionType.TEXT && right != ExpressionType.NUMBER && right != ExpressionType.BOOLEAN) ||
        (right == ExpressionType.TEXT && left != ExpressionType.NUMBER && left != ExpressionType.BOOLEAN) ||
        (setOf(left, right) == setOf(ExpressionType.TIME, ExpressionType.DATETIME))
      if (!ok) problems += ExpressionProblem(ExpressionProblem.Kind.INCOMPARABLE, listOf(op, left.name, right.name))
    }

    @Suppress("CyclomaticComplexMethod")
    private fun call(node: Node.Call): ExpressionType? {
      val name = node.function
      if (name in LIST_FUNCTIONS) return listCall(node)
      val types = node.args.map { type(it) }
      return when (name) {
        "coalesce" -> unify(name, types)
        "if" -> unify(name, types.drop(1))
        "case" -> {
          val pairs = types.drop(1)
          pairs.chunked(2).filter { it.size == 2 }.forEach { comparable(name, types[0], it[0]) }
          unify(name, pairs.chunked(2).map { it.last() })
        }
        "present" -> ExpressionType.BOOLEAN
        "concat" -> ExpressionType.TEXT
        "today", "now" -> if (name == "now") ExpressionType.DATETIME else ExpressionType.DATE
        "add_days" -> {
          argumentsOf(name, types.take(1), DATES)
          numberArgument(name, 2, types[1])
          ExpressionType.DATE
        }
        "end_of_month", "next_day" -> argumentsOf(name, types, DATES).let { ExpressionType.DATE }
        "last" -> types[0]
        "workdays" -> argumentsOf(name, types, DATES).let { ExpressionType.NUMBER }
        "minutes" -> argumentsOf(name, types, TIMES_OF_DAY).let { ExpressionType.NUMBER }
        "days", "weekday", "day", "month", "year" -> argumentsOf(name, types, DATES).let { ExpressionType.NUMBER }
        "date" -> argumentsOf(name, types, DATES).let { ExpressionType.DATE }
        "time" -> argumentsOf(name, types, TIMES_OF_DAY).let { ExpressionType.TIME }
        else -> argumentsOf(name, types, NUMBERS).let { ExpressionType.NUMBER }
      }
    }

    /**
     * A list function's per-item expression is typed by the item's fields; all but `valid_at` compute a number (sum_of etc. of numbers), `valid_at`
     * the type of its value, its key being comparable with the point it is valid at.
     */
    private fun listCall(node: Node.Call): ExpressionType? {
      val list = (node.args[0] as Node.Ident).name
      val itemChecker = TypeChecker({ name -> if (configPath(name) != null) typeOf(name) else itemTypeOf(list, name) }, itemTypeOf, problems)
      if (node.function == VALID_AT) {
        comparable(VALID_AT, itemChecker.type(node.args[1]), type(node.args[2]))
        return itemChecker.type(node.args[3])
      }
      val perItem = node.args.getOrNull(1) ?: return ExpressionType.NUMBER
      val type = itemChecker.type(perItem)
      if (node.function != "count_of") numberArgument(node.function, 2, type)
      return ExpressionType.NUMBER
    }

    private fun numberArgument(function: String, position: Int, type: ExpressionType?) {
      if (type != null && type !in NUMBERS) problems += ExpressionProblem(ExpressionProblem.Kind.ARGUMENT_TYPE, listOf(function, position.toString(), type.name))
    }

    private fun argumentsOf(function: String, types: List<ExpressionType?>, allowed: Set<ExpressionType>) {
      types.forEachIndexed { i, type ->
        if (type != null && type !in allowed) problems += ExpressionProblem(ExpressionProblem.Kind.ARGUMENT_TYPE, listOf(function, (i + 1).toString(), type.name))
      }
    }

    /** The common type of alternative results (`if`, `case`, `coalesce`): numbers with yes/no are numbers, dates with date-times date-times. */
    private fun unify(function: String, types: List<ExpressionType?>): ExpressionType? {
      val known = types.filterNotNull().distinct()
      return when {
        known.isEmpty() -> null
        known.size == 1 -> known.single()
        known.all { it.numeric } -> ExpressionType.NUMBER
        known.all { it.temporal } -> ExpressionType.DATETIME
        else -> null.also { problems += ExpressionProblem(ExpressionProblem.Kind.MIXED_TYPES, listOf(function, known.joinToString(", ") { it.name })) }
      }
    }

    companion object {
      private val NUMBERS = setOf(ExpressionType.NUMBER, ExpressionType.BOOLEAN)
      private val DATES = setOf(ExpressionType.DATE, ExpressionType.DATETIME, ExpressionType.TEXT)
      private val TIMES_OF_DAY = setOf(ExpressionType.TIME, ExpressionType.DATETIME, ExpressionType.TEXT)
    }
  }

  // endregion

  // region parser

  /**
   * Recursive descent over the precedence levels: or := and ('or' and)*, and := not ('and' not)*, not := 'not' not | comparison,
   * comparison := additive (op additive)?, additive := term (('+'|'-') term)*, term := unary (('*'|'/'|'%') unary)*, unary := '-' unary | power.
   */
  private class Parser(private val text: String) {
    private var pos = 0

    fun expression(): Node {
      var node = and()
      while (keyword("or") || symbol("||")) node = Node.Binary("or", node, and())
      return node
    }

    fun expectEnd() {
      skipSpaces()
      if (pos < text.length) fail("unexpected '${text[pos]}'")
    }

    private fun and(): Node {
      var node = not()
      while (keyword("and") || symbol("&&")) node = Node.Binary("and", node, not())
      return node
    }

    private fun not(): Node {
      skipSpaces()
      val bang = text.getOrNull(pos) == '!' && text.getOrNull(pos + 1) != '='
      if (bang) pos++
      return if (bang || keyword("not")) Node.Unary("not", not()) else comparison()
    }

    private fun comparison(): Node {
      val left = additive()
      val op = comparisonOperator() ?: return left
      val node = Node.Binary(op, left, additive())
      if (comparisonOperator() != null) failAt(pos - 1, "comparisons cannot be chained")
      return node
    }

    private fun comparisonOperator(): String? {
      skipSpaces()
      val op = COMPARISONS.find { text.startsWith(it, pos) }
      if (op != null) {
        pos += op.length
      } else if (text.getOrNull(pos) == '=') {
        fail("use '==' to compare")
      }
      return op
    }

    private fun additive(): Node {
      var node = term()
      while (true) {
        val op = peekOperator("+-") ?: return node
        pos++
        node = Node.Binary(op.toString(), node, term())
      }
    }

    private fun term(): Node {
      var node = unary()
      while (true) {
        val op = peekOperator("*/%") ?: return node
        pos++
        node = Node.Binary(op.toString(), node, unary())
      }
    }

    private fun unary(): Node {
      if (peekOperator("-") != null) {
        pos++
        return Node.Unary("-", unary())
      }
      return power()
    }

    /** power := primary ('^' unary)?; right-associative and binding tighter than unary minus on its left (`-2^2` = -4). */
    private fun power(): Node {
      val base = primary()
      if (peekOperator("^") == null) return base
      pos++
      return Node.Binary("^", base, unary())
    }

    private fun primary(): Node {
      skipSpaces()
      if (pos >= text.length) fail("unexpected end")
      val c = text[pos]
      return when {
        c == '(' -> {
          pos++
          val inner = expression()
          expect(')')
          inner
        }
        c.isDigit() || c == '.' -> number()
        c == '"' || c == '\'' -> string(c)
        isIdentStart(c) -> identOrCall()
        else -> fail("unexpected '$c'")
      }
    }

    private fun number(): Node {
      val start = pos
      while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
      val raw = text.substring(start, pos)
      val value = raw.toDoubleOrNull() ?: failAt(start, "invalid number '$raw'")
      return Node.Literal(ExpressionValue.Num(value))
    }

    private fun string(quote: Char): Node {
      val start = pos
      pos++
      val value = StringBuilder()
      while (pos < text.length && text[pos] != quote) {
        if (text[pos] == '\\' && pos + 1 < text.length) pos++
        value.append(text[pos])
        pos++
      }
      if (pos >= text.length) failAt(start, "unterminated text")
      pos++
      return Node.Literal(ExpressionValue.Text(value.toString()))
    }

    private fun readName(): String {
      val start = pos
      while (pos < text.length && (isIdentStart(text[pos]) || text[pos].isDigit() || text[pos] == '.')) pos++
      return text.substring(start, pos)
    }

    private fun identOrCall(): Node {
      val start = pos
      val name = readName()
      if (name.endsWith('.')) failAt(start, "invalid name '$name'")
      when (name) {
        "true" -> return Node.Literal(ExpressionValue.TRUE)
        "false" -> return Node.Literal(ExpressionValue.FALSE)
        "null" -> return Node.Literal(null)
        in KEYWORDS -> failAt(start, "unexpected '$name'")
      }
      skipSpaces()
      if (pos >= text.length || text[pos] != '(') return Node.Ident(name)
      val arity = FUNCTIONS[name] ?: throw ParseException(start + 1, "unknown function '$name'", name)
      pos++
      val args = mutableListOf<Node>()
      skipSpaces()
      if (pos < text.length && text[pos] == ')') {
        pos++
      } else {
        while (true) {
          args += expression()
          skipSpaces()
          if (pos < text.length && text[pos] == ',') {
            pos++
            continue
          }
          expect(')')
          break
        }
      }
      if (args.size !in arity) failAt(start, "wrong number of arguments for '$name'")
      val listArgument = args.take(1).takeIf { name in LIST_FUNCTIONS }.orEmpty()
      val entryArguments = args.takeIf { name in ENTRY_FUNCTIONS }.orEmpty()
      if (listArgument.any { it !is Node.Ident } || entryArguments.any { it !is Node.Ident || configPath(it.name) != null }) {
        throw ParseException(start + 1, "'$name' needs a field name", fieldArgumentOf = name)
      }
      return Node.Call(name, args)
    }

    /** Consumes the keyword [word] if it comes next as a whole word. */
    private fun keyword(word: String): Boolean {
      skipSpaces()
      val start = pos
      if (pos >= text.length || !isIdentStart(text[pos])) return false
      if (readName() == word) return true
      pos = start
      return false
    }

    private fun symbol(op: String): Boolean {
      skipSpaces()
      if (!text.startsWith(op, pos)) return false
      pos += op.length
      return true
    }

    private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_'

    private fun peekOperator(ops: String): Char? {
      skipSpaces()
      return text.getOrNull(pos)?.takeIf { it in ops }
    }

    private fun expect(c: Char) {
      skipSpaces()
      if (pos >= text.length || text[pos] != c) fail(if (pos >= text.length) "missing '$c'" else "expected '$c'")
      pos++
    }

    private fun skipSpaces() {
      while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun fail(detail: String): Nothing = failAt(pos, detail)

    private fun failAt(at: Int, detail: String): Nothing = throw ParseException(at + 1, detail)
  }

  // endregion
}

/**
 * A type problem of an expression; [args] by [kind]: OPERAND_TYPE (operator, type), ARGUMENT_TYPE (function, 1-based argument, type),
 * INCOMPARABLE (operator or `case`, left type, right type), MIXED_TYPES (function, types).
 */
internal data class ExpressionProblem(val kind: Kind, val args: List<String>) {
  enum class Kind { OPERAND_TYPE, ARGUMENT_TYPE, INCOMPARABLE, MIXED_TYPES }
}
