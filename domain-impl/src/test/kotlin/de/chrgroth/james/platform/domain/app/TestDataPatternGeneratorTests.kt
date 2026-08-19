package de.chrgroth.james.platform.domain.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

class TestDataPatternGeneratorTests {

  private val patterns = listOf(
    "[A-Z]{2}-[0-9]{4}",
    "[a-z]+",
    "\\d{3,6}",
    "(foo|bar|baz)",
    "[A-Za-z0-9_]{5,10}",
    "\\w+@\\w+\\.\\w+",
    "a?b*c+",
    "[^0-9]{3}",
  )

  @Test
  fun `generate produces strings matching the given pattern for a range of seeds`() {
    for (pattern in patterns) {
      val regex = Regex(pattern)
      var matched = 0
      val attempts = 200
      repeat(attempts) { seed ->
        val candidate = TestDataPatternGenerator.generate(pattern, Random(seed.toLong()))
        if (regex.matches(candidate)) matched++
      }
      assertThat(matched).describedAs("pattern '$pattern' should match for most attempts").isGreaterThan(attempts / 2)
    }
  }

  @Test
  fun `generate is deterministic for the same seed`() {
    val first = TestDataPatternGenerator.generate("[A-Z]{3}-\\d{2,5}", Random(123L))
    val second = TestDataPatternGenerator.generate("[A-Z]{3}-\\d{2,5}", Random(123L))

    assertThat(first).isEqualTo(second)
  }

  @Test
  fun `generate never runs unbounded for unbounded quantifiers`() {
    val result = TestDataPatternGenerator.generate("a*", Random(1L))

    assertThat(result.length).isLessThanOrEqualTo(8)
  }
}
