package de.chrgroth.james

import de.chrgroth.james.app.AppDatatype
import de.chrgroth.james.app.AppDatatypeDraft
import de.chrgroth.james.app.AppErrorCodes
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.Schema
import org.everit.json.schema.StringSchema

fun AppDatatypeDraft.generateJsonSchema(/* TODO #19 appId: UUID */) = jsonObjectSchemaFor(
    // TODO #19 appId = appId,
    // TODO #19 version = null,
    name = name,
    description = description ?: "",
    schemaContent = schemaContent ?: "",
)

fun AppDatatype.generateJsonSchema(/* TODO #19 appId: UUID */) = jsonObjectSchemaFor(
    // TODO #19 appId = appId,
    // TODO #19 version = version.toString(),
    name = name,
    description = description ?: "",
    schemaContent = schemaContent ?: "",
)

// TODO #17 not sure abut enum schema. may it contain objects?
// TODO #17 not sure abut const schema
// TODO #17 check array does contain plain values only
private fun Schema.isValidPropertyType() = when (this) {
    is ArraySchema -> true
    is BooleanSchema -> true
    is EnumSchema -> true
    is NumberSchema -> true
    is StringSchema -> true
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

// TODO #17 tests
// see: https://json-schema.org/understanding-json-schema/reference/object.html
internal fun ObjectSchema.validate(): Maybe.Errors<ObjectSchema>? {
    val unprocessedPropertiesError: Maybe.Error<ObjectSchema>? = if (unprocessedProperties.isNotEmpty()) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
            details = unprocessedProperties,
        )
    } else null

    val minPropertiesError: Maybe.Error<ObjectSchema>? = if (minProperties != null && minProperties > 0) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_MIN_PROPERTIES_NOT_SUPPORTED
        )
    } else null

    val maxPropertiesError: Maybe.Error<ObjectSchema>? = if (maxProperties != null && maxProperties > 0) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_MAX_PROPERTIES_NOT_SUPPORTED
        )
    } else null

    val additionalPropertiesError: Maybe.Error<ObjectSchema>? = if (permitsAdditionalProperties()) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ADDITIONAL_PROPERTIES_NOT_SUPPORTED
        )
    } else null

    // only allows plain values for now, add references and objects later
    val invalidPropertyTypes = propertySchemas.filter { propertyDef ->
        !propertyDef.value.isValidPropertyType()
    }
    val invalidPropertyTypesError: Maybe.Error<ObjectSchema>? = if (invalidPropertyTypes.isNotEmpty()) {
        Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_PROPERTIES_INVALID_TYPE,
            details = invalidPropertyTypes.keys
        )
    } else null

    // TODO #17 handle id and ref
    // see: https://json-schema.org/understanding-json-schema/structuring.html

    // TODO #17 handle combining and conditionals
    // see: https://json-schema.org/understanding-json-schema/reference/combining.html
    // see: https://json-schema.org/understanding-json-schema/reference/conditionals.html

    // TODO #17 improve this code
    val errors = listOfNotNull(
        unprocessedPropertiesError,
        minPropertiesError,
        maxPropertiesError,
        additionalPropertiesError,
        invalidPropertyTypesError
    )
    return if (errors.isEmpty()) null else Maybe.Errors(errors = errors)
}
