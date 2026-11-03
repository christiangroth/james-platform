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
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The small, safe expression language shared by the platform's declarative calculations (report `calc` steps and value rules; meant to replace
 * Kotlin scripts in computed properties and smart defaults as well). Pure Kotlin - no scripting engine, no reflection, no platform access: an
 * expression can only read the values its [ExpressionContext] hands out and compute a value from them, so it is safe and fast (also in a native
 * image).
 *
 * Syntax:
 * - literals: numbers (`1.5`), texts (`"krank"` or `'krank'`, `\` escapes the quote), `true`, `false`, `null` (no value);
 * - field names (letters incl. umlauts, digits, `_` and `.`, e.g. `gewicht`, `rad.name`, `garantiert.ablauf`) and `config.<field>`;
 * - operators by precedence (lowest first): `or`/`||`, `and`/`&&`, `not`/`!`, comparisons `== != < <= > >=` (not chained), `+ -`, `* / %`,
 *   unary `-`, `^` (power, right-associative, binding tighter than a unary minus on its left: `-2^2` = -4), parentheses;
 * - the functions in [FUNCTIONS].
 *
 * Semantics: arithmetic works on numbers, yes/no values count as 1/0. A missing operand, a division by zero or a non-finite result give no value
 * (null); comparisons with a missing operand give no value too, except `==`/`!=` (no value equals only no value). `and`, `or`, `not` and the
 * condition of `if` treat no value as "no", `coalesce`, `if` and `case` provide defaults.
 */
internal class Expression private constructor(val source: String, private val root: Node) {

  /** The names the expression reads: fields and `config.<field>` references. */
  val identifiers: Set<String> by lazy { buildSet { collect(root, this) } }

  /** Evaluates the expression against [context]; null if it has no value. */
  fun evaluate(context: ExpressionContext): ExpressionValue? = Evaluator(context).eval(root)

  /** Evaluates the expression as number (yes/no as 1/0); null if it has no value or none that is a number. */
  fun evaluateNumber(context: ExpressionContext): Double? = toNumber(evaluate(context))?.takeIf { it.isFinite() }

  /**
   * Infers the result type with [typeOf] answering an identifier's type (null: unknown) and collects type problems, e.g. a text in arithmetic or
   * a date compared with a number. The result type is null when it cannot be told (unknown fields, `null`, problems).
   */
  fun check(typeOf: (String) -> ExpressionType?): TypeCheck {
    val problems = mutableListOf<ExpressionProblem>()
    val type = TypeChecker(typeOf, problems).type(root)
    return TypeCheck(type, problems)
  }

  class TypeCheck(val type: ExpressionType?, val problems: List<ExpressionProblem>)

  class ParseException(val position: Int, val detail: String, val unknownFunction: String? = null) : RuntimeException(detail)

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
      "date" to 1..1,
      "time" to 1..1,
    )

    private val KEYWORDS = setOf("and", "or", "not", "true", "false", "null")
    private val ARITHMETIC = setOf("+", "-", "*", "/", "%", "^")
    private val COMPARISONS = listOf("==", "!=", "<=", ">=", "<", ">")
    private const val MAX_DECIMALS = 10
    private const val MINUTES_PER_DAY = 24 * 60
    private const val SECONDS_PER_MINUTE = 60.0

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

    private fun collect(node: Node, into: MutableSet<String>) {
      when (node) {
        is Node.Literal -> Unit
        is Node.Ident -> into += node.name
        is Node.Unary -> collect(node.operand, into)
        is Node.Binary -> {
          collect(node.left, into)
          collect(node.right, into)
        }
        is Node.Call -> node.args.forEach { collect(it, into) }
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
        else -> numeric(node.function, values.map { toNumber(it) ?: return null })
      }
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

  private class TypeChecker(private val typeOf: (String) -> ExpressionType?, private val problems: MutableList<ExpressionProblem>) {

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
        "today" -> ExpressionType.DATE
        "minutes" -> argumentsOf(name, types, TIMES_OF_DAY).let { ExpressionType.NUMBER }
        "days", "weekday", "day", "month", "year" -> argumentsOf(name, types, DATES).let { ExpressionType.NUMBER }
        "date" -> argumentsOf(name, types, DATES).let { ExpressionType.DATE }
        "time" -> argumentsOf(name, types, TIMES_OF_DAY).let { ExpressionType.TIME }
        else -> argumentsOf(name, types, NUMBERS).let { ExpressionType.NUMBER }
      }
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
