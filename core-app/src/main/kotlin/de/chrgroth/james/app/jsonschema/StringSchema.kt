package de.chrgroth.james.app.jsonschema

import arrow.core.NonEmptyList
import arrow.core.Validated
import arrow.core.ValidatedNel
import de.chrgroth.james.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.createValidation
import de.chrgroth.james.reduceWithFirstValue
import org.everit.json.schema.FormatValidator
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.StringSchema

internal fun ObjectSchema.validateStringProperties(): ValidatedNel<Error, Unit> =
    filterProperties(StringSchema::class)
        .mapNotNull { it.value.validateDefinition(propertyName = it.key) }
        .reduceWithFirstValue()

internal val allowedStringPropertyFormats = listOf(
    "date-time",
    "date",
    "time",
    "email",
    "uri",
    "regex"
)

internal val StringSchema.minLengthNullSafe get() = minLength ?: 0
internal val StringSchema.maxLengthNullSafe get() = maxLength ?: Int.MAX_VALUE

// see: https://json-schema.org/understanding-json-schema/reference/string.html
internal fun StringSchema.validateDefinition(propertyName: String): ValidatedNel<Error, Unit> {

    val commonAnnotationsValidation = validateCommonAnnotations(propertyName)

    val minLengthNegativeValidation = createValidation(
        errorCondition = minLengthNullSafe < 0,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_MIN_LENGTH,
        errorDetails = propertyName
    ) {}

    val maxLengthZeroOrNegativeValidation = createValidation(
        errorCondition = maxLengthNullSafe < 1,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_OR_ZERO_MAX_LENGTH,
        errorDetails = propertyName
    ) {}

    val maxLengthSmallerMinLengthValidation = createValidation(
        errorCondition = maxLengthNullSafe < minLengthNullSafe,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_MAX_LENGTH_SMALLER_MIN_LENGTH,
        errorDetails = propertyName
    ) {}

    // there is no chance to distinguish between unknown format and no/null format, we can only detect known but unsupported formats
    val unsupportedFormatValidation = createValidation(
        errorCondition = formatValidator != FormatValidator.NONE && !allowedStringPropertyFormats.contains(formatValidator.formatName()),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_UNSUPPORTED_FORMAT,
        errorDetails = "$propertyName: format=${formatValidator.formatName()}"
    ) {}

    val unprocessedPropertiesValidation = createValidation(
        errorCondition = unprocessedProperties.isNotEmpty(),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
        errorDetails = "$propertyName: $unprocessedProperties"
    ) {}

    return listOf(
        commonAnnotationsValidation,
        minLengthNegativeValidation,
        maxLengthZeroOrNegativeValidation,
        maxLengthSmallerMinLengthValidation,
        unsupportedFormatValidation,
        unprocessedPropertiesValidation
    ).reduceWithFirstValue()
}
