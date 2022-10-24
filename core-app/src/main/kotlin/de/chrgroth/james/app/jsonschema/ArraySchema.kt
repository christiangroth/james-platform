package de.chrgroth.james.app.jsonschema

import arrow.core.Validated
import arrow.core.ValidatedNel
import de.chrgroth.james.Error
import de.chrgroth.james.app.AppErrorCodes
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.EmptySchema
import org.everit.json.schema.ObjectSchema

internal fun ObjectSchema.validateArrayProperties() =
    filterProperties(ArraySchema::class)
        .mapNotNull { it.value.validateDefinition(propertyName = it.key) }.combine()

internal val ArraySchema.minItemsNullSafe get() = minItems ?: 0
internal val ArraySchema.maxItemsNullSafe get() = maxItems ?: Int.MAX_VALUE
internal val ArraySchema.mode
    get() = when {
        allItemSchema != null && (itemSchemas == null || itemSchemas.isEmpty()) -> ArraySchemaMode.LIST
        allItemSchema == null && itemSchemas != null && itemSchemas.isNotEmpty() -> ArraySchemaMode.TUPLE
        else -> null
    }

enum class ArraySchemaMode { LIST, TUPLE }

// see: https://json-schema.org/understanding-json-schema/reference/array.html
@Suppress("LongMethod", "ComplexMethod")
internal fun ArraySchema.validateDefinition(propertyName: String): ValidatedNel<Error, Unit> {

    val commonAnnotationsErrors = validateCommonAnnotations(propertyName)

    if (mode == null) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_OR_TUPLE_MODE_UNDEFINED,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.LIST && schemaOfAdditionalItems != null) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_MODE_DEFINES_ADDITIONAL_ITEMS,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.TUPLE && schemaOfAdditionalItems != null) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_ADDITIONAL_ITEMS,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.LIST && minItemsNullSafe < 0) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_MIN_ITEMS,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.LIST && maxItemsNullSafe < 1) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_OR_ZERO_MAX_ITEMS,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.LIST && maxItemsNullSafe < minItemsNullSafe) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_MAX_ITEMS_SMALLER_MIN_ITEMS,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.TUPLE && minItems != null) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MIN_ITEMS,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.TUPLE && maxItems != null) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MAX_ITEMS,
                details = propertyName
            )
        )
    }

    if (containedItemSchema != null) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_DEFINES_CONTAINS,
                details = propertyName
            )
        )
    }

    val allItemsSchemaNullOrEmptySchema = allItemSchema == null || allItemSchema is EmptySchema
    if (mode == ArraySchemaMode.LIST && allItemsSchemaNullOrEmptySchema) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_NO_TYPES,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.LIST && !allItemsSchemaNullOrEmptySchema && !allItemSchema.isValidPropertyType()) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.TUPLE && itemSchemas.isNullOrEmpty()) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_NO_TYPES,
                details = propertyName
            )
        )
    }

    if (mode == ArraySchemaMode.TUPLE && itemSchemas.any { !it.isValidPropertyType() }) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
                details = propertyName
            )
        )
    }

    if (unprocessedProperties.isNotEmpty()) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "$propertyName: $unprocessedProperties"
            )
        )
    }

    return Validated.validNel(Unit)
}
