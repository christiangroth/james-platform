package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import jakarta.enterprise.context.ApplicationScoped

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
        is PropertyConstraint.MinDouble -> if (value is Double && value < constraint.min) violations += PropertyConstraintViolation.MinValueViolation(constraint.min)
        is PropertyConstraint.MaxDouble -> if (value is Double && value > constraint.max) violations += PropertyConstraintViolation.MaxValueViolation(constraint.max)
        is PropertyConstraint.MinLength -> if (value is String && value.length < constraint.min) violations += PropertyConstraintViolation.MinLengthViolation(constraint.min)
        is PropertyConstraint.MaxLength -> if (value is String && value.length > constraint.max) violations += PropertyConstraintViolation.MaxLengthViolation(constraint.max)
        is PropertyConstraint.Pattern -> if (value is String && !Regex(constraint.regex).matches(value)) violations += PropertyConstraintViolation.PatternViolation(constraint.regex)
        is PropertyConstraint.MinSize -> if (value is List<*> && value.size < constraint.min) violations += PropertyConstraintViolation.MinSizeViolation(constraint.min)
        is PropertyConstraint.MaxSize -> if (value is List<*> && value.size > constraint.max) violations += PropertyConstraintViolation.MaxSizeViolation(constraint.max)
      }
    }
    val listItemType = property.listItemType
    if (property.type == PropertyType.LIST && listItemType != null && value is List<*>) {
      val itemProperty = property.copy(type = listItemType, constraints = property.itemConstraints)
      for (item in value) {
        violations += checkValue(itemProperty, item, emptyList())
      }
    }
    return violations
  }
}
