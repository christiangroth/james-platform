package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.combine
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.Schema
import org.everit.json.schema.StringSchema

// see: https://json-schema.org/understanding-json-schema/reference/generic.html#annotations
internal fun <T : Schema> T.validateCommonAnnotations(propertyName: String?): Errors<T>? {

    val isTopLevelSchema = propertyName == null

    val hasTitle = title != null && title.isNotBlank()
    val titleError = if (isTopLevelSchema xor hasTitle) {
        Error<T>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_TITLE_ONLY_SUPPORTED_FOR_TOP_LEVEL,
            details = propertyName,
        )
    } else null

    val schemaTypeAllowsDefaultValue = this is StringSchema || this is NumberSchema || this is BooleanSchema
    val defaultValueError = if (defaultValue != null && !schemaTypeAllowsDefaultValue) {
        Error<T>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_DEFAULT_ONLY_SUPPORTED_BOOLEAN_NUMBER_STRING,
            details = propertyName
        )
    } else null

    val readOnlyError = if (isReadOnly != null && isReadOnly) {
        Error<T>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_READ_ONLY_NOT_SUPPORTED,
            details = propertyName,
        )
    } else null

    val writeOnlyError = if (isWriteOnly != null && isWriteOnly) {
        Error<T>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_WRITE_ONLY_NOT_SUPPORTED,
            details = propertyName,
        )
    } else null

    return titleError
        .combine(defaultValueError)
        .combine(readOnlyError)
        .combine(writeOnlyError)
}

// TODO #17 enum properties (can be used in all schema types!)
//  allow for number/integer, string, array in tuple mode, disallow for all others
// see: https://json-schema.org/understanding-json-schema/reference/generic.html#enumerated-values
//val enumProperties = objectSchema.propertySchemas
//    .filter { propertyDef -> propertyDef.value is EnumSchema }
//    .map { propertyDef -> propertyDef.key to propertyDef.value as EnumSchema }
//    .toMap()
//enumProperties.any { propertyDef ->
//    propertyDef.value.unprocessedProperties
//    propertyDef.value.possibleValues
//    propertyDef.value.possibleValuesAsList
//    false
//}
