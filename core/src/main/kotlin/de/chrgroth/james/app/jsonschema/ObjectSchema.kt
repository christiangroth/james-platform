package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.combine
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.CombinedSchema
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.Schema
import org.everit.json.schema.StringSchema

internal fun Schema.isValidPropertyType() = when (this) {
    is ArraySchema -> true
    is BooleanSchema -> true
    is EnumSchema -> true
    is NumberSchema -> true
    is StringSchema -> true
    is CombinedSchema -> true
    else -> false
}

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

// see: https://json-schema.org/understanding-json-schema/reference/object.html
@Suppress("LongMethod", "ComplexMethod")
internal fun ObjectSchema.validateDefinition(): Errors<ObjectSchema>? {

    val commonAnnotationsErrors = validateCommonAnnotations(null)

    val minPropertiesError = if (minProperties != null && minProperties > 0) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_MIN_PROPERTIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val maxPropertiesError = if (maxProperties != null && maxProperties > 0) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_MAX_PROPERTIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val additionalPropertiesError = if (permitsAdditionalProperties()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ADDITIONAL_PROPERTIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    @Suppress("DEPRECATION")
    val patternPropertiesError = if (patternProperties != null && patternProperties.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_PATTERN_PROPERTIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val propertyNameSchemaError = if (propertyNameSchema != null) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_PROPERTY_NAME_SCHEMA_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val propertyDependenciesError = if (propertyDependencies != null && propertyDependencies.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_PROPERTY_DEPENDENCIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    val schemaDependenciesError = if (schemaDependencies != null && schemaDependencies.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_SCHEMA_DEPENDENCIES_NOT_SUPPORTED,
            details = null,
        )
    } else null

    // only allows plain values for now, add references and objects later
    val invalidPropertyTypes = propertySchemas.filter { propertyDef ->
        !propertyDef.value.isValidPropertyType()
    }
    val invalidPropertyTypesError = if (invalidPropertyTypes.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_PROPERTIES_INVALID_TYPE,
            details = invalidPropertyTypes.map { "${it.key}=${it.value.javaClass.simpleName}" }.toList().toString()
        )
    } else null

    val requiredButNotExistingProperties = requiredProperties?.filter { !definesProperty(it) } ?: emptyList()
    val requiredButNotExistingPropertiesError = if (requiredButNotExistingProperties.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_REQUIRED_PROPERTIES_DO_NOT_EXIST,
            details = requiredButNotExistingProperties.sorted().toString()
        )
    } else null

    val unprocessedPropertiesError = if (unprocessedProperties.isNotEmpty()) {
        Error<ObjectSchema>(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
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

// TODO #17 define what's breaking
@Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
internal fun ObjectSchema.isBreakingTo(next: ObjectSchema): Boolean {
    // - property removed/renamed
    // - property type changed / more specialized
    // - property enum value removed
    // - property regex changed
    // - property min increased
    // - property max decreased
    // - property made required
    // - property required but default removed
    // - new required property without default
    return false
}
