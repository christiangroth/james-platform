package de.chrgroth.james.platform.domain.report

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The small, safe arithmetic expression language of calculated report fields (`calc`): numbers, field names (letters incl. umlauts, digits,
 * `_` and `.`, e.g. `gewicht`, `band.preis`, `config.groesse`), `+ - * / %`, `^` (power, right-associative), unary minus, parentheses and a
 * fixed set of [FUNCTIONS]. There are no assignments, loops, strings or calls into the platform, so evaluating an expression cannot do
 * anything but compute a number. Values are doubles; a missing operand, a division by zero or a non-finite result yields null (only
 * `coalesce` handles nulls itself).
 */
internal class ReportExpression private constructor(val source: String, private val root: Node) {

  /** The field names (incl. `config.<field>`) the expression reads. */
  val identifiers: Set<String> by lazy { buildSet { collect(root, this) } }

  /** Evaluates the expression, [valueOf] answering a field's value (null if missing or not numeric). */
  fun evaluate(valueOf: (String) -> Double?): Double? = eval(root, valueOf)?.takeIf { it.isFinite() }

  class ParseException(val position: Int, val detail: String, val unknownFunction: String? = null) : RuntimeException(detail)

  private sealed interface Node {
    data class Num(val value: Double) : Node
    data class Ident(val name: String) : Node
    data class Neg(val operand: Node) : Node
    data class Binary(val op: Char, val left: Node, val right: Node) : Node
    data class Call(val function: String, val args: List<Node>) : Node
  }

  companion object {
    const val MAX_LENGTH = 500

    /** Function name to allowed argument counts (null: any number from 1). */
    val FUNCTIONS: Map<String, IntRange?> = linkedMapOf(
      "abs" to 1..1,
      "sqrt" to 1..1,
      "round" to 1..2,
      "floor" to 1..1,
      "ceil" to 1..1,
      "min" to null,
      "max" to null,
      "coalesce" to null,
    )

    private const val MAX_DECIMALS = 10

    /** Parses [source]; throws [ParseException] with the 1-based position of the problem. */
    fun parse(source: String): ReportExpression {
      if (source.length > MAX_LENGTH) throw ParseException(MAX_LENGTH, "longer than $MAX_LENGTH characters")
      val parser = Parser(source)
      val root = parser.expression()
      parser.expectEnd()
      return ReportExpression(source, root)
    }

    private fun collect(node: Node, into: MutableSet<String>) {
      when (node) {
        is Node.Num -> Unit
        is Node.Ident -> into += node.name
        is Node.Neg -> collect(node.operand, into)
        is Node.Binary -> {
          collect(node.left, into)
          collect(node.right, into)
        }
        is Node.Call -> node.args.forEach { collect(it, into) }
      }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun eval(node: Node, valueOf: (String) -> Double?): Double? = when (node) {
      is Node.Num -> node.value
      is Node.Ident -> valueOf(node.name)
      is Node.Neg -> eval(node.operand, valueOf)?.let { -it }
      is Node.Binary -> {
        val left = eval(node.left, valueOf)
        val right = eval(node.right, valueOf)
        if (left == null || right == null) {
          null
        } else {
          when (node.op) {
            '+' -> left + right
            '-' -> left - right
            '*' -> left * right
            '/' -> if (right == 0.0) null else left / right
            '%' -> if (right == 0.0) null else left % right
            else -> left.pow(right)
          }
        }
      }
      is Node.Call -> call(node, valueOf)
    }

    private fun call(node: Node.Call, valueOf: (String) -> Double?): Double? {
      if (node.function == "coalesce") return node.args.firstNotNullOfOrNull { eval(it, valueOf) }
      val args = node.args.map { eval(it, valueOf) ?: return null }
      return when (node.function) {
        "abs" -> abs(args[0])
        "sqrt" -> if (args[0] < 0) null else sqrt(args[0])
        "round" -> round(args[0], args.getOrNull(1))
        "floor" -> floor(args[0])
        "ceil" -> ceil(args[0])
        "min" -> args.min()
        "max" -> args.max()
        else -> null
      }
    }

    private fun round(value: Double, decimals: Double?): Double? {
      if (!value.isFinite()) return null
      val scale = (decimals ?: 0.0).toInt().coerceIn(0, MAX_DECIMALS)
      return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toDouble()
    }
  }

  /** Recursive descent: expression := term (('+'|'-') term)*, term := unary (('*'|'/'|'%') unary)*, unary := '-' unary | power. */
  private class Parser(private val text: String) {
    private var pos = 0

    fun expression(): Node {
      var node = term()
      while (true) {
        val op = peekOperator("+-") ?: return node
        pos++
        node = Node.Binary(op, node, term())
      }
    }

    fun expectEnd() {
      skipSpaces()
      if (pos < text.length) fail("unexpected '${text[pos]}'")
    }

    private fun term(): Node {
      var node = unary()
      while (true) {
        val op = peekOperator("*/%") ?: return node
        pos++
        node = Node.Binary(op, node, unary())
      }
    }

    private fun unary(): Node {
      if (peekOperator("-") != null) {
        pos++
        return Node.Neg(unary())
      }
      return power()
    }

    /** power := primary ('^' unary)?; right-associative and binding tighter than unary minus on its left (`-2^2` = -4). */
    private fun power(): Node {
      val base = primary()
      if (peekOperator("^") == null) return base
      pos++
      return Node.Binary('^', base, unary())
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
        isIdentStart(c) -> identOrCall()
        else -> fail("unexpected '$c'")
      }
    }

    private fun number(): Node {
      val start = pos
      while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
      val raw = text.substring(start, pos)
      val value = raw.toDoubleOrNull() ?: failAt(start, "invalid number '$raw'")
      return Node.Num(value)
    }

    private fun identOrCall(): Node {
      val start = pos
      while (pos < text.length && (isIdentStart(text[pos]) || text[pos].isDigit() || text[pos] == '.')) pos++
      val name = text.substring(start, pos)
      if (name.endsWith('.')) failAt(start, "invalid name '$name'")
      skipSpaces()
      if (pos >= text.length || text[pos] != '(') return Node.Ident(name)
      val arity = FUNCTIONS[name]
      if (!FUNCTIONS.containsKey(name)) throw ParseException(start + 1, "unknown function '$name'", name)
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
      val count = args.size
      if (count == 0 || (arity != null && count !in arity)) failAt(start, "wrong number of arguments for '$name'")
      return Node.Call(name, args)
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
}
