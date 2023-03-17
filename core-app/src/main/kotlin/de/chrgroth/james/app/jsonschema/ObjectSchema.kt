package de.chrgroth.james.app.jsonschema

import arrow.core.ValidatedNel
import arrow.core.andThen
import de.chrgroth.james.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.createValidation
import de.chrgroth.james.reduceWithFirstValue
import org.everit.json.schema.ObjectSchema

// TODO #19 not sure if an id is needed at all: "${'$'}id": "${jsonSchemaIdFor(appId, version, name)}"
fun jsonObjectSchemaFor(/* TODO #19 appId: UUID, version: String?, */name: String, description: String, schemaContent: String) = """
{
  "title": "$name",
  "description": "$description",
  "type": "object",
  "additionalProperties": false,
$schemaContent
}
""".trimIndent()

internal fun ObjectSchema.validateTopLevelSchema(): ValidatedNel<Error, ObjectSchema> {
    val commonAnnotationsValidation = validateCommonAnnotations(null)
    val objectSchemaValidation = validateDefinition()
    val stringPropertyValidation = validateStringProperties()
    val numberPropertyValidation = validateNumberProperties()
    val booleanPropertyValidation = validateBooleanProperties()
    val arrayPropertyValidation = validateArrayProperties()
    val combinedPropertyValidation = validateCombinedProperties()

    return listOf(
        commonAnnotationsValidation,
        objectSchemaValidation,
        stringPropertyValidation,
        numberPropertyValidation,
        booleanPropertyValidation,
        arrayPropertyValidation,
        combinedPropertyValidation
    ).reduceWithFirstValue().map { this }
}

// see: https://json-schema.org/understanding-json-schema/reference/object.html
@Suppress("LongMethod", "ComplexMethod")
internal fun ObjectSchema.validateDefinition(): ValidatedNel<Error, Unit> {

    val commonAnnotationsValidation = validateCommonAnnotations(null)

    val minPropertiesValidation = createValidation(
        errorCondition = minProperties != null && minProperties > 0,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_MIN_PROPERTIES_NOT_SUPPORTED,
        errorDetails = null,
    ) {}

    val maxPropertiesValidation = createValidation(
        errorCondition = maxProperties != null && maxProperties > 0,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_MAX_PROPERTIES_NOT_SUPPORTED,
        errorDetails = null,
    ) {}

    val additionalPropertiesValidation = createValidation(
        errorCondition = permitsAdditionalProperties(),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_ADDITIONAL_PROPERTIES_NOT_SUPPORTED,
        errorDetails = null,
    ) {}

    @Suppress("DEPRECATION")
    val patternPropertiesValidation = createValidation(
        errorCondition = patternProperties != null && patternProperties.isNotEmpty(),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_PATTERN_PROPERTIES_NOT_SUPPORTED,
        errorDetails = null,
    ) {}

    val propertyNameSchemaValidation = createValidation(
        errorCondition = propertyNameSchema != null,
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_PROPERTY_NAME_SCHEMA_NOT_SUPPORTED,
        errorDetails = null,
    ) {}

    val propertyDependenciesValidation = createValidation(
        errorCondition = propertyDependencies != null && propertyDependencies.isNotEmpty(),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_PROPERTY_DEPENDENCIES_NOT_SUPPORTED,
        errorDetails = null,
    ) {}

    val schemaDependenciesValidation = createValidation(
        errorCondition = schemaDependencies != null && schemaDependencies.isNotEmpty(),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_SCHEMA_DEPENDENCIES_NOT_SUPPORTED,
        errorDetails = null,
    ) {}

    // only allows plain values for now, add references and objects later
    val invalidPropertyTypes = propertySchemas.filter { propertyDef ->
        !propertyDef.value.isValidPropertyType()
    }
    val invalidPropertyTypesValidation = createValidation(
        errorCondition = invalidPropertyTypes.isNotEmpty(),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_PROPERTIES_INVALID_TYPE,
        errorDetails = invalidPropertyTypes.map { "${it.key}=${it.value.javaClass.simpleName}" }.toList().toString()
    ) {}

    val requiredButNotExistingProperties = requiredProperties?.filter { !definesProperty(it) } ?: emptyList()
    val requiredButNotExistingPropertiesValidation = createValidation(
        errorCondition = requiredButNotExistingProperties.isNotEmpty(),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_REQUIRED_PROPERTIES_DO_NOT_EXIST,
        errorDetails = requiredButNotExistingProperties.sorted().toString()
    ) {}

    val unprocessedPropertiesValidation = createValidation(
        errorCondition = unprocessedProperties.isNotEmpty(),
        errorCode = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
        errorDetails = unprocessedProperties.toString(),
    ) {}

    return listOf(
        commonAnnotationsValidation,
        minPropertiesValidation,
        maxPropertiesValidation,
        additionalPropertiesValidation,
        patternPropertiesValidation,
        propertyNameSchemaValidation,
        propertyDependenciesValidation,
        schemaDependenciesValidation,
        invalidPropertyTypesValidation,
        requiredButNotExistingPropertiesValidation,
        unprocessedPropertiesValidation
    ).reduceWithFirstValue()
}
