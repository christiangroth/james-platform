package de.chrgroth.james.platform.domain.port.`in`.app

import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.Property

interface PropertyConstraintPort {
  fun checkValue(property: Property, value: Any?, existingValues: List<Any?> = emptyList()): List<PropertyConstraintViolation>
}
