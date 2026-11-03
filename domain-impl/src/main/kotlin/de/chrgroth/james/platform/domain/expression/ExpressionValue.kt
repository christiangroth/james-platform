package de.chrgroth.james.platform.domain.expression

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** The static type of an expression value; an unknown type (e.g. a field whose type is not known) is null wherever types are inferred. */
internal enum class ExpressionType {
  NUMBER,
  TEXT,
  BOOLEAN,
  DATE,
  DATETIME,
  TIME,
  ;

  /** Numbers and yes/no values (true = 1, false = 0) take part in arithmetic. */
  val numeric: Boolean get() = this == NUMBER || this == BOOLEAN

  val temporal: Boolean get() = this == DATE || this == DATETIME
}

/**
 * A value of the expression language: a number, a text, a yes/no value, a date, a date-time or a time of day. "No value" (a missing field, a
 * division by zero, ...) is Kotlin null throughout.
 */
internal sealed interface ExpressionValue {
  val type: ExpressionType

  data class Num(val value: Double) : ExpressionValue {
    override val type: ExpressionType get() = ExpressionType.NUMBER
  }

  data class Text(val value: String) : ExpressionValue {
    override val type: ExpressionType get() = ExpressionType.TEXT
  }

  data class Bool(val value: Boolean) : ExpressionValue {
    override val type: ExpressionType get() = ExpressionType.BOOLEAN
  }

  data class Date(val value: LocalDate) : ExpressionValue {
    override val type: ExpressionType get() = ExpressionType.DATE
  }

  data class DateTime(val value: LocalDateTime) : ExpressionValue {
    override val type: ExpressionType get() = ExpressionType.DATETIME
  }

  data class Time(val value: LocalTime) : ExpressionValue {
    override val type: ExpressionType get() = ExpressionType.TIME
  }

