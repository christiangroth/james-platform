package de.chrgroth.james.platform.domain.imports

import de.chrgroth.james.platform.domain.model.app.PropertyType
import de.chrgroth.james.platform.domain.model.app.parseDurationValue

/** Converts a raw string value (from a source record or a static fallback/lookup value) into the type [de.chrgroth.james.platform.domain.port.`in`.app.PropertyConstraintPort] expects for the given target property type. Returns null for blank input. */
internal fun parseScalarValue(type: PropertyType, rawValue: String?): Any? {
  if (rawValue.isNullOrBlank()) return null
  return when (type) {
    PropertyType.LONG -> rawValue.toLongOrNull()
    PropertyType.DOUBLE -> rawValue.toDoubleOrNull()
    PropertyType.BOOLEAN -> rawValue.equals("true", ignoreCase = true)
    PropertyType.DURATION -> parseDurationValue(rawValue)
    else -> rawValue
  }
}
