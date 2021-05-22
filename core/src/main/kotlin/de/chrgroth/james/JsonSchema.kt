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
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

fun AppDatatypeDraft.generateJsonSchema(/* TODO #19 appId: UUID */) = jsonSchemaFor(
    // TODO #19 appId = appId,
    // TODO #19 version = null,
    name = name,
    description = description ?: "",
    schemaContent = schemaContent ?: "",
)

fun AppDatatype.generateJsonSchema(/* TODO #19 appId: UUID */) = jsonSchemaFor(
    // TODO #19 appId = appId,
    // TODO #19 version = version.toString(),
    name = name,
    description = description ?: "",
    schemaContent = schemaContent ?: "",
)

// TODO #19 not sure if an id is needed at all: "${'$'}id": "${jsonSchemaIdFor(appId, version, name)}"
fun jsonSchemaFor(/* TODO #19 appId: UUID, version: String?, */name: String, description: String, schemaContent: String) = """
{
  "title": "$name",
  "description": "$description",
  "type": "object",
$schemaContent
}
""".trimIndent()

// TODO #18 not sure why this is rejected: "${'$'}schema": "$SCHEMA_VERSION"
// latest json schema release the library can handle :(
// latest at all: https://json-schema.org/draft/2020-12/schema
const val SCHEMA_VERSION = "http://json-schema.org/draft-07/schema"

// TODO #19 if ids are used then host needs to be added, but not in core project. Must also be resolvable later.
fun jsonSchemaIdFor(appId: UUID, version: String?, datatypeName: String) =
    "/apps/$appId/versions/${version ?: "SNAPSHOT"}/datatypes/$datatypeName.schema.json"

// see https://github.com/everit-org/json-schema
internal fun String.validateJsonSchema(): Maybe<ObjectSchema> {
    return parseJsonSchema().transform {

        // see: https://json-schema.org/understanding-json-schema/reference/object.html
        if(it.unprocessedProperties.isNotEmpty()) {
            return@transform Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNKNOWN_PROPERTIES,
                details = it.unprocessedProperties,
            )
        } else if (it.minProperties != null && it.minProperties > 0) {
            return@transform Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_MIN_PROPERTIES_NOT_SUPPORTED
            )
        } else if (it.maxProperties != null && it.maxProperties > 0) {
            return@transform Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_MAX_PROPERTIES_NOT_SUPPORTED
            )
        } else if (it.permitsAdditionalProperties()) {
            return@transform Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ADDITIONAL_PROPERTIES_NOT_SUPPORTED
            )
        }

        // only allows plain values for now, add references and objects later
        val invalidPropertyTypes = it.propertySchemas
            .filter { propertyDef -> !propertyDef.value.isValidPropertyType() }
        if(invalidPropertyTypes.isNotEmpty()) {
            return@transform Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_PROPERTIES_INVALID_TYPE, invalidPropertyTypes.keys)
        }

        // TODO #17 handle id and ref
        // see: https://json-schema.org/understanding-json-schema/structuring.html

        // TODO #17 handle combining and conditionals
        // see: https://json-schema.org/understanding-json-schema/reference/combining.html
        // see: https://json-schema.org/understanding-json-schema/reference/conditionals.html

        // TODO #17 validate string properties
        // see: https://json-schema.org/understanding-json-schema/reference/string.html
        val stringProperties = it.propertySchemas
            .filter { propertyDef -> propertyDef.value is StringSchema }
            .map { propertyDef -> propertyDef.key to propertyDef.value as StringSchema }
            .toMap()
        stringProperties.any { propertyDef ->
            propertyDef.value.unprocessedProperties
            propertyDef.value.requireString()
            false
        }

        // TODO #17 validate number properties
        // see: https://json-schema.org/understanding-json-schema/reference/numeric.html
        val numberProperties = it.propertySchemas
            .filter { propertyDef -> propertyDef.value is NumberSchema }
            .map { propertyDef -> propertyDef.key to propertyDef.value as NumberSchema }
            .toMap()
        numberProperties.any { propertyDef ->
            propertyDef.value.unprocessedProperties
            propertyDef.value.requiresInteger()
            propertyDef.value.isRequiresNumber
            false
        }

        // TODO #17 validate boolean properties
        // see: https://json-schema.org/understanding-json-schema/reference/boolean.html
        val booleanProperties = it.propertySchemas
            .filter { propertyDef -> propertyDef.value is BooleanSchema }
            .map { propertyDef -> propertyDef.key to propertyDef.value as BooleanSchema }
            .toMap()
        booleanProperties.any { propertyDef ->
            propertyDef.value.unprocessedProperties
            false
        }

        // TODO #17 validate enum properties (may be part of everyting!!?!?! how to handle this?)
        // see: https://json-schema.org/understanding-json-schema/reference/generic.html#enumerated-values
        val enumProperties = it.propertySchemas
            .filter { propertyDef -> propertyDef.value is EnumSchema }
            .map { propertyDef -> propertyDef.key to propertyDef.value as EnumSchema }
            .toMap()
        enumProperties.any { propertyDef ->
            propertyDef.value.unprocessedProperties
            propertyDef.value.possibleValues
            propertyDef.value.possibleValuesAsList
            false
        }

        // TODO #17 validate array properties
        // see: https://json-schema.org/understanding-json-schema/reference/array.html
        val arrayProperties = it.propertySchemas
            .filter { propertyDef -> propertyDef.value is ArraySchema }
            .map { propertyDef -> propertyDef.key to propertyDef.value as ArraySchema }
            .toMap()
        arrayProperties.any { propertyDef ->
            propertyDef.value.unprocessedProperties
            propertyDef.value.allItemSchema
            propertyDef.value.containedItemSchema
            propertyDef.value.itemSchemas
            propertyDef.value.schemaOfAdditionalItems
            propertyDef.value.permitsAdditionalItems()
            propertyDef.value.requiresArray()
            false
        }

        // TODO #17 handle title / description?
        // TODO #17 handle default?
        // TODO #17 handle examples?
        // TODO #17 handle readOnly / writeOnly?
        // see: https://json-schema.org/understanding-json-schema/reference/generic.html#annotations

        // TODO #17 handle comments?
        // see: https://json-schema.org/understanding-json-schema/reference/generic.html#comments

        // TODO #17 might be ConstSchema, need similar handling to EnumSchema
        // see: https://json-schema.org/understanding-json-schema/reference/generic.html#constant-values

        // all valid, proceed
        Maybe.Result(it)
    }
}

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

// TODO #17 some kind of logging would be nice
internal fun String.parseJsonSchema(): Maybe<ObjectSchema> {

    val loadSchemaResult = runCatching {
        SchemaLoader.builder()
            .draftV7Support()
            .useDefaults(true)
            .schemaJson(JSONObject(JSONTokener(this)))
            .build()
            .load()
            .build()
    }

    if (loadSchemaResult.isSuccess) {
        val schema = loadSchemaResult.getOrNull()
            ?: return Maybe.Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NULL)

        // ignored properties that are not keywords of a schema
        return when (schema) {
            !is ObjectSchema -> Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_IS_NOT_OBJECT_SCHEMA,
                details = schema.javaClass
            )
            else -> Maybe.Result(schema)
        }
    } else {
        return Maybe.Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_INVALID,
            details = loadSchemaResult.exceptionOrNull(),
        )
    }
}

// TODO #17 define what's breaking
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
