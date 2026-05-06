package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PropertyTypeTests {

  // region UniqueKey — available for scalar types, not for LIST or OBJECT

  @Test
  fun `scalar types include UniqueKey in availableConstraints`() {
    val scalarTypes = listOf(
      PropertyType.LONG,
      PropertyType.DOUBLE,
      PropertyType.BOOLEAN,
      PropertyType.STRING,
      PropertyType.DATE,
      PropertyType.TIME,
      PropertyType.DATETIME,
      PropertyType.DURATION,
      PropertyType.REF,
    )
    scalarTypes.forEach { type ->
      assertThat(type.availableConstraints())
        .describedAs("$type should include UniqueKey")
        .contains(PropertyConstraint.UniqueKey::class)
    }
  }

  @Test
  fun `LIST does not include UniqueKey`() {
    assertThat(PropertyType.LIST.availableConstraints())
      .doesNotContain(PropertyConstraint.UniqueKey::class)
  }

  @Test
  fun `OBJECT does not include UniqueKey`() {
    assertThat(PropertyType.OBJECT.availableConstraints())
      .doesNotContain(PropertyConstraint.UniqueKey::class)
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

  // region types with only UniqueKey

  @Test
  fun `BOOLEAN has only UniqueKey`() {
    assertThat(PropertyType.BOOLEAN.availableConstraints())
      .containsExactly(PropertyConstraint.UniqueKey::class)
  }

  @Test
  fun `DATE has only UniqueKey`() {
    assertThat(PropertyType.DATE.availableConstraints())
      .containsExactly(PropertyConstraint.UniqueKey::class)
  }

  @Test
  fun `TIME has only UniqueKey`() {
    assertThat(PropertyType.TIME.availableConstraints())
      .containsExactly(PropertyConstraint.UniqueKey::class)
  }

  @Test
  fun `DATETIME has only UniqueKey`() {
    assertThat(PropertyType.DATETIME.availableConstraints())
      .containsExactly(PropertyConstraint.UniqueKey::class)
  }

  @Test
  fun `REF has only UniqueKey`() {
    assertThat(PropertyType.REF.availableConstraints())
      .containsExactly(PropertyConstraint.UniqueKey::class)
  }

  @Test
  fun `DURATION has only UniqueKey`() {
    assertThat(PropertyType.DURATION.availableConstraints())
      .containsExactly(PropertyConstraint.UniqueKey::class)
  }

  // endregion

  // region OBJECT

  @Test
  fun `OBJECT has no available constraints`() {
    assertThat(PropertyType.OBJECT.availableConstraints()).isEmpty()
  }

  // endregion

  // region supportsComputedProperty

  @Test
  fun `scalar non-REF types support computed properties`() {
    val supportedTypes = listOf(
      PropertyType.LONG,
      PropertyType.DOUBLE,
      PropertyType.BOOLEAN,
      PropertyType.STRING,
      PropertyType.DATE,
      PropertyType.TIME,
      PropertyType.DATETIME,
      PropertyType.DURATION,
    )
    supportedTypes.forEach { type ->
      assertThat(type.supportsComputedProperty())
        .describedAs("$type should support computed properties")
        .isTrue()
    }
  }

  @Test
  fun `REF does not support computed properties`() {
    assertThat(PropertyType.REF.supportsComputedProperty()).isFalse()
  }

  @Test
  fun `LIST does not support computed properties`() {
    assertThat(PropertyType.LIST.supportsComputedProperty()).isFalse()
  }

  @Test
  fun `OBJECT does not support computed properties`() {
    assertThat(PropertyType.OBJECT.supportsComputedProperty()).isFalse()
  }

  // endregion
}
