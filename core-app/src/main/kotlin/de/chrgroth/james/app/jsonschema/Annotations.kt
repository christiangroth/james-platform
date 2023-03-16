package de.chrgroth.james.app.jsonschema

import arrow.core.ValidatedNel
import de.chrgroth.james.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.createValidation
import de.chrgroth.james.reduceWithFirstValue
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.Schema
import org.everit.json.schema.StringSchema

// see: https://json-schema.org/understanding-json-schema/reference/generic.html#annotations
internal fun <T : Schema> T.validateCommonAnnotations(propertyName: String?): ValidatedNel<Error, Unit> {

    val isTopLevelSchema = propertyName == null
    val hasTitle = title != null && title.isNotBlank()
    val titleValidation = createValidation(
        errorCondition = isTopLevelSchema xor hasTitle,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_TITLE_MANDATORY_FOR_TOP_LEVEL_NOT_SUPPORTED_FOR_EVERYTHING_ELSE,
        errorDetails = propertyName
    ) { }

    val schemaTypeAllowsDefaultValue = this is StringSchema || this is NumberSchema || this is BooleanSchema
    val defaultValueValidation = createValidation(
        errorCondition = defaultValue != null && !schemaTypeAllowsDefaultValue,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_DEFAULT_ONLY_SUPPORTED_BOOLEAN_NUMBER_STRING,
        errorDetails = propertyName
    ) {}

    val readOnlyValidation = createValidation(
        errorCondition = isReadOnly != null && isReadOnly,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_READ_ONLY_NOT_SUPPORTED,
        errorDetails = propertyName
    ) {}

    val writeOnlyValidation = createValidation(
        errorCondition = isWriteOnly != null && isWriteOnly,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_WRITE_ONLY_NOT_SUPPORTED,
        errorDetails = propertyName
    ) {}

    return listOf(titleValidation, defaultValueValidation, readOnlyValidation, writeOnlyValidation).reduceWithFirstValue()
}
