package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.parseDurationValue
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import jakarta.enterprise.context.ApplicationScoped
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.time.toJavaDuration

@ApplicationScoped
@Suppress("Unused")
class PropertyConstraintService : PropertyConstraintPort {

  override fun checkValue(property: Property, value: Any?, existingValues: List<Any?>): List<PropertyConstraintViolation> {
    val violations = mutableListOf<PropertyConstraintViolation>()
    for (constraint in property.constraints) {
      when (constraint) {
        is PropertyConstraint.UniqueKey -> if (existingValues.contains(value)) violations += PropertyConstraintViolation.UniqueKeyViolation
        is PropertyConstraint.MinLong -> if (value is Long && value < constraint.min) violations += PropertyConstraintViolation.MinValueViolation(constraint.min)
        is PropertyConstraint.MaxLong -> if (value is Long && value > constraint.max) violations += PropertyConstraintViolation.MaxValueViolation(constraint.max)
        is PropertyConstraint.StepLong -> if (value is Long && constraint.step != 0L && value % constraint.step != 0L) violations += PropertyConstraintViolation.StepViolation(constraint.step)
        is PropertyConstraint.MinDouble -> if (value is Double && value < constraint.min) violations += PropertyConstraintViolation.MinValueViolation(constraint.min)
        is PropertyConstraint.MaxDouble -> if (value is Double && value > constraint.max) violations += PropertyConstraintViolation.MaxValueViolation(constraint.max)
        is PropertyConstraint.StepDouble ->
          if (value is Double && constraint.step != 0.0 && !isMultipleOf(value, constraint.step)) violations += PropertyConstraintViolation.StepViolation(constraint.step)
        is PropertyConstraint.MinLength -> if (value is String && value.length < constraint.min) violations += PropertyConstraintViolation.MinLengthViolation(constraint.min)
        is PropertyConstraint.MaxLength -> if (value is String && value.length > constraint.max) violations += PropertyConstraintViolation.MaxLengthViolation(constraint.max)
        is PropertyConstraint.Pattern -> if (value is String && !Regex(constraint.regex).matches(value)) violations += PropertyConstraintViolation.PatternViolation(constraint.regex)
        is PropertyConstraint.MinSize -> if (value is List<*> && value.size < constraint.min) violations += PropertyConstraintViolation.MinSizeViolation(constraint.min)
        is PropertyConstraint.MaxSize -> if (value is List<*> && value.size > constraint.max) violations += PropertyConstraintViolation.MaxSizeViolation(constraint.max)
        is PropertyConstraint.MinDate -> parseOrNull(value) { LocalDate.parse(it) }?.let { if (it < constraint.min) violations += PropertyConstraintViolation.MinDateViolation(constraint.min) }
        is PropertyConstraint.MaxDate -> parseOrNull(value) { LocalDate.parse(it) }?.let { if (it > constraint.max) violations += PropertyConstraintViolation.MaxDateViolation(constraint.max) }
        is PropertyConstraint.MinTime -> parseOrNull(value) { LocalTime.parse(it) }?.let { if (it < constraint.min) violations += PropertyConstraintViolation.MinTimeViolation(constraint.min) }
        is PropertyConstraint.MaxTime -> parseOrNull(value) { LocalTime.parse(it) }?.let { if (it > constraint.max) violations += PropertyConstraintViolation.MaxTimeViolation(constraint.max) }
        is PropertyConstraint.MinDatetime ->
          parseOrNull(value) { LocalDateTime.parse(it) }?.let { if (it < constraint.min) violations += PropertyConstraintViolation.MinDatetimeViolation(constraint.min) }
        is PropertyConstraint.MaxDatetime ->
          parseOrNull(value) { LocalDateTime.parse(it) }?.let { if (it > constraint.max) violations += PropertyConstraintViolation.MaxDatetimeViolation(constraint.max) }
        is PropertyConstraint.MinDuration ->
          parseOrNull(value) { parseDurationValue(it)?.toJavaDuration() ?: error("invalid duration") }
            ?.let { if (it < constraint.min) violations += PropertyConstraintViolation.MinDurationViolation(constraint.min) }
        is PropertyConstraint.MaxDuration ->
          parseOrNull(value) { parseDurationValue(it)?.toJavaDuration() ?: error("invalid duration") }
            ?.let { if (it > constraint.max) violations += PropertyConstraintViolation.MaxDurationViolation(constraint.max) }
      }
    }
    val listItemType = property.listItemType
    if (property.type == PropertyType.LIST && listItemType != null && value is List<*>) {
      val itemProperty = property.copy(type = listItemType, constraints = property.itemConstraints)
      for (item in value) {
        violations += checkValue(itemProperty, item, emptyList())
      }
    }
    if (property.type == PropertyType.OBJECT && value is Map<*, *>) {
      for (nestedProperty in property.nestedProperties) {
        violations += checkValue(nestedProperty, value[nestedProperty.id.value], emptyList())
      }
    }
    return violations
  }

  private fun <T> parseOrNull(value: Any?, parse: (String) -> T): T? = if (value is String) runCatching { parse(value) }.getOrNull() else null

  private fun isMultipleOf(value: Double, step: Double): Boolean {
    val remainder = value % step
    return remainder < STEP_EPSILON || (step - remainder) < STEP_EPSILON
  }

  companion object {
    private const val STEP_EPSILON = 1e-9
  }
}
