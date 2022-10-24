package de.chrgroth.james.app.jsonschema

import arrow.core.Validated
import arrow.core.ValidatedNel
import de.chrgroth.james.Error
import de.chrgroth.james.app.AppErrorCodes
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.Schema
import org.everit.json.schema.StringSchema

// see: https://json-schema.org/understanding-json-schema/reference/generic.html#annotations
internal fun <T : Schema> T.validateCommonAnnotations(propertyName: String?): ValidatedNel<Error, Unit> {

    val isTopLevelSchema = propertyName == null
    val hasTitle = title != null && title.isNotBlank()
    if (isTopLevelSchema xor hasTitle) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_TITLE_MANDATORY_FOR_TOP_LEVEL_NOT_SUPPORTED_FOR_EVERYTHING_ELSE,
                details = propertyName,
            )
        )
    }

    val schemaTypeAllowsDefaultValue = this is StringSchema || this is NumberSchema || this is BooleanSchema
    if (defaultValue != null && !schemaTypeAllowsDefaultValue) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_DEFAULT_ONLY_SUPPORTED_BOOLEAN_NUMBER_STRING,
                details = propertyName
            )
        )
    }

    if (isReadOnly != null && isReadOnly) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_READ_ONLY_NOT_SUPPORTED,
                details = propertyName,
            )
        )
    }

    if (isWriteOnly != null && isWriteOnly) {
        return Validated.invalidNel(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_WRITE_ONLY_NOT_SUPPORTED,
                details = propertyName,
            )
        )
    }

    return Validated.validNel(Unit)
}
