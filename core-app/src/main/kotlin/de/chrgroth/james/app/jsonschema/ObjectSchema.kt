package de.chrgroth.james.app.jsonschema

import arrow.core.ValidatedNel
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.Error
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
    val commonAnnotationsErrors = validateCommonAnnotations(null)
    val objectSchemaErrors = validateDefinition()
    val stringPropertyErrors = validateStringProperties()
    val numberPropertyErrors = validateNumberProperties()
    val booleanPropertyErrors = validateBooleanProperties()
    val arrayPropertyErrors = validateArrayProperties()
    val combinedPropertyErrors = validateCombinedProperties()

    @Suppress("UNCHECKED_CAST")
    val errors = commonAnnotationsErrors
        .combine(objectSchemaErrors)
        .combine(stringPropertyErrors as Errors<ObjectSchema>?)
        .combine(numberPropertyErrors as Errors<ObjectSchema>?)
        .combine(booleanPropertyErrors as Errors<ObjectSchema>?)
        .combine(arrayPropertyErrors as Errors<ObjectSchema>?)
        .combine(combinedPropertyErrors as Errors<ObjectSchema>?)

    return errors ?: Result(this)
}

// see: https://json-schema.org/understanding-json-schema/reference/object.html
@Suppress("LongMethod", "ComplexMethod")
internal fun ObjectSchema.validateDefinition(): Errors<ObjectSchema>? {

    val commonAnnotationsErrors = validateCommonAnnotations(null)

    val minPropertiesError = if (minProperties != null && minProperties > 0) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_MIN_PROPERTIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val maxPropertiesError = if (maxProperties != null && maxProperties > 0) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_MAX_PROPERTIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val additionalPropertiesError = if (permitsAdditionalProperties()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_ADDITIONAL_PROPERTIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    @Suppress("DEPRECATION")
    val patternPropertiesError = if (patternProperties != null && patternProperties.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_PATTERN_PROPERTIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val propertyNameSchemaError = if (propertyNameSchema != null) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_PROPERTY_NAME_SCHEMA_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val propertyDependenciesError = if (propertyDependencies != null && propertyDependencies.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_PROPERTY_DEPENDENCIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val schemaDependenciesError = if (schemaDependencies != null && schemaDependencies.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_SCHEMA_DEPENDENCIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    // only allows plain values for now, add references and objects later
    val invalidPropertyTypes = propertySchemas.filter { propertyDef ->
        !propertyDef.value.isValidPropertyType()
    }
    val invalidPropertyTypesError = if (invalidPropertyTypes.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_PROPERTIES_INVALID_TYPE,
            details = invalidPropertyTypes.map { "${it.key}=${it.value.javaClass.simpleName}" }.toList().toString()
        )
    } else null

    val requiredButNotExistingProperties = requiredProperties?.filter { !definesProperty(it) } ?: emptyList()
    val requiredButNotExistingPropertiesError = if (requiredButNotExistingProperties.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_REQUIRED_PROPERTIES_DO_NOT_EXIST,
            details = requiredButNotExistingProperties.sorted().toString()
        )
    } else null

    val unprocessedPropertiesError = if (unprocessedProperties.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
            details = unprocessedProperties.toString(),
        )
    } else null

    return commonAnnotationsErrors
        .combine(minPropertiesError)
        .combine(maxPropertiesError)
        .combine(additionalPropertiesError)
        .combine(patternPropertiesError)
        .combine(propertyNameSchemaError)
        .combine(propertyDependenciesError)
        .combine(schemaDependenciesError)
        .combine(invalidPropertyTypesError)
        .combine(requiredButNotExistingPropertiesError)
        .combine(unprocessedPropertiesError)
}
