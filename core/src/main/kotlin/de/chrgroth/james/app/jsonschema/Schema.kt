package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.combine
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.cast

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

        val commonAnnotationsErrors = objectSchema.validateCommonAnnotations(null)
        val objectSchemaErrors = objectSchema.validateDefinition()
        val stringPropertyErrors = objectSchema.validateStringProperties()
        val numberPropertyErrors = objectSchema.validateNumberProperties()
        val booleanPropertyErrors = objectSchema.validateBooleanProperties()
        val arrayPropertyErrors = objectSchema.validateArrayProperties()
        val combinedPropertyErrors = objectSchema.validateCombinedProperties()

        @Suppress("UNCHECKED_CAST")
        val errors = commonAnnotationsErrors
            .combine(objectSchemaErrors)
            .combine(stringPropertyErrors as Errors<ObjectSchema>?)
            .combine(numberPropertyErrors as Errors<ObjectSchema>?)
            .combine(booleanPropertyErrors as Errors<ObjectSchema>?)
            .combine(arrayPropertyErrors as Errors<ObjectSchema>?)
            .combine(combinedPropertyErrors as Errors<ObjectSchema>?)

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
            ?: return Error(AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NULL, null)

        // ignored properties that are not keywords of a schema
        when (schema) {
            !is ObjectSchema -> Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_IS_NOT_OBJECT_SCHEMA,
                details = schema.javaClass.simpleName
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

internal fun <PropertyType : Any> ObjectSchema.filterProperties(expectedSchemaType: KClass<PropertyType>) = propertySchemas
    .filter { propertyDef -> expectedSchemaType.isInstance(propertyDef.value) }
    .map { propertyDef -> propertyDef.key to expectedSchemaType.cast(propertyDef.value) }
