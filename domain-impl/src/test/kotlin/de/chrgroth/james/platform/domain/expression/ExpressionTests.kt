package de.chrgroth.james.platform.domain.expression

import de.chrgroth.james.platform.domain.expression.ExpressionValue.Bool
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Date
import de.chrgroth.james.platform.domain.expression.ExpressionValue.DateTime
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Num
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Text
import de.chrgroth.james.platform.domain.expression.ExpressionValue.Time
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ExpressionTests {

  private val today = LocalDate.of(2026, 11, 3)

  private class MapContext(private val fields: Map<String, ExpressionValue?>, private val configValues: Map<String, ExpressionValue?>, override val today: LocalDate) :
    ExpressionContext {
    override fun field(name: String): ExpressionValue? = fields[name]

    override fun config(path: String): ExpressionValue? = configValues[path]
  }

  private fun value(source: String, fields: Map<String, ExpressionValue?> = emptyMap(), config: Map<String, ExpressionValue?> = emptyMap()): ExpressionValue? =
    Expression.parse(source).evaluate(MapContext(fields, config, today))

  private fun eval(source: String, values: Map<String, Double?> = emptyMap()): Double? =
    Expression.parse(source).evaluateNumber(MapContext(values.mapValues { (_, v) -> v?.let { Num(it) } }, emptyMap(), today))

  private fun type(source: String, types: Map<String, ExpressionType> = emptyMap()): Expression.TypeCheck = Expression.parse(source).check { types[it] }

  private fun parseError(source: String): Expression.ParseException =
    runCatching { Expression.parse(source) }.exceptionOrNull() as? Expression.ParseException ?: error("'$source' parsed")

  // region arithmetic (backwards compatible)

  @Test
  fun `operators follow the usual precedence, power is right-associative and binds tighter than unary minus`() {
    assertThat(eval("1 + 2 * 3")).isEqualTo(7.0)
    assertThat(eval("(1 + 2) * 3")).isEqualTo(9.0)
    assertThat(eval("10 - 4 - 3")).isEqualTo(3.0)
    assertThat(eval("2 ^ 3 ^ 2")).isEqualTo(512.0)
    assertThat(eval("-2 ^ 2")).isEqualTo(-4.0)
    assertThat(eval("2 ^ -1")).isEqualTo(0.5)
    assertThat(eval("10 % 4")).isEqualTo(2.0)
    assertThat(eval("1.5 * 2")).isEqualTo(3.0)
  }

  @Test
  fun `fields are read by name, including umlauts, dots and config references`() {
    val bmi = Expression.parse("gewicht / (config.größe / 100) ^ 2")

    assertThat(bmi.identifiers).containsExactlyInAnyOrder("gewicht", "config.größe")
    assertThat(bmi.evaluateNumber(MapContext(mapOf("gewicht" to Num(80.0)), mapOf("größe" to Num(180.0)), today))).isCloseTo(24.69, within(0.01))
    assertThat(eval("jahresbrutto * 40 / wochenstunden", mapOf("jahresbrutto" to 60_000.0, "wochenstunden" to 30.0))).isEqualTo(80_000.0)
    assertThat(eval("garantiert.ablauf + 1", mapOf("garantiert.ablauf" to 1.0))).isEqualTo(2.0)
  }

  @Test
  fun `missing values and divisions by zero yield no value, coalesce falls back`() {
    assertThat(eval("a + 1")).isNull()
    assertThat(eval("1 / b", mapOf("b" to 0.0))).isNull()
    assertThat(eval("1 % b", mapOf("b" to 0.0))).isNull()
    assertThat(eval("sqrt(-1)")).isNull()
    assertThat(eval("10 ^ 400")).isNull()
    assertThat(eval("coalesce(a, b, 3)", mapOf("b" to 2.0))).isEqualTo(2.0)
    assertThat(eval("coalesce(a, 3)")).isEqualTo(3.0)
    assertThat(eval("coalesce(1 / 0, 3)")).isEqualTo(3.0)
    assertThat(eval("max(a, 1)")).isNull()
  }

  @Test
  fun `numeric functions`() {
    assertThat(eval("round(1.2345, 2)")).isEqualTo(1.23)
    assertThat(eval("round(2.5)")).isEqualTo(3.0)
    assertThat(eval("abs(-3) + floor(1.7) + ceil(1.2)")).isEqualTo(6.0)
    assertThat(eval("min(3, 1, 2) + max(3, 1, 2)")).isEqualTo(4.0)
    assertThat(eval("sqrt(16)")).isEqualTo(4.0)
  }

  // endregion

  // region booleans, comparisons, conditions

  @Test
  fun `comparisons, logic and their precedence`() {
    assertThat(value("1 + 1 == 2")).isEqualTo(Bool(true))
    assertThat(value("2 * 3 > 5 and 1 < 2")).isEqualTo(Bool(true))
    assertThat(value("1 > 2 or 2 >= 2 and 3 <= 2")).isEqualTo(Bool(false))
    assertThat(value("(1 > 2 or 2 >= 2) and 3 != 2")).isEqualTo(Bool(true))
    assertThat(value("not 1 > 2")).isEqualTo(Bool(true))
    assertThat(value("!(1 > 2) && 1 == 1 || false")).isEqualTo(Bool(true))
    assertThat(value("true and not false")).isEqualTo(Bool(true))
  }

  @Test
  fun `booleans count as 1 and 0 in arithmetic and as yes or no in logic`() {
    val journal = mapOf("kopfschmerz" to Bool(true), "migraene" to Bool(false))

    assertThat(value("kopfschmerz + migraene + true", journal)).isEqualTo(Num(2.0))
    assertThat(value("max(kopfschmerz, migraene)", journal)).isEqualTo(Num(1.0))
    assertThat(value("kopfschmerz or migraene", journal)).isEqualTo(Bool(true))
    assertThat(value("kopfschmerz and migraene", journal)).isEqualTo(Bool(false))
    assertThat(value("kopfschmerz == 1", journal)).isEqualTo(Bool(true))
    assertThat(Expression.parse("kopfschmerz or migraene").evaluateNumber(MapContext(journal, emptyMap(), today))).isEqualTo(1.0)
  }

  @Test
  fun `missing values are no in logic and conditions, equal only to null and make ordering comparisons empty`() {
    assertThat(value("fehlt or false")).isEqualTo(Bool(false))
    assertThat(value("not fehlt")).isEqualTo(Bool(true))
    assertThat(value("if(fehlt, 1, 0)")).isEqualTo(Num(0.0))
    assertThat(value("fehlt == null")).isEqualTo(Bool(true))
    assertThat(value("fehlt == 1")).isEqualTo(Bool(false))
    assertThat(value("fehlt != 1")).isEqualTo(Bool(true))
    assertThat(value("fehlt > 1")).isNull()
    assertThat(value("present(fehlt)")).isEqualTo(Bool(false))
    assertThat(value("present(' ')")).isEqualTo(Bool(false))
    assertThat(value("present(0)")).isEqualTo(Bool(true))
  }

  @Test
  fun `if evaluates only the chosen branch`() {
    assertThat(value("if(2 > 1, 'ja', 'nein')")).isEqualTo(Text("ja"))
    assertThat(value("if(0, 1 / 0, 5)")).isEqualTo(Num(5.0))
    assertThat(value("if(x > 0, x, null)", mapOf("x" to Num(-1.0)))).isNull()
  }

  // endregion

  // region texts

  @Test
  fun `texts as literals, results, in comparisons and case`() {
    val row = mapOf("befinden" to Text("angeschlagen"), "art" to Text("U"))

    assertThat(value("\"krank\"")).isEqualTo(Text("krank"))
    assertThat(value("'it\\'s'")).isEqualTo(Text("it's"))
    assertThat(value("befinden == 'angeschlagen'", row)).isEqualTo(Bool(true))
    assertThat(value("befinden == 'Angeschlagen'", row)).isEqualTo(Bool(false))
    assertThat(value("'a' < 'b'")).isEqualTo(Bool(true))
    assertThat(value("case(befinden, 'krank', 0, 'angeschlagen', 1, 'gut', 2, 3)", row)).isEqualTo(Num(1.0))
    assertThat(value("case(befinden, 'krank', 0, 3)", row)).isEqualTo(Num(3.0))
    assertThat(value("case(befinden, 'krank', 0, 'gut', 2)", row)).isNull()
    assertThat(value("case(fehlt, 'krank', 0, null, -1, 9)", row)).isEqualTo(Num(-1.0))
    assertThat(value("case(art, 'U', 'Urlaub', 'K', 'Krank', 'Arbeit')", row)).isEqualTo(Text("Urlaub"))
    assertThat(value("concat(art, '-', 2, '-', fehlt)", row)).isEqualTo(Text("U-2-"))
    assertThat(value("'abc' == 1")).isEqualTo(Bool(false))
  }

  // endregion

  // region dates and times

  @Test
  fun `minutes between times of day (overnight on the next day) and date-times`() {
    val shift = mapOf("beginn" to Time(LocalTime.of(8, 15)), "ende" to Time(LocalTime.of(16, 45)), "pause" to Num(30.0))

    assertThat(value("minutes(beginn, ende) - pause", shift)).isEqualTo(Num(480.0))
    assertThat(value("minutes(time('22:00'), time('06:00'))")).isEqualTo(Num(480.0))
    assertThat(value("minutes('08:00', '08:30')")).isEqualTo(Num(30.0))
    val start = DateTime(LocalDateTime.of(2026, 11, 2, 22, 0))
    val end = DateTime(LocalDateTime.of(2026, 11, 3, 1, 30))
    assertThat(value("minutes(a, b)", mapOf("a" to start, "b" to end))).isEqualTo(Num(210.0))
    assertThat(value("minutes(beginn, fehlt)", shift)).isNull()
  }

  @Test
  fun `days, weekday, day, month, year and today`() {
    val row = mapOf("datum" to Date(LocalDate.of(2026, 11, 1)), "zeit" to DateTime(LocalDateTime.of(2026, 10, 30, 12, 0)))

    assertThat(value("days(datum, today())", row)).isEqualTo(Num(2.0))
    assertThat(value("days(today(), datum)", row)).isEqualTo(Num(-2.0))
    assertThat(value("weekday(datum)", row)).isEqualTo(Num(7.0))
    assertThat(value("weekday(today())")).isEqualTo(Num(2.0))
    assertThat(value("weekday(zeit)", row)).isEqualTo(Num(5.0))
    assertThat(value("day(datum) + month(datum) * 100 + year(datum) * 10000", row)).isEqualTo(Num(20261101.0))
    assertThat(value("today()")).isEqualTo(Date(today))
    assertThat(value("date('2026-01-01')")).isEqualTo(Date(LocalDate.of(2026, 1, 1)))
    assertThat(value("date('kein Datum')")).isNull()
    assertThat(value("year(fehlt)")).isNull()
  }

  @Test
  fun `dates compare with dates, date-times, configuration dates and date texts`() {
    val row = mapOf("datum" to Date(LocalDate.of(2026, 3, 1)), "zeit" to DateTime(LocalDateTime.of(2026, 3, 1, 8, 0)))
    val config = mapOf("wechsel" to Date(LocalDate.of(2026, 2, 1)))
    val expression = Expression.parse("if(datum >= config.wechsel, 40, 38)")

    assertThat(expression.evaluate(MapContext(row, config, today))).isEqualTo(Num(40.0))
    assertThat(expression.evaluate(MapContext(mapOf("datum" to Date(LocalDate.of(2026, 1, 31))), config, today))).isEqualTo(Num(38.0))
    assertThat(value("datum < '2026-03-02'", row)).isEqualTo(Bool(true))
    assertThat(value("datum == date('2026-03-01')", row)).isEqualTo(Bool(true))
    assertThat(value("zeit > datum", row)).isEqualTo(Bool(true))
    assertThat(value("time('08:00') < time('09:30')")).isEqualTo(Bool(true))
    assertThat(value("datum > 5", row)).isNull()
  }

  @Test
  fun `raw values are read by type or guessed`() {
    assertThat(ExpressionValue.of("2026-11-03", ExpressionType.DATE)).isEqualTo(Date(LocalDate.of(2026, 11, 3)))
    assertThat(ExpressionValue.of("2026-11-03T08:00", ExpressionType.DATE)).isEqualTo(Date(LocalDate.of(2026, 11, 3)))
    assertThat(ExpressionValue.of("08:30", ExpressionType.TIME)).isEqualTo(Time(LocalTime.of(8, 30)))
    assertThat(ExpressionValue.of("true", ExpressionType.BOOLEAN)).isEqualTo(Bool(true))
    assertThat(ExpressionValue.of("12", ExpressionType.TEXT)).isEqualTo(Text("12"))
    assertThat(ExpressionValue.of("", ExpressionType.TEXT)).isNull()
    assertThat(ExpressionValue.of("x", ExpressionType.NUMBER)).isNull()
    assertThat(ExpressionValue.of("1.5", null)).isEqualTo(Num(1.5))
    assertThat(ExpressionValue.of("false", null)).isEqualTo(Bool(false))
    assertThat(ExpressionValue.of("2026-11-03", null)).isEqualTo(Date(LocalDate.of(2026, 11, 3)))
    assertThat(ExpressionValue.of("09:00", null)).isEqualTo(Time(LocalTime.of(9, 0)))
    assertThat(ExpressionValue.of("krank", null)).isEqualTo(Text("krank"))
  }

  // endregion

  // region type check

  @Test
  fun `result types are inferred`() {
    val types = mapOf("datum" to ExpressionType.DATE, "befinden" to ExpressionType.TEXT, "krank" to ExpressionType.BOOLEAN, "beginn" to ExpressionType.TIME)

    assertThat(type("1 + krank", types).type).isEqualTo(ExpressionType.NUMBER)
    assertThat(type("datum > today()", types).type).isEqualTo(ExpressionType.BOOLEAN)
    assertThat(type("if(krank, 'krank', befinden)", types).type).isEqualTo(ExpressionType.TEXT)
    assertThat(type("if(krank, 1, krank)", types).type).isEqualTo(ExpressionType.NUMBER)
    assertThat(type("coalesce(datum, today())", types).type).isEqualTo(ExpressionType.DATE)
    assertThat(type("case(befinden, 'krank', 0, 'gut', 2, 1)", types).type).isEqualTo(ExpressionType.NUMBER)
    assertThat(type("minutes(beginn, '17:00')", types).type).isEqualTo(ExpressionType.NUMBER)
    assertThat(type("time(beginn)", types).type).isEqualTo(ExpressionType.TIME)
    assertThat(type("unbekannt").type).isNull()
    assertThat(type("coalesce(unbekannt, null)").type).isNull()
    assertThat(type("if(krank, 1, null)", types).type).isEqualTo(ExpressionType.NUMBER)
    assertThat(type("concat(datum)", types).type).isEqualTo(ExpressionType.TEXT)
    assertThat(type("1 + krank", types).problems).isEmpty()
  }

  @Test
  fun `type problems are reported with operator or function and types`() {
    val types = mapOf("datum" to ExpressionType.DATE, "befinden" to ExpressionType.TEXT, "beginn" to ExpressionType.TIME)

    assertThat(type("befinden * 2", types).problems)
      .containsExactly(ExpressionProblem(ExpressionProblem.Kind.OPERAND_TYPE, listOf("*", "TEXT")))
    assertThat(type("-datum", types).problems).containsExactly(ExpressionProblem(ExpressionProblem.Kind.OPERAND_TYPE, listOf("-", "DATE")))
    assertThat(type("minutes(datum, beginn)", types).problems)
      .containsExactly(ExpressionProblem(ExpressionProblem.Kind.ARGUMENT_TYPE, listOf("minutes", "1", "DATE")))
    assertThat(type("year(beginn) + round(befinden)", types).problems).containsExactly(
      ExpressionProblem(ExpressionProblem.Kind.ARGUMENT_TYPE, listOf("year", "1", "TIME")),
      ExpressionProblem(ExpressionProblem.Kind.ARGUMENT_TYPE, listOf("round", "1", "TEXT")),
    )
    assertThat(type("befinden == 1", types).problems).containsExactly(ExpressionProblem(ExpressionProblem.Kind.INCOMPARABLE, listOf("==", "TEXT", "NUMBER")))
    assertThat(type("datum > beginn", types).problems).containsExactly(ExpressionProblem(ExpressionProblem.Kind.INCOMPARABLE, listOf(">", "DATE", "TIME")))
    assertThat(type("case(datum, 1, 'a', 'b')", types).problems).containsExactly(ExpressionProblem(ExpressionProblem.Kind.INCOMPARABLE, listOf("case", "DATE", "NUMBER")))
    assertThat(type("if(true, 1, 'a')").problems).containsExactly(ExpressionProblem(ExpressionProblem.Kind.MIXED_TYPES, listOf("if", "NUMBER, TEXT")))
    assertThat(type("if(true, 1, 'a')").type).isNull()
    assertThat(type("datum >= '2026-01-01' and befinden != 'krank'", types).problems).isEmpty()
  }

  // endregion

  // region parse errors

  @Test
  fun `invalid expressions are rejected with their position`() {
    assertThat(parseError("1 +").position).isEqualTo(4)
    parseError("(1 + 2")
    assertThat(parseError("1 2").position).isEqualTo(3)
    parseError("abs(1, 2)")
    parseError("today(1)")
    parseError("if(1, 2)")
    parseError("case(1, 2)")
    assertThat(parseError("a = 1").detail).contains("==")
    assertThat(parseError("exec(1)").unknownFunction).isEqualTo("exec")
    assertThat(parseError("1 < 2 < 3").detail).contains("chained")
    assertThat(parseError("'offen").detail).contains("unterminated")
    parseError("1 + and")
    parseError("a.")
    parseError("1..2")
    assertThatThrownBy { Expression.parse("1+".repeat(300) + "1") }.isInstanceOf(Expression.ParseException::class.java)
  }

  @Test
  fun `keywords are whole words, so field names may start with them`() {
    assertThat(value("order + notiz + truth", mapOf("order" to Num(1.0), "notiz" to Num(2.0), "truth" to Num(3.0)))).isEqualTo(Num(6.0))
    assertThat(Expression.parse("order or andere").identifiers).containsExactlyInAnyOrder("order", "andere")
  }

  // endregion
}
