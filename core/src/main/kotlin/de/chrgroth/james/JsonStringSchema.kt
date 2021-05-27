package de.chrgroth.james

import de.chrgroth.james.app.AppErrorCodes
import org.everit.json.schema.FormatValidator
import org.everit.json.schema.StringSchema

internal val allowedStringPropertyFormats = listOf(
    "date-time",
    "date",
    "time",
    "email",
    "uri",
    "uri-reference",
    "uri-template",
    "regex"
)
internal val StringSchema.minLengthNullSafe get() = minLength ?: 0
internal val StringSchema.maxLengthNullSafe get() = maxLength ?: Int.MAX_VALUE

// see: https://json-schema.org/understanding-json-schema/reference/object.html
internal fun StringSchema.validate(propertyName: String): Maybe.Errors<Unit>? {

    val minLengthNegativeError: Maybe.Error<Unit>? = if (minLengthNullSafe < 0) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_MIN_LENGTH,
            details = propertyName
        )
    } else null

    val maxLengthZeroOrNegativeError: Maybe.Error<Unit>? = if(maxLengthNullSafe < 1) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_OR_ZERO_MAX_LENGTH,
            details = propertyName
        )
    } else null

    val maxLengthSmallerMinLengthError: Maybe.Error<Unit>? = if(maxLengthNullSafe < minLengthNullSafe) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_MAX_LENGTH_SMALLER_MIN_LENGTH,
            details = propertyName
        )
    } else null

    val patternUsedError: Maybe.Error<Unit>? = if(pattern != null) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_PATTERN_INSTEAD_OF_FORMAT_REGEX,
            details = propertyName
        )
    } else null

    val unsupportedFormatError: Maybe.Error<Unit>? =
        if (formatValidator != FormatValidator.NONE && !allowedStringPropertyFormats.contains(formatValidator.formatName())) {
            Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_UNSUPPORTED_FORMAT,
                details = "$propertyName: format=${formatValidator.formatName()}"
            )
        } else null

    val unprocessedPropertiesError: Maybe.Error<Unit>? = if(unprocessedProperties.isNotEmpty()) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
            details = "$propertyName: $unprocessedProperties"
        )
    } else null

    // TODO #17 improve this code
    val errors = listOfNotNull(
        minLengthNegativeError,
        maxLengthZeroOrNegativeError,
        maxLengthSmallerMinLengthError,
        patternUsedError,
        unsupportedFormatError,
        unprocessedPropertiesError,
    )
    return if (errors.isEmpty()) null else Maybe.Errors(errors = errors)
}
