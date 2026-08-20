package de.chrgroth.james.platform.domain.app

import kotlin.random.Random

/**
 * Generates random strings for a (bounded subset of) regular expression `Pattern` constraint, for use by [TestDataGeneratorService].
 * Supports literals, `.`, character classes (`[...]`/`[^...]`, including ranges), predefined classes (`\d \D \w \W \s \S`), groups
 * `(...)`/`(?:...)`, alternation `|`, and quantifiers `* + ? {n} {n,} {n,m}`. Anchors `^`/`$` are ignored (patterns are meant to be
 * matched in full via [java.util.regex.Matcher.matches] semantics anyway). Unbounded quantifiers (`*`, `+`, `{n,}`) are capped at
 * [UNBOUNDED_REPEAT_CAP] repeats to keep generated values bounded.
 *
 * This is not a general regex engine - patterns using backreferences, lookaround, or other unsupported syntax produce best-effort
 * output that the caller must still verify against the real [Regex];
 * [TestDataGeneratorService] retries a bounded number of times and fails generation cleanly if no candidate ever matches.
 */
internal object TestDataPatternGenerator {

  private const val UNBOUNDED_REPEAT_CAP = 8
  private val SAFE_ANY_CHARS = (('a'..'z') + ('A'..'Z') + ('0'..'9')).toList()
  private val SAFE_WORD_CHARS = SAFE_ANY_CHARS + '_'
  private val SAFE_DIGIT_CHARS = ('0'..'9').toList()
  private val SAFE_NON_WORD_CHARS = listOf(' ', '-', '.', '@')

  fun generate(pattern: String, rng: Random): String {
    val stripped = pattern.removePrefix("^").removeSuffix("$")
    val (node, _) = parseAlternation(stripped, 0)
    return node.render(rng)
  }

  private sealed interface RxNode {
    fun render(rng: Random): String
  }

  private data class RxLiteral(val text: String) : RxNode {
    override fun render(rng: Random) = text
  }

  private data class RxCharSet(val chars: List<Char>) : RxNode {
    override fun render(rng: Random) = if (chars.isEmpty()) "" else chars[rng.nextInt(chars.size)].toString()
  }

  private data class RxConcat(val nodes: List<RxNode>) : RxNode {
    override fun render(rng: Random) = nodes.joinToString("") { it.render(rng) }
  }

  private data class RxAlternation(val options: List<RxNode>) : RxNode {
    override fun render(rng: Random) = options[rng.nextInt(options.size)].render(rng)
  }

  private data class RxRepeat(val node: RxNode, val min: Int, val max: Int) : RxNode {
    override fun render(rng: Random): String {
      val count = if (max <= min) min else min + rng.nextInt(max - min + 1)
      return (0 until count).joinToString("") { node.render(rng) }
    }
  }

  /** Parses a top-level `|`-separated alternation, starting at [start], until end of string or an enclosing `)`. */
  private fun parseAlternation(pattern: String, start: Int): Pair<RxNode, Int> {
    val options = mutableListOf<RxNode>()
    var pos = start
    while (true) {
      val (seq, next) = parseConcat(pattern, pos)
      options += seq
      pos = next
      if (pos < pattern.length && pattern[pos] == '|') {
        pos++
      } else {
        break
      }
    }
    return (if (options.size == 1) options[0] else RxAlternation(options)) to pos
  }

  private fun parseConcat(pattern: String, start: Int): Pair<RxNode, Int> {
    val nodes = mutableListOf<RxNode>()
    var pos = start
    while (pos < pattern.length && pattern[pos] != '|' && pattern[pos] != ')') {
      val (atom, next) = parseAtom(pattern, pos)
      val (quantified, afterQuant) = parseQuantifier(atom, pattern, next)
      nodes += quantified
      pos = afterQuant
    }
    return RxConcat(nodes) to pos
  }

  private fun parseAtom(pattern: String, pos: Int): Pair<RxNode, Int> {
    val c = pattern[pos]
    return when (c) {
      '(' -> {
        var next = pos + 1
        if (pattern.startsWith("?:", next)) next += 2
        val (inner, afterInner) = parseAlternation(pattern, next)
        val closing = if (afterInner < pattern.length && pattern[afterInner] == ')') afterInner + 1 else afterInner
        inner to closing
      }
      '[' -> parseCharClass(pattern, pos)
      '.' -> RxCharSet(SAFE_ANY_CHARS) to pos + 1
      '\\' -> {
        val next = pattern.getOrNull(pos + 1)
        val node: RxNode = when (next) {
          'd' -> RxCharSet(SAFE_DIGIT_CHARS)
          'D' -> RxCharSet(SAFE_ANY_CHARS.filterNot { it.isDigit() })
          'w' -> RxCharSet(SAFE_WORD_CHARS)
          'W' -> RxCharSet(SAFE_NON_WORD_CHARS)
          's' -> RxCharSet(listOf(' '))
          'S' -> RxCharSet(SAFE_ANY_CHARS)
          null -> RxLiteral("")
          else -> RxLiteral(next.toString())
        }
        node to pos + 2
      }
      else -> RxLiteral(c.toString()) to pos + 1
    }
  }

  private fun parseCharClass(pattern: String, pos: Int): Pair<RxNode, Int> {
    var i = pos + 1
    val negate = pattern.getOrNull(i) == '^'
    if (negate) i++
    val included = mutableListOf<Char>()
    while (i < pattern.length && pattern[i] != ']') {
      val c = pattern[i]
      if (c == '\\' && i + 1 < pattern.length) {
        included += pattern[i + 1]
        i += 2
        continue
      }
      if (i + 2 < pattern.length && pattern[i + 1] == '-' && pattern[i + 2] != ']') {
        included += (c..pattern[i + 2]).toList()
        i += 3
        continue
      }
      included += c
      i++
    }
    val end = if (i < pattern.length) i + 1 else i
    val safeUniverse = SAFE_ANY_CHARS + SAFE_NON_WORD_CHARS
    val chars = if (negate) safeUniverse.filterNot { it in included } else included.filter { it in safeUniverse }
    return RxCharSet(chars.ifEmpty { SAFE_ANY_CHARS }) to end
  }

  private fun parseQuantifier(atom: RxNode, pattern: String, pos: Int): Pair<RxNode, Int> {
    if (pos >= pattern.length) return atom to pos
    return when (pattern[pos]) {
      '*' -> RxRepeat(atom, 0, UNBOUNDED_REPEAT_CAP) to pos + 1
      '+' -> RxRepeat(atom, 1, UNBOUNDED_REPEAT_CAP) to pos + 1
      '?' -> RxRepeat(atom, 0, 1) to pos + 1
      '{' -> {
        val close = pattern.indexOf('}', pos)
        if (close < 0) return atom to pos
        val spec = pattern.substring(pos + 1, close)
        val parts = spec.split(",")
        val min = parts[0].toIntOrNull() ?: 0
        val max = when {
          parts.size == 1 -> min
          parts[1].isBlank() -> min + UNBOUNDED_REPEAT_CAP
          else -> parts[1].toIntOrNull() ?: min
        }
        RxRepeat(atom, min, max) to close + 1
      }
      else -> atom to pos
    }
  }
}
