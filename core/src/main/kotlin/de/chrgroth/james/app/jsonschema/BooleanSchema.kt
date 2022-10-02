package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.combine
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.ObjectSchema

internal fun ObjectSchema.validateBooleanProperties() =
    filterProperties(BooleanSchema::class)
        .mapNotNull { it.value.validateDefinition(propertyName = it.key) }.combine()

// see: https://json-schema.org/understanding-json-schema/reference/boolean.html
internal fun BooleanSchema.validateDefinition(propertyName: String): Errors<BooleanSchema>? {

    val commonAnnotationsErrors = validateCommonAnnotations(propertyName)

    val unprocessedPropertiesError = if (unprocessedProperties.isNotEmpty()) {
        Error<BooleanSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
            details = "$propertyName: $unprocessedProperties"
        )
    } else null

    return commonAnnotationsErrors
        .combine(unprocessedPropertiesError)
}
