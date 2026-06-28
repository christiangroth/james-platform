package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyId
import de.chrgroth.james.platform.domain.model.app.PropertyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PropertyConstraintServiceTests {

  private val service = PropertyConstraintService()

  // region UniqueKey

  @Test
  fun `checkValue passes UniqueKey when value is not in existing values`() {
    val property = property(constraints = setOf(PropertyConstraint.UniqueKey))

    val violations = service.checkValue(property, "new", existingValues = listOf("a", "b"))

    assertThat(violations).isEmpty()
  }

  @Test
  fun `checkValue reports UNIQUE_KEY_VIOLATION when value already exists`() {
    val property = property(constraints = setOf(PropertyConstraint.UniqueKey))

    val violations = service.checkValue(property, "dup", existingValues = listOf("dup", "other"))

    assertThat(violations).containsExactly(PropertyConstraintViolation.UniqueKeyViolation)
  }

  @Test
  fun `checkValue passes UniqueKey when no existing values provided`() {
    val property = property(constraints = setOf(PropertyConstraint.UniqueKey))

    val violations = service.checkValue(property, "first")

    assertThat(violations).isEmpty()
  }

  // endregion

  // region MinLong / MaxLong

  @Test
  fun `checkValue passes MinLong when value meets minimum`() {
    val property = property(type = PropertyType.LONG, constraints = setOf(PropertyConstraint.MinLong(10L)))

    assertThat(service.checkValue(property, 10L)).isEmpty()
    assertThat(service.checkValue(property, 100L)).isEmpty()
  }

  @Test
  fun `checkValue reports MIN_VALUE_VIOLATION when Long value is below minimum`() {
    val property = property(type = PropertyType.LONG, constraints = setOf(PropertyConstraint.MinLong(10L)))

    val violations = service.checkValue(property, 9L)

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinValueViolation(10L))
  }

  @Test
  fun `checkValue passes MaxLong when value meets maximum`() {
    val property = property(type = PropertyType.LONG, constraints = setOf(PropertyConstraint.MaxLong(100L)))

    assertThat(service.checkValue(property, 100L)).isEmpty()
    assertThat(service.checkValue(property, 0L)).isEmpty()
  }

  @Test
  fun `checkValue reports MAX_VALUE_VIOLATION when Long value exceeds maximum`() {
    val property = property(type = PropertyType.LONG, constraints = setOf(PropertyConstraint.MaxLong(100L)))

    val violations = service.checkValue(property, 101L)

    assertThat(violations).containsExactly(PropertyConstraintViolation.MaxValueViolation(100L))
  }

  // endregion

  // region MinDouble / MaxDouble

  @Test
  fun `checkValue passes MinDouble when value meets minimum`() {
    val property = property(type = PropertyType.DOUBLE, constraints = setOf(PropertyConstraint.MinDouble(1.5)))

    assertThat(service.checkValue(property, 1.5)).isEmpty()
    assertThat(service.checkValue(property, 2.0)).isEmpty()
  }

  @Test
  fun `checkValue reports MIN_VALUE_VIOLATION when Double value is below minimum`() {
    val property = property(type = PropertyType.DOUBLE, constraints = setOf(PropertyConstraint.MinDouble(1.5)))

    val violations = service.checkValue(property, 1.4)

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinValueViolation(1.5))
  }

  @Test
  fun `checkValue passes MaxDouble when value meets maximum`() {
    val property = property(type = PropertyType.DOUBLE, constraints = setOf(PropertyConstraint.MaxDouble(9.9)))

    assertThat(service.checkValue(property, 9.9)).isEmpty()
    assertThat(service.checkValue(property, 0.0)).isEmpty()
  }

  @Test
  fun `checkValue reports MAX_VALUE_VIOLATION when Double value exceeds maximum`() {
    val property = property(type = PropertyType.DOUBLE, constraints = setOf(PropertyConstraint.MaxDouble(9.9)))

    val violations = service.checkValue(property, 10.0)

    assertThat(violations).containsExactly(PropertyConstraintViolation.MaxValueViolation(9.9))
  }

  // endregion

  // region StepLong / StepDouble

  @Test
  fun `checkValue passes StepLong when value is a multiple of step`() {
    val property = property(type = PropertyType.LONG, constraints = setOf(PropertyConstraint.StepLong(5L)))

    assertThat(service.checkValue(property, 10L)).isEmpty()
    assertThat(service.checkValue(property, 0L)).isEmpty()
  }

  @Test
  fun `checkValue reports STEP_VIOLATION when Long value is not a multiple of step`() {
    val property = property(type = PropertyType.LONG, constraints = setOf(PropertyConstraint.StepLong(5L)))

    val violations = service.checkValue(property, 7L)

    assertThat(violations).containsExactly(PropertyConstraintViolation.StepViolation(5L))
  }

  @Test
  fun `checkValue passes StepDouble when value is a multiple of step`() {
    val property = property(type = PropertyType.DOUBLE, constraints = setOf(PropertyConstraint.StepDouble(0.5)))

    assertThat(service.checkValue(property, 1.0)).isEmpty()
    assertThat(service.checkValue(property, 1.5)).isEmpty()
  }

  @Test
  fun `checkValue reports STEP_VIOLATION when Double value is not a multiple of step`() {
    val property = property(type = PropertyType.DOUBLE, constraints = setOf(PropertyConstraint.StepDouble(0.5)))

    val violations = service.checkValue(property, 1.2)

    assertThat(violations).containsExactly(PropertyConstraintViolation.StepViolation(0.5))
  }

  // endregion

  // region MinDate / MaxDate

  @Test
  fun `checkValue passes MinDate and MaxDate when value is within range`() {
    val property = property(
      type = PropertyType.DATE,
      constraints = setOf(PropertyConstraint.MinDate(java.time.LocalDate.parse("2020-01-01")), PropertyConstraint.MaxDate(java.time.LocalDate.parse("2030-01-01"))),
    )

    assertThat(service.checkValue(property, "2025-06-15")).isEmpty()
  }

  @Test
  fun `checkValue reports MinDateViolation when date is before minimum`() {
    val min = java.time.LocalDate.parse("2020-01-01")
    val property = property(type = PropertyType.DATE, constraints = setOf(PropertyConstraint.MinDate(min)))

    val violations = service.checkValue(property, "2019-12-31")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinDateViolation(min))
  }

  @Test
  fun `checkValue reports MaxDateViolation when date is after maximum`() {
    val max = java.time.LocalDate.parse("2030-01-01")
    val property = property(type = PropertyType.DATE, constraints = setOf(PropertyConstraint.MaxDate(max)))

    val violations = service.checkValue(property, "2030-01-02")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MaxDateViolation(max))
  }

  @Test
  fun `checkValue skips MinDate when value is not a parseable date string`() {
    val property = property(type = PropertyType.DATE, constraints = setOf(PropertyConstraint.MinDate(java.time.LocalDate.parse("2020-01-01"))))

    assertThat(service.checkValue(property, "not a date")).isEmpty()
  }

  // endregion

  // region MinTime / MaxTime

  @Test
  fun `checkValue passes MinTime and MaxTime when value is within range`() {
    val property = property(
      type = PropertyType.TIME,
      constraints = setOf(PropertyConstraint.MinTime(java.time.LocalTime.parse("08:00:00")), PropertyConstraint.MaxTime(java.time.LocalTime.parse("18:00:00"))),
    )

    assertThat(service.checkValue(property, "12:00:00")).isEmpty()
  }

  @Test
  fun `checkValue reports MinTimeViolation when time is before minimum`() {
    val min = java.time.LocalTime.parse("08:00:00")
    val property = property(type = PropertyType.TIME, constraints = setOf(PropertyConstraint.MinTime(min)))

    val violations = service.checkValue(property, "07:59:59")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinTimeViolation(min))
  }

  @Test
  fun `checkValue reports MaxTimeViolation when time is after maximum`() {
    val max = java.time.LocalTime.parse("18:00:00")
    val property = property(type = PropertyType.TIME, constraints = setOf(PropertyConstraint.MaxTime(max)))

    val violations = service.checkValue(property, "18:00:01")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MaxTimeViolation(max))
  }

  // endregion

  // region MinDatetime / MaxDatetime

  @Test
  fun `checkValue passes MinDatetime and MaxDatetime when value is within range`() {
    val property = property(
      type = PropertyType.DATETIME,
      constraints = setOf(
        PropertyConstraint.MinDatetime(java.time.LocalDateTime.parse("2020-01-01T00:00:00")),
        PropertyConstraint.MaxDatetime(java.time.LocalDateTime.parse("2030-01-01T00:00:00")),
      ),
    )

    assertThat(service.checkValue(property, "2025-06-15T12:00:00")).isEmpty()
  }

  @Test
  fun `checkValue reports MinDatetimeViolation when datetime is before minimum`() {
    val min = java.time.LocalDateTime.parse("2020-01-01T00:00:00")
    val property = property(type = PropertyType.DATETIME, constraints = setOf(PropertyConstraint.MinDatetime(min)))

    val violations = service.checkValue(property, "2019-12-31T23:59:59")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinDatetimeViolation(min))
  }

  @Test
  fun `checkValue reports MaxDatetimeViolation when datetime is after maximum`() {
    val max = java.time.LocalDateTime.parse("2030-01-01T00:00:00")
    val property = property(type = PropertyType.DATETIME, constraints = setOf(PropertyConstraint.MaxDatetime(max)))

    val violations = service.checkValue(property, "2030-01-01T00:00:01")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MaxDatetimeViolation(max))
  }

  // endregion

  // region MinDuration / MaxDuration

  @Test
  fun `checkValue passes MinDuration and MaxDuration when value is within range`() {
    val property = property(
      type = PropertyType.DURATION,
      constraints = setOf(PropertyConstraint.MinDuration(java.time.Duration.parse("PT1H")), PropertyConstraint.MaxDuration(java.time.Duration.parse("PT8H"))),
    )

    assertThat(service.checkValue(property, "PT4H")).isEmpty()
  }

  @Test
  fun `checkValue reports MinDurationViolation when duration is below minimum`() {
    val min = java.time.Duration.parse("PT1H")
    val property = property(type = PropertyType.DURATION, constraints = setOf(PropertyConstraint.MinDuration(min)))

    val violations = service.checkValue(property, "PT30M")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinDurationViolation(min))
  }

  @Test
  fun `checkValue reports MaxDurationViolation when duration exceeds maximum`() {
    val max = java.time.Duration.parse("PT8H")
    val property = property(type = PropertyType.DURATION, constraints = setOf(PropertyConstraint.MaxDuration(max)))

    val violations = service.checkValue(property, "PT9H")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MaxDurationViolation(max))
  }

  // endregion

  // region MinLength / MaxLength / Pattern

  @Test
  fun `checkValue passes MinLength when string meets minimum length`() {
    val property = property(constraints = setOf(PropertyConstraint.MinLength(3)))

    assertThat(service.checkValue(property, "abc")).isEmpty()
    assertThat(service.checkValue(property, "abcd")).isEmpty()
  }

  @Test
  fun `checkValue reports MIN_LENGTH_VIOLATION when string is too short`() {
    val property = property(constraints = setOf(PropertyConstraint.MinLength(3)))

    val violations = service.checkValue(property, "ab")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinLengthViolation(3))
  }

  @Test
  fun `checkValue passes MaxLength when string meets maximum length`() {
    val property = property(constraints = setOf(PropertyConstraint.MaxLength(5)))

    assertThat(service.checkValue(property, "hello")).isEmpty()
    assertThat(service.checkValue(property, "hi")).isEmpty()
  }

  @Test
  fun `checkValue reports MAX_LENGTH_VIOLATION when string is too long`() {
    val property = property(constraints = setOf(PropertyConstraint.MaxLength(5)))

    val violations = service.checkValue(property, "toolong")

    assertThat(violations).containsExactly(PropertyConstraintViolation.MaxLengthViolation(5))
  }

  @Test
  fun `checkValue passes Pattern when string matches regex`() {
    val property = property(constraints = setOf(PropertyConstraint.Pattern("""^\d{4}$""")))

    assertThat(service.checkValue(property, "1234")).isEmpty()
  }

  @Test
  fun `checkValue reports PATTERN_VIOLATION when string does not match regex`() {
    val property = property(constraints = setOf(PropertyConstraint.Pattern("""^\d{4}$""")))

    val violations = service.checkValue(property, "abcd")

    assertThat(violations).containsExactly(PropertyConstraintViolation.PatternViolation("""^\d{4}$"""))
  }

  // endregion

  // region MinSize / MaxSize

  @Test
  fun `checkValue passes MinSize when list meets minimum size`() {
    val property = property(type = PropertyType.LIST, constraints = setOf(PropertyConstraint.MinSize(2)))

    assertThat(service.checkValue(property, listOf("a", "b"))).isEmpty()
    assertThat(service.checkValue(property, listOf("a", "b", "c"))).isEmpty()
  }

  @Test
  fun `checkValue reports MIN_SIZE_VIOLATION when list is too small`() {
    val property = property(type = PropertyType.LIST, constraints = setOf(PropertyConstraint.MinSize(2)))

    val violations = service.checkValue(property, listOf("a"))

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinSizeViolation(2))
  }

  @Test
  fun `checkValue passes MaxSize when list meets maximum size`() {
    val property = property(type = PropertyType.LIST, constraints = setOf(PropertyConstraint.MaxSize(3)))

    assertThat(service.checkValue(property, listOf("a", "b", "c"))).isEmpty()
  }

  @Test
  fun `checkValue reports MAX_SIZE_VIOLATION when list exceeds maximum size`() {
    val property = property(type = PropertyType.LIST, constraints = setOf(PropertyConstraint.MaxSize(3)))

    val violations = service.checkValue(property, listOf("a", "b", "c", "d"))

    assertThat(violations).containsExactly(PropertyConstraintViolation.MaxSizeViolation(3))
  }

  // endregion

  // region type mismatches — no violation expected when value type doesn't match constraint

  @Test
  fun `checkValue skips MinLong when value is not a Long`() {
    val property = property(constraints = setOf(PropertyConstraint.MinLong(10L)))

    assertThat(service.checkValue(property, "not a long")).isEmpty()
  }

  @Test
  fun `checkValue skips MinLength when value is not a String`() {
    val property = property(constraints = setOf(PropertyConstraint.MinLength(3)))

    assertThat(service.checkValue(property, 42L)).isEmpty()
  }

  @Test
  fun `checkValue skips MinSize when value is not a List`() {
    val property = property(constraints = setOf(PropertyConstraint.MinSize(1)))

    assertThat(service.checkValue(property, "not a list")).isEmpty()
  }

  // endregion

  // region multiple constraints

  @Test
  fun `checkValue reports all violations when multiple constraints fail`() {
    val property = property(type = PropertyType.LONG, constraints = setOf(PropertyConstraint.MinLong(10L), PropertyConstraint.MaxLong(5L)))

    val violations = service.checkValue(property, 7L)

    assertThat(violations).containsExactlyInAnyOrder(
      PropertyConstraintViolation.MinValueViolation(10L),
      PropertyConstraintViolation.MaxValueViolation(5L),
    )
  }

  @Test
  fun `checkValue returns no violations when no constraints are defined`() {
    val property = property(constraints = emptySet())

    val violations = service.checkValue(property, null)

    assertThat(violations).isEmpty()
  }

  // endregion

  // region OBJECT / nestedProperties

  @Test
  fun `checkValue returns no violations for OBJECT property with no nested properties`() {
    val property = property(type = PropertyType.OBJECT)

    val violations = service.checkValue(property, mapOf("anything" to "value"))

    assertThat(violations).isEmpty()
  }

  @Test
  fun `checkValue returns no violations for OBJECT property when nested property value is valid`() {
    val nested = property(id = "nested-1", name = "Nested", type = PropertyType.STRING, constraints = setOf(PropertyConstraint.MinLength(2)))
    val property = property(type = PropertyType.OBJECT).copy(nestedProperties = listOf(nested))

    val violations = service.checkValue(property, mapOf("nested-1" to "ab"))

    assertThat(violations).isEmpty()
  }

  @Test
  fun `checkValue recurses into nested OBJECT property at depth 2`() {
    val innerNested = property(id = "inner-1", name = "Inner", type = PropertyType.STRING, constraints = setOf(PropertyConstraint.MinLength(3)))
    val outerNested = property(id = "outer-1", name = "Outer", type = PropertyType.OBJECT).copy(nestedProperties = listOf(innerNested))
    val property = property(type = PropertyType.OBJECT).copy(nestedProperties = listOf(outerNested))

    val violations = service.checkValue(property, mapOf("outer-1" to mapOf("inner-1" to "abcd")))

    assertThat(violations).isEmpty()
  }

  @Test
  fun `checkValue reports violation when nested property value fails constraint`() {
    val nested = property(id = "nested-1", name = "Nested", type = PropertyType.STRING, constraints = setOf(PropertyConstraint.MinLength(5)))
    val property = property(type = PropertyType.OBJECT).copy(nestedProperties = listOf(nested))

    val violations = service.checkValue(property, mapOf("nested-1" to "ab"))

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinLengthViolation(5))
  }

  @Test
  fun `checkValue propagates violation from nested OBJECT property at depth 2`() {
    val innerNested = property(id = "inner-1", name = "Inner", type = PropertyType.STRING, constraints = setOf(PropertyConstraint.MinLength(5)))
    val outerNested = property(id = "outer-1", name = "Outer", type = PropertyType.OBJECT).copy(nestedProperties = listOf(innerNested))
    val property = property(type = PropertyType.OBJECT).copy(nestedProperties = listOf(outerNested))

    val violations = service.checkValue(property, mapOf("outer-1" to mapOf("inner-1" to "ab")))

    assertThat(violations).containsExactly(PropertyConstraintViolation.MinLengthViolation(5))
  }

  @Test
  fun `checkValue returns no violations for OBJECT property when value is not a Map`() {
    val nested = property(id = "nested-1", name = "Nested", type = PropertyType.STRING, constraints = setOf(PropertyConstraint.MinLength(5)))
    val property = property(type = PropertyType.OBJECT).copy(nestedProperties = listOf(nested))

    val violations = service.checkValue(property, null)

    assertThat(violations).isEmpty()
  }

  // endregion

  companion object {
    fun property(
      id: String = "p-1",
      name: String = "Name",
      type: PropertyType = PropertyType.STRING,
      constraints: Set<PropertyConstraint> = emptySet(),
    ) = Property(
      id = PropertyId(id),
      name = name,
      type = type,
      constraints = constraints,
    )
  }
}
