package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.app.AppErrorCodes
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

// TODO #17 move all Json*Schema.kt files to package app.schema or app.jsonschema?? check references

// TODO #18 not sure why this is rejected: "${'$'}schema": "$SCHEMA_VERSION"
// latest json schema release the library can handle :(
// latest at all: https://json-schema.org/draft/2020-12/schema
const val SCHEMA_VERSION = "http://json-schema.org/draft-07/schema"

// TODO #19 if ids are used then host needs to be added, but not in core project. Must also be resolvable later.
fun jsonSchemaIdFor(appId: UUID, version: String?, datatypeName: String) =
    "/apps/$appId/versions/${version ?: "SNAPSHOT"}/datatypes/$datatypeName.schema.json"

// see https://github.com/everit-org/json-schema
internal fun String.validateJsonSchema(): Maybe<ObjectSchema> {
    return parseJsonSchema().transform { objectSchema ->

        val objectSchemaErrors = objectSchema.validate()
        val stringPropertyErrors = objectSchema.validateStringProperties()
        val numberPropertyErrors = objectSchema.validateNumberProperties()
        val booleanPropertyErrors = objectSchema.validateBooleanProperties()

        // TODO #17 validate array properties
        // see: https://json-schema.org/understanding-json-schema/reference/array.html
        val arrayProperties = objectSchema.propertySchemas
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

        // TODO #17 validate enum properties (may be part of everyting!!?!?! how to handle this?)
        // see: https://json-schema.org/understanding-json-schema/reference/generic.html#enumerated-values
        val enumProperties = objectSchema.propertySchemas
            .filter { propertyDef -> propertyDef.value is EnumSchema }
            .map { propertyDef -> propertyDef.key to propertyDef.value as EnumSchema }
            .toMap()
        enumProperties.any { propertyDef ->
            propertyDef.value.unprocessedProperties
            propertyDef.value.possibleValues
            propertyDef.value.possibleValuesAsList
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

        @Suppress("UNCHECKED_CAST")
        val errors = objectSchemaErrors
            .combine(stringPropertyErrors as Errors<ObjectSchema>?)
            .combine(numberPropertyErrors as Errors<ObjectSchema>?)
            .combine(booleanPropertyErrors as Errors<ObjectSchema>?)

        errors ?: Result(objectSchema)
    }
}

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

    return if (loadSchemaResult.isSuccess) {
        val schema = loadSchemaResult.getOrNull()
            ?: return Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NULL)

        // ignored properties that are not keywords of a schema
        when (schema) {
            !is ObjectSchema -> Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_IS_NOT_OBJECT_SCHEMA,
                details = schema.javaClass.name
            )
            else -> Result(schema)
        }
    } else {
        Error(
            code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_INVALID,
            details = loadSchemaResult.exceptionOrNull()?.message,
        )
    }
}

// TODO #17 when to use Class and when to use KClass??
@Suppress("UNCHECKED_CAST")
internal fun <PropertyType> ObjectSchema.filterProperties(expectedSchemaType: Class<PropertyType>) = propertySchemas
    .filter { propertyDef -> propertyDef.value.javaClass == expectedSchemaType }
    .map { propertyDef -> propertyDef.key to propertyDef.value as PropertyType }
