package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.CurrencyGranularity
import de.chrgroth.james.platform.domain.model.app.DistanceGranularity
import de.chrgroth.james.platform.domain.model.app.TimeGranularity
import de.chrgroth.james.platform.domain.model.app.UnitFamily
import de.chrgroth.james.platform.domain.model.app.granularityByName
import de.chrgroth.james.platform.domain.model.app.formatUnitValue
import de.chrgroth.james.platform.domain.model.app.parseUnitValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class UnitFormatTests {

  // region parseUnitValue — DISTANCE

  @ParameterizedTest
  @CsvSource(
    "15km 400m, 15400",
    "15km, 15000",
    "400m, 400",
    "1km 1m 1cm 1mm, 1001.011",
    "0km, 0",
  )
  fun `parses unit-suffixed distance values into storage granularity`(raw: String, expectedMeters: String) {
    assertThat(parseUnitValue(raw, DistanceGranularity.METERS)).isEqualByComparingTo(BigDecimal(expectedMeters))
  }

  @Test
  fun `parses a decimal amount that converts to an integer in storage granularity`() {
    assertThat(parseUnitValue("17,23km", DistanceGranularity.METERS)).isEqualByComparingTo(BigDecimal("17230"))
    assertThat(parseUnitValue("17.23km", DistanceGranularity.METERS)).isEqualByComparingTo(BigDecimal("17230"))
  }

  @Test
  fun `parses a bare number as already expressed in storage granularity`() {
    assertThat(parseUnitValue("15400", DistanceGranularity.METERS)).isEqualByComparingTo(BigDecimal("15400"))
    assertThat(parseUnitValue("17,23", DistanceGranularity.METERS)).isEqualByComparingTo(BigDecimal("17.23"))
  }

  // endregion

  // region parseUnitValue — TIME

  @ParameterizedTest
  @CsvSource(
    "1d 2h 30min 15s, 95415000",
    "2h, 7200000",
    "500ms, 500",
  )
  fun `parses unit-suffixed time values into storage granularity`(raw: String, expectedMillis: String) {
    assertThat(parseUnitValue(raw, TimeGranularity.MILLISECONDS)).isEqualByComparingTo(BigDecimal(expectedMillis))
  }

  // endregion

  // region parseUnitValue — rejected input

  @ParameterizedTest
  @ValueSource(strings = ["", "  ", "not-a-number", "15xy", "15km 20xy", "abc"])
  fun `rejects values not matching either accepted format`(raw: String) {
    assertThat(parseUnitValue(raw, DistanceGranularity.METERS)).isNull()
  }

  @Test
  fun `rejects symbols from a different family than the storage granularity`() {
    assertThat(parseUnitValue("15min", DistanceGranularity.METERS)).isNull()
    assertThat(parseUnitValue("15km", TimeGranularity.SECONDS)).isNull()
  }

  // endregion

  // region formatUnitValue

  @Test
  fun `formats a storage value as unit-suffixed composite text, accepted back by parseUnitValue`() {
    val formatted = formatUnitValue(BigDecimal("15400"), DistanceGranularity.METERS)

    assertThat(formatted).isEqualTo("15km 400m")
    assertThat(parseUnitValue(formatted, DistanceGranularity.METERS)).isEqualByComparingTo(BigDecimal("15400"))
  }

  @Test
  fun `formats zero using the smallest granularity`() {
    assertThat(formatUnitValue(BigDecimal.ZERO, DistanceGranularity.METERS)).isEqualTo("0mm")
  }

  // endregion

  // region CURRENCY

  @ParameterizedTest
  @CsvSource(
    "'12,50€', 1250",
    "12.50 €, 1250",
    "12€ 50ct, 1250",
    "99ct, 99",
    "1250, 1250",
  )
  fun `parses euro amounts into cents`(raw: String, expectedCents: String) {
    assertThat(parseUnitValue(raw, CurrencyGranularity.CENTS)).isEqualByComparingTo(BigDecimal(expectedCents))
  }

  @Test
  fun `parses euro amounts into a euro storage granularity`() {
    assertThat(parseUnitValue("12,50€", CurrencyGranularity.EUROS)).isEqualByComparingTo(BigDecimal("12.5"))
  }

  @Test
  fun `rejects distance symbols for a currency value`() {
    assertThat(parseUnitValue("12km", CurrencyGranularity.CENTS)).isNull()
  }

  @Test
  fun `formats currency as a single euro amount with two decimals, accepted back by parseUnitValue`() {
    assertThat(formatUnitValue(BigDecimal("1250"), CurrencyGranularity.CENTS)).isEqualTo("12,50€")
    assertThat(formatUnitValue(BigDecimal("1999.5"), CurrencyGranularity.EUROS)).isEqualTo("1999,50€")
    assertThat(formatUnitValue(BigDecimal.ZERO, CurrencyGranularity.CENTS)).isEqualTo("0,00€")
    assertThat(parseUnitValue(formatUnitValue(BigDecimal("1250"), CurrencyGranularity.CENTS), CurrencyGranularity.CENTS)).isEqualByComparingTo(BigDecimal("1250"))
  }

  @Test
  fun `resolves currency granularities by name`() {
    assertThat(granularityByName(UnitFamily.CURRENCY, "euros")).isEqualTo(CurrencyGranularity.EUROS)
    assertThat(granularityByName(UnitFamily.CURRENCY, "KILOMETERS")).isNull()
  }

  // endregion
}
