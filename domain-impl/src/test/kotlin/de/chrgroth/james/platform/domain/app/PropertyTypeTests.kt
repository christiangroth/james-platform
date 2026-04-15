package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PropertyTypeTests {

  // region general constraints available on every type

  @Test
  fun `every PropertyType includes NotNull in availableConstraints`() {
    PropertyType.entries.forEach { type ->
      assertThat(type.availableConstraints())
        .describedAs("$type should include NotNull")
        .contains(PropertyConstraint.NotNull::class)
    }
  }

  @Test
  fun `every PropertyType includes UniqueKey in availableConstraints`() {
    PropertyType.entries.forEach { type ->
      assertThat(type.availableConstraints())
        .describedAs("$type should include UniqueKey")
        .contains(PropertyConstraint.UniqueKey::class)
    }
  }

  // endregion

  // region LONG

  @Test
  fun `LONG includes MinLong and MaxLong`() {
    val constraints = PropertyType.LONG.availableConstraints()
    assertThat(constraints).contains(PropertyConstraint.MinLong::class, PropertyConstraint.MaxLong::class)
  }

  @Test
  fun `LONG does not include string or list constraints`() {
    val constraints = PropertyType.LONG.availableConstraints()
    assertThat(constraints).doesNotContain(
      PropertyConstraint.MinLength::class,
      PropertyConstraint.MaxLength::class,
      PropertyConstraint.Pattern::class,
      PropertyConstraint.MinSize::class,
      PropertyConstraint.MaxSize::class,
    )
  }

  // endregion

  // region DOUBLE

  @Test
  fun `DOUBLE includes MinDouble and MaxDouble`() {
    val constraints = PropertyType.DOUBLE.availableConstraints()
    assertThat(constraints).contains(PropertyConstraint.MinDouble::class, PropertyConstraint.MaxDouble::class)
  }

  @Test
  fun `DOUBLE does not include LONG or string or list constraints`() {
    val constraints = PropertyType.DOUBLE.availableConstraints()
    assertThat(constraints).doesNotContain(
      PropertyConstraint.MinLong::class,
      PropertyConstraint.MaxLong::class,
      PropertyConstraint.MinLength::class,
      PropertyConstraint.MaxLength::class,
      PropertyConstraint.Pattern::class,
      PropertyConstraint.MinSize::class,
      PropertyConstraint.MaxSize::class,
    )
  }

  // endregion

  // region STRING

  @Test
  fun `STRING includes MinLength, MaxLength and Pattern`() {
    val constraints = PropertyType.STRING.availableConstraints()
    assertThat(constraints).contains(
      PropertyConstraint.MinLength::class,
      PropertyConstraint.MaxLength::class,
      PropertyConstraint.Pattern::class,
    )
  }

  @Test
  fun `STRING does not include numeric or list constraints`() {
    val constraints = PropertyType.STRING.availableConstraints()
    assertThat(constraints).doesNotContain(
      PropertyConstraint.MinLong::class,
      PropertyConstraint.MaxLong::class,
      PropertyConstraint.MinDouble::class,
      PropertyConstraint.MaxDouble::class,
      PropertyConstraint.MinSize::class,
      PropertyConstraint.MaxSize::class,
    )
  }

  // endregion

  // region LIST

  @Test
  fun `LIST includes MinSize and MaxSize`() {
    val constraints = PropertyType.LIST.availableConstraints()
    assertThat(constraints).contains(PropertyConstraint.MinSize::class, PropertyConstraint.MaxSize::class)
  }

  @Test
  fun `LIST does not include numeric or string constraints`() {
    val constraints = PropertyType.LIST.availableConstraints()
    assertThat(constraints).doesNotContain(
      PropertyConstraint.MinLong::class,
      PropertyConstraint.MaxLong::class,
      PropertyConstraint.MinDouble::class,
      PropertyConstraint.MaxDouble::class,
      PropertyConstraint.MinLength::class,
      PropertyConstraint.MaxLength::class,
      PropertyConstraint.Pattern::class,
    )
  }

  // endregion

  // region types with only general constraints

  @Test
  fun `BOOLEAN has only general constraints`() {
    assertThat(PropertyType.BOOLEAN.availableConstraints())
      .containsExactlyInAnyOrderElementsOf(PropertyConstraint.GENERAL_CONSTRAINTS)
  }

  @Test
  fun `DATE has only general constraints`() {
    assertThat(PropertyType.DATE.availableConstraints())
      .containsExactlyInAnyOrderElementsOf(PropertyConstraint.GENERAL_CONSTRAINTS)
  }

  @Test
  fun `TIME has only general constraints`() {
    assertThat(PropertyType.TIME.availableConstraints())
      .containsExactlyInAnyOrderElementsOf(PropertyConstraint.GENERAL_CONSTRAINTS)
  }

  @Test
  fun `DATETIME has only general constraints`() {
    assertThat(PropertyType.DATETIME.availableConstraints())
      .containsExactlyInAnyOrderElementsOf(PropertyConstraint.GENERAL_CONSTRAINTS)
  }

  @Test
  fun `REF has only general constraints`() {
    assertThat(PropertyType.REF.availableConstraints())
      .containsExactlyInAnyOrderElementsOf(PropertyConstraint.GENERAL_CONSTRAINTS)
  }

  @Test
  fun `OBJECT has only general constraints`() {
    assertThat(PropertyType.OBJECT.availableConstraints())
      .containsExactlyInAnyOrderElementsOf(PropertyConstraint.GENERAL_CONSTRAINTS)
  }

  // endregion
}
