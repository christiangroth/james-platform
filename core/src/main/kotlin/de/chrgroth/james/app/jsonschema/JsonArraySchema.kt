package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.combine
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.ObjectSchema

internal fun ObjectSchema.validateArrayProperties() =
    filterProperties(ArraySchema::class)
        .mapNotNull { it.second.validateDefinition(propertyName = it.first) }.combine()

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

    val modeError: Error<ArraySchema>? = if (mode == null) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_OR_TUPLE_MODE_UNDEFINED,
            details = propertyName
        )
    } else null

    val additionalItemsInListModeError: Error<ArraySchema>? = if (mode == ArraySchemaMode.LIST && schemaOfAdditionalItems != null) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_MODE_DEFINES_ADDITIONAL_ITEMS,
            details = propertyName
        )
    } else null

    val additionalItemsInTupleModeError: Error<ArraySchema>? = if (mode == ArraySchemaMode.TUPLE && schemaOfAdditionalItems != null) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_ADDITIONAL_ITEMS,
            details = propertyName
        )
    } else null

    val minItemsInListModeError: Error<ArraySchema>? = if (mode == ArraySchemaMode.LIST && minItemsNullSafe < 0) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_MIN_ITEMS,
            details = propertyName
        )
    } else null

    val maxItemsInListModeError: Error<ArraySchema>? = if (mode == ArraySchemaMode.LIST && maxItemsNullSafe < 1) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_OR_ZERO_MAX_ITEMS,
            details = propertyName
        )
    } else null

    val maxSmallerMinInListModeError: Error<ArraySchema>? = if (mode == ArraySchemaMode.LIST && maxItemsNullSafe < minItemsNullSafe) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_MAX_ITEMS_SMALLER_MIN_ITEMS,
            details = propertyName
        )
    } else null

    val minItemsInTupleModeError: Error<ArraySchema>? = if (mode == ArraySchemaMode.TUPLE && minItems != null) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MIN_ITEMS,
            details = propertyName
        )
    } else null

    val maxItemsInTupleModeError: Error<ArraySchema>? = if (mode == ArraySchemaMode.TUPLE && maxItems != null) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MAX_ITEMS,
            details = propertyName
        )
    } else null

    val containsError: Error<ArraySchema>? = if (containedItemSchema != null) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_DEFINES_CONTAINS,
            details = propertyName
        )
    } else null

    val containsInvalidTypeInListModeError: Error<ArraySchema>? =
        if (mode == ArraySchemaMode.LIST && !allItemSchema.isValidPropertyType()) {
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
                details = propertyName
            )
        } else null

    val containsInvalidTypeInTupleModeError: Error<ArraySchema>? =
        if (mode == ArraySchemaMode.TUPLE && itemSchemas.any { !it.isValidPropertyType() }) {
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
                details = propertyName
            )
        } else null

    val unprocessedPropertiesError: Error<ArraySchema>? = if (unprocessedProperties.isNotEmpty()) {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
            details = "$propertyName: $unprocessedProperties"
        )
    } else null

    return modeError
        .combine(additionalItemsInListModeError)
        .combine(additionalItemsInTupleModeError)
        .combine(minItemsInListModeError)
        .combine(maxItemsInListModeError)
        .combine(maxSmallerMinInListModeError)
        .combine(minItemsInTupleModeError)
        .combine(maxItemsInTupleModeError)
        .combine(containsError)
        .combine(containsInvalidTypeInListModeError)
        .combine(containsInvalidTypeInTupleModeError)
        .combine(unprocessedPropertiesError)
}
