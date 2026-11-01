package de.chrgroth.james.platform.domain.model.app

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ValueConversionTests {

  @ParameterizedTest
  @CsvSource(
    "STRING, LONG",
    "STRING, DOUBLE",
    "STRING, BOOLEAN",
    "LONG, DOUBLE",
    "LONG, STRING",
    "DOUBLE, STRING",
    "BOOLEAN, STRING",
    "STRING, DATE",
    "STRING, DATETIME",
    "DATETIME, DATE",
  )
  fun `isConvertible is true for every supported type pair`(from: PropertyType, to: PropertyType) {
    assertThat(ValueConversion.isConvertible(from, to)).isTrue()
  }

  @Test
  fun `isConvertible is true for a type paired with itself`() {
    PropertyType.entries.forEach { assertThat(ValueConversion.isConvertible(it, it)).isTrue() }
  }

  @Test
  fun `isConvertible is false for an unsupported type pair`() {
    assertThat(ValueConversion.isConvertible(PropertyType.STRING, PropertyType.REF)).isFalse()
    assertThat(ValueConversion.isConvertible(PropertyType.DOUBLE, PropertyType.LONG)).isFalse()
    assertThat(ValueConversion.isConvertible(PropertyType.BOOLEAN, PropertyType.LONG)).isFalse()
  }

  @Test
  fun `convert returns null unchanged`() {
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.LONG, null).getOrNull()).isNull()
  }

  @Test
  fun `convert returns a blank value unchanged`() {
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.LONG, " ").getOrNull()).isEqualTo(" ")
  }

  @Test
  fun `convert returns the raw value unchanged when source and target type are the same`() {
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.STRING, "hello").getOrNull()).isEqualTo("hello")
  }

  @Test
  fun `convert fails for an unsupported type pair`() {
    val result = ValueConversion.convert(PropertyType.DOUBLE, PropertyType.LONG, "42.0")

    assertThat(result.leftOrNull()).isEqualTo(ValueConversionError.UNSUPPORTED_CONVERSION)
  }

  @Test
  fun `string to long normalizes the stored value to canonical long format`() {
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.LONG, "042").getOrNull()).isEqualTo("42")
  }

  @Test
  fun `string to long fails for a non-numeric value`() {
    val result = ValueConversion.convert(PropertyType.STRING, PropertyType.LONG, "abc")

    assertThat(result.leftOrNull()).isEqualTo(ValueConversionError.INVALID_VALUE)
  }

  @Test
  fun `long to double normalizes the stored value to canonical double format`() {
    assertThat(ValueConversion.convert(PropertyType.LONG, PropertyType.DOUBLE, "42").getOrNull()).isEqualTo("42")
    assertThat(ValueConversion.convert(PropertyType.LONG, PropertyType.DOUBLE, "42.500").getOrNull()).isEqualTo("42.5")
  }

  @Test
  fun `long to double fails for a non-numeric value`() {
    val result = ValueConversion.convert(PropertyType.LONG, PropertyType.DOUBLE, "abc")

    assertThat(result.leftOrNull()).isEqualTo(ValueConversionError.INVALID_VALUE)
  }

  @Test
  fun `string to boolean normalizes the stored value to canonical true or false`() {
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.BOOLEAN, "TRUE").getOrNull()).isEqualTo("true")
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.BOOLEAN, "yes").getOrNull()).isEqualTo("false")
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.BOOLEAN, "1").getOrNull()).isEqualTo("false")
  }

  @Test
  fun `datetime to date truncates the raw value to its date part`() {
    assertThat(ValueConversion.convert(PropertyType.DATETIME, PropertyType.DATE, "2024-01-15T10:30:00Z").getOrNull()).isEqualTo("2024-01-15")
    assertThat(ValueConversion.convert(PropertyType.DATETIME, PropertyType.DATE, "2024-01-15 10:30:00").getOrNull()).isEqualTo("2024-01-15")
  }

  @Test
  fun `datetime with offset to date uses the date in the app time zone`() {
    // Berlin midnight stored as UTC instant, in summer (CEST) and winter (CET)
    assertThat(ValueConversion.convert(PropertyType.DATETIME, PropertyType.DATE, "2025-08-28T22:00:00.000Z").getOrNull()).isEqualTo("2025-08-29")
    assertThat(ValueConversion.convert(PropertyType.DATETIME, PropertyType.DATE, "2024-01-14T23:00:00.000Z").getOrNull()).isEqualTo("2024-01-15")
    assertThat(ValueConversion.convert(PropertyType.DATETIME, PropertyType.DATE, "2024-01-15T00:30:00+01:00").getOrNull()).isEqualTo("2024-01-15")
  }

  @Test
  fun `datetime without offset to date keeps its local date`() {
    assertThat(ValueConversion.convert(PropertyType.DATETIME, PropertyType.DATE, "2024-01-15T23:30:00").getOrNull()).isEqualTo("2024-01-15")
  }

  @Test
  fun `a datetime with offset is converted into the local date-time in the app time zone`() {
    assertThat(ValueConversion.toLocalDatetime("2026-09-29T19:01:00.000Z")).isEqualTo("2026-09-29T21:01")
    assertThat(ValueConversion.toLocalDatetime("2026-01-15T22:30:15Z")).isEqualTo("2026-01-15T23:30:15")
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.DATETIME, "2026-09-29T19:01:00Z").getOrNull()).isEqualTo("2026-09-29T21:01")
  }

  @Test
  fun `a local datetime without offset is kept as is`() {
    assertThat(ValueConversion.toLocalDatetime("2026-09-29T21:01")).isEqualTo("2026-09-29T21:01")
  }

  @Test
  fun `string to date and string to datetime pass the raw value through unchanged`() {
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.DATE, "2024-01-15").getOrNull()).isEqualTo("2024-01-15")
    assertThat(ValueConversion.convert(PropertyType.STRING, PropertyType.DATETIME, "2024-01-15T10:30:00").getOrNull()).isEqualTo("2024-01-15T10:30:00")
  }

  @Test
  fun `convertGranularity converts a value from a foreign granularity into the unit's storage granularity`() {
    val unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.METERS)

    assertThat(ValueConversion.convertGranularity(unit, DistanceGranularity.KILOMETERS, "15")).isEqualTo("15000")
  }

  @Test
  fun `convertGranularity leaves the value unchanged when it already matches the storage granularity`() {
    val unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.METERS)

    assertThat(ValueConversion.convertGranularity(unit, DistanceGranularity.METERS, "15000")).isEqualTo("15000")
  }

  @Test
  fun `convertGranularity leaves a non-numeric value unchanged`() {
    val unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.METERS, DistanceGranularity.METERS)

    assertThat(ValueConversion.convertGranularity(unit, DistanceGranularity.KILOMETERS, "abc")).isEqualTo("abc")
  }

  @Test
  fun `convertGranularity rounds fractional results`() {
    val unit = PropertyUnit(UnitFamily.DISTANCE, DistanceGranularity.KILOMETERS, DistanceGranularity.KILOMETERS)

    assertThat(ValueConversion.convertGranularity(unit, DistanceGranularity.METERS, "1500")).isEqualTo("1.5")
  }
}
