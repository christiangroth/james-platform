package de.chrgroth.james.platform.domain.app

import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.PropertyConstraint
import de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@Suppress("Unused")
class PropertyConstraintService : PropertyConstraintPort {

  override fun checkValue(property: Property, value: Any?, existingValues: List<Any?>): List<PropertyConstraintViolation> {
    val violations = mutableListOf<PropertyConstraintViolation>()
    for (constraint in property.constraints) {
      when (constraint) {
        PropertyConstraint.NOT_NULL -> if (value == null) violations += PropertyConstraintViolation.NOT_NULL_VIOLATION
        PropertyConstraint.UNIQUE_KEY -> if (existingValues.contains(value)) violations += PropertyConstraintViolation.UNIQUE_KEY_VIOLATION
      }
    }
    return violations
  }
}
