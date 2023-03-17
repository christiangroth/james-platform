package de.chrgroth.james.app.jsonschema

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.app.AppDomainErrorCodes
import de.chrgroth.james.createValidation
import de.chrgroth.james.reduceWithFirstValue
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.EmptySchema
import org.everit.json.schema.ObjectSchema

internal fun ObjectSchema.validateArrayProperties(): ValidatedNel<DomainError, Unit> =
    filterProperties(ArraySchema::class)
        .mapNotNull { it.value.validateDefinition(propertyName = it.key) }
        .reduceWithFirstValue()

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
internal fun ArraySchema.validateDefinition(propertyName: String): ValidatedNel<DomainError, Unit> {

    val commonAnnotationsValidation = validateCommonAnnotations(propertyName)

    val modeValidation = createValidation(
        errorCondition = mode == null,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_OR_TUPLE_MODE_UNDEFINED,
        errorDetails = propertyName
    ) {}

    val listAndAdditionalItemsValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.LIST && schemaOfAdditionalItems != null,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_MODE_DEFINES_ADDITIONAL_ITEMS,
        errorDetails = propertyName
    ) {}

    val tupleAndAdditionalItemsVaidation = createValidation(
        errorCondition = mode == ArraySchemaMode.TUPLE && schemaOfAdditionalItems != null,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_ADDITIONAL_ITEMS,
        errorDetails = propertyName
    ) {}

    val listMinItemsValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.LIST && minItemsNullSafe < 0,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_MIN_ITEMS,
        errorDetails = propertyName
    ) {}

    val listMaxItemsValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.LIST && maxItemsNullSafe < 1,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_OR_ZERO_MAX_ITEMS,
        errorDetails = propertyName
    ) {}

    val listMaxMinItemsValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.LIST && maxItemsNullSafe < minItemsNullSafe,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_MAX_ITEMS_SMALLER_MIN_ITEMS,
        errorDetails = propertyName
    ) {}

    val tupleMinItemsValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.TUPLE && minItems != null,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MIN_ITEMS,
        errorDetails = propertyName
    ) {}

    val tupleMaxItemsValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.TUPLE && maxItems != null,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MAX_ITEMS,
        errorDetails = propertyName
    ) {}

    val containedItemSchemaValidation = createValidation(
        errorCondition = containedItemSchema != null,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_DEFINES_CONTAINS,
        errorDetails = propertyName
    ) {}

    val allItemsSchemaNullOrEmptySchema = allItemSchema == null || allItemSchema is EmptySchema
    val listAllItemSchemaValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.LIST && allItemsSchemaNullOrEmptySchema,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_NO_TYPES,
        errorDetails = propertyName
    ) {}

    val listAllItemSchemaTypeValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.LIST && !allItemsSchemaNullOrEmptySchema && !allItemSchema.isValidPropertyType(),
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
        errorDetails = propertyName
    ) {}

    val tupleItemSchemaValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.TUPLE && itemSchemas.isNullOrEmpty(),
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_NO_TYPES,
        errorDetails = propertyName
    ) {}

    val tupleItemSchemaTypeValidation = createValidation(
        errorCondition = mode == ArraySchemaMode.TUPLE && itemSchemas.any { !it.isValidPropertyType() },
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
        errorDetails = propertyName
    ) {}

    val unprocessedPropertiesValidation = createValidation(
        errorCondition = unprocessedProperties.isNotEmpty(),
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
        errorDetails = "$propertyName: $unprocessedProperties"
    ) {}

    return listOf(
        commonAnnotationsValidation,
        modeValidation,
        listAndAdditionalItemsValidation,
        tupleAndAdditionalItemsVaidation,
        listMinItemsValidation,
        listMaxItemsValidation,
        listMaxMinItemsValidation,
        tupleMinItemsValidation,
        tupleMaxItemsValidation,
        containedItemSchemaValidation,
        listAllItemSchemaValidation,
        listAllItemSchemaTypeValidation,
        tupleItemSchemaValidation,
        tupleItemSchemaTypeValidation,
        unprocessedPropertiesValidation
    ).reduceWithFirstValue()
}