  companion object {
    val TRUE = Bool(true)
    val FALSE = Bool(false)

    /** A number, or no value if it is not finite (overflow, NaN). */
    fun number(value: Double): ExpressionValue? = if (value.isFinite()) Num(value) else null

    /**
     * A raw stored value (numbers plain, yes/no as `true`/`false`, dates and times ISO) read as [type]; without a type it is guessed (number,
     * yes/no, date-time, date, time, else text). An empty or unreadable raw value is no value.
     */
    fun of(raw: String?, type: ExpressionType?): ExpressionValue? {
      if (raw.isNullOrEmpty()) return null
      return when (type) {
        null -> guess(raw)
        ExpressionType.NUMBER -> raw.toDoubleOrNull()?.let { number(it) }
        ExpressionType.TEXT -> Text(raw)
        ExpressionType.BOOLEAN -> raw.toBooleanStrictOrNull()?.let { Bool(it) }
        ExpressionType.DATE -> parseDate(raw)?.let { Date(it) }
        ExpressionType.DATETIME -> parseDateTime(raw)?.let { DateTime(it) }
        ExpressionType.TIME -> parseTime(raw)?.let { Time(it) }
      }
    }

    private fun guess(raw: String): ExpressionValue =
      raw.toDoubleOrNull()?.let { number(it) }
        ?: raw.toBooleanStrictOrNull()?.let { Bool(it) }
        ?: runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let { DateTime(it) }
        ?: runCatching { LocalDate.parse(raw) }.getOrNull()?.let { Date(it) }
        ?: runCatching { LocalTime.parse(raw) }.getOrNull()?.let { Time(it) }
        ?: Text(raw)

    private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw) }.getOrNull() ?: runCatching { LocalDateTime.parse(raw).toLocalDate() }.getOrNull()

    private fun parseDateTime(raw: String): LocalDateTime? =
      runCatching { LocalDateTime.parse(raw) }.getOrNull() ?: runCatching { LocalDate.parse(raw).atStartOfDay() }.getOrNull()

    private fun parseTime(raw: String): LocalTime? = runCatching { LocalTime.parse(raw) }.getOrNull() ?: runCatching { LocalDateTime.parse(raw).toLocalTime() }.getOrNull()

    // region coercions

    /** The value as number: numbers, yes/no as 1/0, texts holding a number; anything else is no number. */
    fun toNumber(value: ExpressionValue?): Double? = when (value) {
      is Num -> value.value
      is Bool -> if (value.value) 1.0 else 0.0
      is Text -> value.value.trim().toDoubleOrNull()
      else -> null
    }

    /** Whether the value counts as "yes": true, a number other than 0, a non-empty text, any date or time. No value counts as "no". */
    fun truthy(value: ExpressionValue?): Boolean = when (value) {
      null -> false
      is Bool -> value.value
      is Num -> value.value != 0.0
      is Text -> value.value.isNotEmpty()
      else -> true
    }

    /** The value as date: a date, the day of a date-time, a text holding one. */
    fun toDate(value: ExpressionValue?): LocalDate? = when (value) {
      is Date -> value.value
      is DateTime -> value.value.toLocalDate()
      is Text -> parseDate(value.value.trim())
      else -> null
    }

    /** The value as date-time: a date-time, a date (at midnight), a text holding one. */
    fun toDateTime(value: ExpressionValue?): LocalDateTime? = when (value) {
      is DateTime -> value.value
      is Date -> value.value.atStartOfDay()
      is Text -> parseDateTime(value.value.trim())
      else -> null
    }

    /** The value as time of day: a time, the time of a date-time, a text holding one. */
    fun toTime(value: ExpressionValue?): LocalTime? = when (value) {
      is Time -> value.value
      is DateTime -> value.value.toLocalTime()
      is Text -> parseTime(value.value.trim())
      else -> null
    }

    /** The value as text: numbers without trailing zeros (`2`, `2.5`), yes/no as `true`/`false`, dates and times ISO. */
    fun toText(value: ExpressionValue?): String? = when (value) {
      null -> null
      is Text -> value.value
      is Num -> BigDecimal.valueOf(value.value).stripTrailingZeros().toPlainString()
      is Bool -> value.value.toString()
      is Date -> value.value.toString()
      is DateTime -> value.value.toString()
      is Time -> value.value.toString()
    }

    /**
     * Compares two values: numbers and yes/no numerically, texts alphabetically, dates and date-times chronologically, times of day by time - a
     * text on one side is read like the other side (e.g. `datum >= "2026-01-01"`). Null if they cannot be compared.
     */
    @Suppress("CyclomaticComplexMethod")
    fun compare(a: ExpressionValue, b: ExpressionValue): Int? = when {
      a.type.numeric && b.type.numeric -> toNumber(a)!!.compareTo(toNumber(b)!!)
      a is Text && b is Text -> a.value.compareTo(b.value)
      a.type.numeric || b.type.numeric -> toNumber(a)?.let { x -> toNumber(b)?.let { y -> x.compareTo(y) } }
      a is Time || b is Time -> toTime(a)?.let { x -> toTime(b)?.let { y -> x.compareTo(y) } }
      a is Date && (b is Date || b is Text) -> toDate(b)?.let { a.value.compareTo(it) }
      b is Date && a is Text -> toDate(a)?.compareTo(b.value)
      else -> toDateTime(a)?.let { x -> toDateTime(b)?.let { y -> x.compareTo(y) } }
    }

    /** Equality as `==` sees it: no value equals only no value; values that cannot be compared are not equal. */
    fun same(a: ExpressionValue?, b: ExpressionValue?): Boolean = when {
      a == null || b == null -> a == null && b == null
      else -> compare(a, b) == 0
    }

    // endregion
  }
}

/** What an expression reads while it is evaluated: the values of fields and of the configuration, and today's date. */
internal interface ExpressionContext {
  /** The value of a field (e.g. `gewicht`, `rad.name`), null if missing. */
  fun field(name: String): ExpressionValue?

  /** The value of a configuration field (`config.<path>`), null if missing. */
  fun config(path: String): ExpressionValue?

  val today: LocalDate
}
