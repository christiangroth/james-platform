package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.combine
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import kotlin.math.floor

internal fun ObjectSchema.validateNumberProperties() =
    filterProperties(NumberSchema::class)
        .mapNotNull { it.second.validateDefinition(propertyName = it.first) }.combine()

internal val NumberSchema.minimumNullSafe get() = minimum ?: Int.MIN_VALUE
internal val NumberSchema.exclusiveMinimumLimitNullSafe get() = exclusiveMinimumLimit ?: Int.MIN_VALUE
internal val NumberSchema.combinedMinimum get() = if (exclusiveMinimumLimit != null) exclusiveMinimumLimitNullSafe else minimumNullSafe

internal val NumberSchema.maximumNullSafe get() = maximum ?: Int.MAX_VALUE
internal val NumberSchema.exclusiveMaximumLimitNullSafe get() = exclusiveMaximumLimit ?: Int.MAX_VALUE
internal val NumberSchema.combinedMaximum get() = if (exclusiveMaximumLimit != null) exclusiveMaximumLimitNullSafe else maximumNullSafe

internal val NumberSchema.multipleOfNullSafe get() = multipleOf?.toDouble() ?: 0.0

// see: https://json-schema.org/understanding-json-schema/reference/numeric.html
internal fun NumberSchema.validateDefinition(propertyName: String): Errors<NumberSchema>? {

    val commonAnnotationsErrors = validateCommonAnnotations(propertyName)

    val minAndExclusiveMinError = if (minimum != null && exclusiveMinimumLimit != null) {
        Error<NumberSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MIN_AND_EXCLUSIVE_MIN_LIMIT,
            details = propertyName
        )
    } else null

    val maxAndExclusiveMaxError = if (maximum != null && exclusiveMaximumLimit != null) {
        Error<NumberSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_AND_EXCLUSIVE_MAX_LIMIT,
            details = propertyName
        )
    } else null

    val maxLimitSmallerMinLimitError = if (combinedMaximum.toLong() < combinedMinimum.toLong()) {
        Error<NumberSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
            details = propertyName
        )
    } else null

    val multipleOfZeroOrNegativeError = if (multipleOf != null && multipleOf.toDouble() <= 0) {
        Error<NumberSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_NEGATIVE_OR_ZERO,
            details = propertyName
        )
    } else null

    val floatingPointMultipleOfForIntegerValue =
        if (requiresInteger() && floor(multipleOfNullSafe) != multipleOfNullSafe) {
            Error<NumberSchema>(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_FLOATING_POINT_FOR_INTEGER,
                details = propertyName
            )
        } else null

    val unprocessedPropertiesError = if (unprocessedProperties.isNotEmpty()) {
        Error<NumberSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
            details = "$propertyName: $unprocessedProperties"
        )
    } else null

    return commonAnnotationsErrors
        .combine(minAndExclusiveMinError)
        .combine(maxAndExclusiveMaxError)
        .combine(maxLimitSmallerMinLimitError)
        .combine(multipleOfZeroOrNegativeError)
        .combine(floatingPointMultipleOfForIntegerValue)
        .combine(unprocessedPropertiesError)
}
