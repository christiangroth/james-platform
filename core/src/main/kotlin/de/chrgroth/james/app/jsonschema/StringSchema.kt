package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.combine
import org.everit.json.schema.FormatValidator
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.StringSchema

internal fun ObjectSchema.validateStringProperties() =
    filterProperties(StringSchema::class)
        .mapNotNull { it.value.validateDefinition(propertyName = it.key) }.combine()

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
internal fun StringSchema.validateDefinition(propertyName: String): Errors<StringSchema>? {

    val commonAnnotationsErrors = validateCommonAnnotations(propertyName)

    val minLengthNegativeError = if (minLengthNullSafe < 0) {
        Error<StringSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_MIN_LENGTH,
            details = propertyName
        )
    } else null

    val maxLengthZeroOrNegativeError = if (maxLengthNullSafe < 1) {
        Error<StringSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_OR_ZERO_MAX_LENGTH,
            details = propertyName
        )
    } else null

    val maxLengthSmallerMinLengthError = if (maxLengthNullSafe < minLengthNullSafe) {
        Error<StringSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_MAX_LENGTH_SMALLER_MIN_LENGTH,
            details = propertyName
        )
    } else null

    val patternUsedError = if (pattern != null) {
        Error<StringSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_PATTERN_INSTEAD_OF_FORMAT_REGEX,
            details = propertyName
        )
    } else null

    // there is no chance to distinguish between unknown format and no/null format, we can only detect known but unsupported formats
    val unsupportedFormatError =
        if (formatValidator != FormatValidator.NONE && !allowedStringPropertyFormats.contains(formatValidator.formatName())) {
            Error<StringSchema>(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_UNSUPPORTED_FORMAT,
                details = "$propertyName: format=${formatValidator.formatName()}"
            )
        } else null

    val unprocessedPropertiesError = if (unprocessedProperties.isNotEmpty()) {
        Error<StringSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
            details = "$propertyName: $unprocessedProperties"
        )
    } else null

    return commonAnnotationsErrors
        .combine(minLengthNegativeError)
        .combine(maxLengthZeroOrNegativeError)
        .combine(maxLengthSmallerMinLengthError)
        .combine(patternUsedError)
        .combine(unsupportedFormatError)
        .combine(unprocessedPropertiesError)
}
