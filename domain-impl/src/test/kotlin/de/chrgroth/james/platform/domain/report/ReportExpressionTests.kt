package de.chrgroth.james.platform.domain.report

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class ReportExpressionTests {

  private fun eval(source: String, values: Map<String, Double?> = emptyMap()): Double? = ReportExpression.parse(source).evaluate { values[it] }

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
    val bmi = ReportExpression.parse("gewicht / (config.größe / 100) ^ 2")

    assertThat(bmi.identifiers).containsExactlyInAnyOrder("gewicht", "config.größe")
    assertThat(bmi.evaluate { mapOf("gewicht" to 80.0, "config.größe" to 180.0)[it] }).isCloseTo(24.69, within(0.01))
    assertThat(eval("jahresbrutto * 40 / wochenstunden", mapOf("jahresbrutto" to 60_000.0, "wochenstunden" to 30.0))).isEqualTo(80_000.0)
  }

  @Test
  fun `missing values and divisions by zero yield no value, coalesce falls back`() {
    assertThat(eval("a + 1")).isNull()
    assertThat(eval("1 / b", mapOf("b" to 0.0))).isNull()
    assertThat(eval("sqrt(-1)")).isNull()
    assertThat(eval("coalesce(a, b, 3)", mapOf("b" to 2.0))).isEqualTo(2.0)
    assertThat(eval("coalesce(a, 3)")).isEqualTo(3.0)
  }

  @Test
  fun `functions`() {
    assertThat(eval("round(1.2345, 2)")).isEqualTo(1.23)
    assertThat(eval("round(2.5)")).isEqualTo(3.0)
    assertThat(eval("abs(-3) + floor(1.7) + ceil(1.2)")).isEqualTo(6.0)
    assertThat(eval("min(3, 1, 2) + max(3, 1, 2)")).isEqualTo(4.0)
    assertThat(eval("sqrt(16)")).isEqualTo(4.0)
  }

  @Test
  fun `invalid expressions are rejected with their position`() {
    assertThatThrownBy { ReportExpression.parse("1 +") }.isInstanceOfSatisfying(ReportExpression.ParseException::class.java) {
      assertThat(it.position).isEqualTo(4)
    }
    assertThatThrownBy { ReportExpression.parse("(1 + 2") }.isInstanceOf(ReportExpression.ParseException::class.java)
    assertThatThrownBy { ReportExpression.parse("1 2") }.isInstanceOfSatisfying(ReportExpression.ParseException::class.java) {
      assertThat(it.position).isEqualTo(3)
    }
    assertThatThrownBy { ReportExpression.parse("abs(1, 2)") }.isInstanceOf(ReportExpression.ParseException::class.java)
    assertThatThrownBy { ReportExpression.parse("a = 1") }.isInstanceOf(ReportExpression.ParseException::class.java)
    assertThatThrownBy { ReportExpression.parse("exec(1)") }.isInstanceOfSatisfying(ReportExpression.ParseException::class.java) {
      assertThat(it.unknownFunction).isEqualTo("exec")
    }
    assertThatThrownBy { ReportExpression.parse("1+".repeat(300) + "1") }.isInstanceOf(ReportExpression.ParseException::class.java)
  }
}
