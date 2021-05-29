package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.app.AppErrorCodes
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.ObjectSchema

internal fun ObjectSchema.validateBooleanProperties() =
    filterProperties(BooleanSchema::class.java)
        .mapNotNull { it.second.validateDefinition(propertyName = it.first) }.combine()

// see: https://json-schema.org/understanding-json-schema/reference/boolean.html
internal fun BooleanSchema.validateDefinition(propertyName: String): Errors<BooleanSchema>? =
    if (unprocessedProperties.isNotEmpty()) {
        Errors(
            listOf(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                    details = "$propertyName: $unprocessedProperties"
                )
            )
        )
    } else null
