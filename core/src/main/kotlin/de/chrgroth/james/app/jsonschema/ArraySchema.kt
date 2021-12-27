package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.combine
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
internal fun ArraySchema.validateDefinition(propertyName: String): Errors<ArraySchema>? {

    val commonAnnotationsErrors = validateCommonAnnotations(propertyName)

    val modeError = if (mode == null) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_OR_TUPLE_MODE_UNDEFINED,
            details = propertyName
        )
    } else null

    val additionalItemsInListModeError = if (mode == ArraySchemaMode.LIST && schemaOfAdditionalItems != null) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_MODE_DEFINES_ADDITIONAL_ITEMS,
            details = propertyName
        )
    } else null

    val additionalItemsInTupleModeError = if (mode == ArraySchemaMode.TUPLE && schemaOfAdditionalItems != null) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_ADDITIONAL_ITEMS,
            details = propertyName
        )
    } else null

    val minItemsInListModeError = if (mode == ArraySchemaMode.LIST && minItemsNullSafe < 0) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_MIN_ITEMS,
            details = propertyName
        )
    } else null

    val maxItemsInListModeError = if (mode == ArraySchemaMode.LIST && maxItemsNullSafe < 1) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_OR_ZERO_MAX_ITEMS,
            details = propertyName
        )
    } else null

    val maxSmallerMinInListModeError = if (mode == ArraySchemaMode.LIST && maxItemsNullSafe < minItemsNullSafe) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_MAX_ITEMS_SMALLER_MIN_ITEMS,
            details = propertyName
        )
    } else null

    val minItemsInTupleModeError = if (mode == ArraySchemaMode.TUPLE && minItems != null) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MIN_ITEMS,
            details = propertyName
        )
    } else null

    val maxItemsInTupleModeError = if (mode == ArraySchemaMode.TUPLE && maxItems != null) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MAX_ITEMS,
            details = propertyName
        )
    } else null

    val containsError = if (containedItemSchema != null) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_DEFINES_CONTAINS,
            details = propertyName
        )
    } else null

    val allItemsSchemaNullOrEmptySchema = allItemSchema == null || allItemSchema is EmptySchema
    val containsNoTypeInListModeError =
        if (mode == ArraySchemaMode.LIST && allItemsSchemaNullOrEmptySchema) {
            Error<ArraySchema>(
                code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_NO_TYPES,
                details = propertyName
            )
        } else null

    val containsInvalidTypeInListModeError =
        if (mode == ArraySchemaMode.LIST && !allItemsSchemaNullOrEmptySchema && !allItemSchema.isValidPropertyType()) {
            Error<ArraySchema>(
                code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
                details = propertyName
            )
        } else null

    val containsNoTypeInTupleModeError =
        if (mode == ArraySchemaMode.TUPLE && itemSchemas.isNullOrEmpty()) {
            Error<ArraySchema>(
                code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_NO_TYPES,
                details = propertyName
            )
        } else null

    val containsInvalidTypeInTupleModeError =
        if (mode == ArraySchemaMode.TUPLE && itemSchemas.any { !it.isValidPropertyType() }) {
            Error<ArraySchema>(
                code = AppErrorCodes.APP_DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
                details = propertyName
            )
        } else null

    val unprocessedPropertiesError = if (unprocessedProperties.isNotEmpty()) {
        Error<ArraySchema>(
            code = AppErrorCodes.APP_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
            details = "$propertyName: $unprocessedProperties"
        )
    } else null

    return commonAnnotationsErrors
        .combine(modeError)
        .combine(additionalItemsInListModeError)
        .combine(additionalItemsInTupleModeError)
        .combine(minItemsInListModeError)
        .combine(maxItemsInListModeError)
        .combine(maxSmallerMinInListModeError)
        .combine(minItemsInTupleModeError)
        .combine(maxItemsInTupleModeError)
        .combine(containsError)
        .combine(containsNoTypeInListModeError)
        .combine(containsInvalidTypeInListModeError)
        .combine(containsNoTypeInTupleModeError)
        .combine(containsInvalidTypeInTupleModeError)
        .combine(unprocessedPropertiesError)
}
