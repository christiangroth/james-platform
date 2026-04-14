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

  // region NOT_NULL

  @Test
  fun `checkValue passes NOT_NULL when value is present`() {
    val property = property(constraints = setOf(PropertyConstraint.NOT_NULL))

    val violations = service.checkValue(property, "hello")

    assertThat(violations).isEmpty()
  }

  @Test
  fun `checkValue reports NOT_NULL_VIOLATION when value is null`() {
    val property = property(constraints = setOf(PropertyConstraint.NOT_NULL))

    val violations = service.checkValue(property, null)

    assertThat(violations).containsExactly(PropertyConstraintViolation.NOT_NULL_VIOLATION)
  }

  // endregion

  // region UNIQUE_KEY

  @Test
  fun `checkValue passes UNIQUE_KEY when value is not in existing values`() {
    val property = property(constraints = setOf(PropertyConstraint.UNIQUE_KEY))

    val violations = service.checkValue(property, "new", existingValues = listOf("a", "b"))

    assertThat(violations).isEmpty()
  }

  @Test
  fun `checkValue reports UNIQUE_KEY_VIOLATION when value already exists`() {
    val property = property(constraints = setOf(PropertyConstraint.UNIQUE_KEY))

    val violations = service.checkValue(property, "dup", existingValues = listOf("dup", "other"))

    assertThat(violations).containsExactly(PropertyConstraintViolation.UNIQUE_KEY_VIOLATION)
  }

  @Test
  fun `checkValue passes UNIQUE_KEY when no existing values provided`() {
    val property = property(constraints = setOf(PropertyConstraint.UNIQUE_KEY))

    val violations = service.checkValue(property, "first")

    assertThat(violations).isEmpty()
  }

  // endregion

  // region multiple constraints

  @Test
  fun `checkValue reports all violations when multiple constraints fail`() {
    val property = property(constraints = setOf(PropertyConstraint.NOT_NULL, PropertyConstraint.UNIQUE_KEY))

    val violations = service.checkValue(property, null, existingValues = listOf(null, "other"))

    assertThat(violations).containsExactlyInAnyOrder(
      PropertyConstraintViolation.NOT_NULL_VIOLATION,
      PropertyConstraintViolation.UNIQUE_KEY_VIOLATION,
    )
  }

  @Test
  fun `checkValue returns no violations when no constraints are defined`() {
    val property = property(constraints = emptySet())

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
