package de.chrgroth.james.app.jsonschema

import arrow.core.ValidatedNel
import de.chrgroth.james.DomainError
import de.chrgroth.james.app.AppDomainErrorCodes
import de.chrgroth.james.createValidation
import de.chrgroth.james.reduceWithFirstValue
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import kotlin.math.floor

internal fun ObjectSchema.validateNumberProperties(): ValidatedNel<DomainError, Unit> =
    filterProperties(NumberSchema::class)
        .mapNotNull { it.value.validateDefinition(propertyName = it.key) }
        .reduceWithFirstValue()

internal val NumberSchema.minimumNullSafe get() = minimum ?: Int.MIN_VALUE
internal val NumberSchema.exclusiveMinimumLimitNullSafe get() = exclusiveMinimumLimit ?: Int.MIN_VALUE
internal val NumberSchema.combinedMinimum get() = if (exclusiveMinimumLimit != null) exclusiveMinimumLimitNullSafe else minimumNullSafe

internal val NumberSchema.maximumNullSafe get() = maximum ?: Int.MAX_VALUE
internal val NumberSchema.exclusiveMaximumLimitNullSafe get() = exclusiveMaximumLimit ?: Int.MAX_VALUE
internal val NumberSchema.combinedMaximum get() = if (exclusiveMaximumLimit != null) exclusiveMaximumLimitNullSafe else maximumNullSafe

internal val NumberSchema.multipleOfNullSafe get() = multipleOf?.toDouble() ?: 0.0

// see: https://json-schema.org/understanding-json-schema/reference/numeric.html
internal fun NumberSchema.validateDefinition(propertyName: String): ValidatedNel<DomainError, Unit> {

    val commonAnnotationsValidation = validateCommonAnnotations(propertyName)

    val minAndExclusiveMinValidation = createValidation(
        errorCondition = minimum != null && exclusiveMinimumLimit != null,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MIN_AND_EXCLUSIVE_MIN_LIMIT,
        errorDetails = propertyName
    ) {}

    val maxAndExclusiveMaxValidation = createValidation(
        errorCondition = maximum != null && exclusiveMaximumLimit != null,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_AND_EXCLUSIVE_MAX_LIMIT,
        errorDetails = propertyName
    ) {}

    val maxLimitSmallerMinLimitValidation = createValidation(
        errorCondition = combinedMaximum.toLong() < combinedMinimum.toLong(),
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
        errorDetails = propertyName
    ) {}

    val multipleOfZeroOrNegativeValidation = createValidation(
        errorCondition = multipleOf != null && multipleOf.toDouble() <= 0,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_NEGATIVE_OR_ZERO,
        errorDetails = propertyName
    ) {}

    val floatingPointMultipleOfForIntegerValueValidation = createValidation(
        errorCondition = requiresInteger() && floor(multipleOfNullSafe) != multipleOfNullSafe,
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_FLOATING_POINT_FOR_INTEGER,
        errorDetails = propertyName
    ) {}

    val unprocessedPropertiesValidation = createValidation(
        errorCondition = unprocessedProperties.isNotEmpty(),
        domainErrorCode = AppDomainErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
        errorDetails = "$propertyName: $unprocessedProperties"
    ) {}

    return listOf(
        commonAnnotationsValidation,
        minAndExclusiveMinValidation,
        maxAndExclusiveMaxValidation,
        maxLimitSmallerMinLimitValidation,
        multipleOfZeroOrNegativeValidation,
        floatingPointMultipleOfForIntegerValueValidation,
        unprocessedPropertiesValidation
    ).reduceWithFirstValue()
}
