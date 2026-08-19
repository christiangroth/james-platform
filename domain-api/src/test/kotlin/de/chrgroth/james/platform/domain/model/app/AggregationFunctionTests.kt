package de.chrgroth.james.platform.domain.model.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class AggregationFunctionTests {

  @ParameterizedTest
  @EnumSource(value = AggregationFunction::class, names = ["SUM", "AVG", "MIN", "MAX"])
  fun `requiresNumericSourceProperty is true for SUM, AVG, MIN, MAX`(function: AggregationFunction) {
    assertThat(function.requiresNumericSourceProperty()).isTrue()
  }

  @ParameterizedTest
  @EnumSource(value = AggregationFunction::class, names = ["COUNT"])
  fun `requiresNumericSourceProperty is false for COUNT`(function: AggregationFunction) {
    assertThat(function.requiresNumericSourceProperty()).isFalse()
  }
}
