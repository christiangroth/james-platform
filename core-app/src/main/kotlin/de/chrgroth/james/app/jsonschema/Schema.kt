package de.chrgroth.james.app.jsonschema

import arrow.core.ValidatedNel
import arrow.core.andThen
import de.chrgroth.james.Error
import de.chrgroth.james.app.AppErrorCodes
import org.everit.json.schema.ArraySchema
import org.everit.json.schema.BooleanSchema
import org.everit.json.schema.CombinedSchema
import org.everit.json.schema.EnumSchema
import org.everit.json.schema.NumberSchema
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.Schema
import org.everit.json.schema.StringSchema
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
internal fun String.parseToObjectSchema(): ValidatedNel<Error, ObjectSchema> {
    return parseJsonSchema().andThen { it.validateTopLevelSchema() }
}

internal fun String.parseJsonSchema(): ValidatedNel<Error, ObjectSchema> {

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
            ?: return Error(code = AppErrorCodes.DATATYPE_SCHEMA_NULL, details = null)

        // ignored properties that are not keywords of a schema
        when (schema) {
            !is ObjectSchema -> Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_IS_NOT_OBJECT_SCHEMA,
                details = schema.javaClass.simpleName
            )

            else -> Result(schema)
        }
    } else {
        Error(
            code = AppErrorCodes.DATATYPE_SCHEMA_INVALID,
            details = loadSchemaResult.exceptionOrNull()?.message,
        )
    }
}

internal fun Schema.isValidPropertyType() = when (this) {
    is ArraySchema -> true
    is BooleanSchema -> true
    is EnumSchema -> true
    is NumberSchema -> true
    is StringSchema -> true
    is CombinedSchema -> true
    else -> false
}

internal fun <PropertyType : Any> ObjectSchema.filterProperties(expectedSchemaType: KClass<PropertyType>) =
    propertySchemas.filterProperties(expectedSchemaType)

internal fun <PropertyType : Any> Map<String, Schema>.filterProperties(expectedSchemaType: KClass<PropertyType>) =
    filter { propertyDef -> expectedSchemaType.isInstance(propertyDef.value) }
        .map { propertyDef -> propertyDef.key to expectedSchemaType.cast(propertyDef.value) }
        .toMap()
