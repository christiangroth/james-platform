package de.chrgroth.james

import de.chrgroth.james.app.AppDatatype
import de.chrgroth.james.app.AppDatatypeDraft
import de.chrgroth.james.app.AppErrorCodes
import org.everit.json.schema.ObjectSchema
import org.everit.json.schema.loader.SchemaLoader
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

fun AppDatatypeDraft.generateJsonSchema(appId: UUID) = jsonSchemaFor(
    //appId = appId,
    //version = null,
    name = name,
    description = description ?: "",
    schemaContent = schemaContent ?: "",
)

fun AppDatatype.generateJsonSchema(appId: UUID) = jsonSchemaFor(
    //appId = appId,
    //version = version.toString(),
    name = name,
    description = description ?: "",
    schemaContent = schemaContent ?: "",
)

// TODO #17 not sure why this is rejected: "${'$'}schema": "$SCHEMA_VERSION"
// TODO #17 not sure if an id is needed at all: "${'$'}id": "${jsonSchemaIdFor(appId, version, name)}"
fun jsonSchemaFor(/*appId: UUID, version: String?, */name: String, description: String, schemaContent: String) = """
{
  "title": "$name",
  "description": "$description",
  "type": "object",
$schemaContent
}
""".trimIndent()

// latest json schema release the library can handle :(
// latest at all: https://json-schema.org/draft/2020-12/schema
const val SCHEMA_VERSION = "http://json-schema.org/draft-07/schema"

// TODO #17 if ids are used then host needs to be added, but not in core project. Must also be resolvable later.
fun jsonSchemaIdFor(appId: UUID, version: String?, datatypeName: String) =
    "/apps/$appId/versions/${version ?: "SNAPSHOT"}/datatypes/$datatypeName.schema.json"

fun String.parseJsonSchema(): Maybe<ObjectSchema> {

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
        return when {
            schema.unprocessedProperties.isNotEmpty() -> Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNKNOWN_PROPERTIES,
                details = schema.unprocessedProperties,
            )
            schema !is ObjectSchema -> Maybe.Error(
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
