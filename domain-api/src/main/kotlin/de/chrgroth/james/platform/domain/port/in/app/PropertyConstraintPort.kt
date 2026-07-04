package de.chrgroth.james.platform.domain.port.`in`.app

import de.chrgroth.james.platform.domain.error.PathedConstraintViolation
import de.chrgroth.james.platform.domain.error.PropertyConstraintViolation
import de.chrgroth.james.platform.domain.model.app.Property

interface PropertyConstraintPort {
  fun checkValue(property: Property, value: Any?, existingValues: List<Any?> = emptyList()): List<PropertyConstraintViolation>

  /** Same checks as [checkValue], but each violation is paired with the human-readable property path (e.g. "Address > Street") that produced it. */
  fun checkValueWithPaths(property: Property, value: Any?, existingValues: List<Any?> = emptyList()): List<PathedConstraintViolation>
}
