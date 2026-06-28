package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.parseDurationValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationFormatTests {

  @ParameterizedTest
  @CsvSource(
    "1d 2h 30m 15s, P1DT2H30M15S",
    "2h 30m 15s, PT2H30M15S",
    "30m 15s, PT30M15S",
    "15s, PT15S",
    "1d, PT24H",
    "2h, PT2H",
    "02:30:15, PT2H30M15S",
    "30:15, PT30M15S",
    "15, PT15S",
    "0, PT0S",
  )
  fun `parses accepted unit-suffixed and colon-separated formats`(raw: String, expectedIso: String) {
    assertThat(parseDurationValue(raw)).isEqualTo(kotlin.time.Duration.parseIsoString(expectedIso))
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "  ", "not-a-duration", "1x", "1:2:3:4", "1::5", ":5", "5:", "1d 2x", "abc"])
  fun `rejects values not matching either accepted format`(raw: String) {
    assertThat(parseDurationValue(raw)).isNull()
  }

  @org.junit.jupiter.api.Test
  fun `sums unit-suffixed parts into the expected duration`() {
    assertThat(parseDurationValue("1d 2h 30m 15s")).isEqualTo(1.days + 2.hours + 30.minutes + 15.seconds)
  }
}
