package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import org.junit.jupiter.api.Test

class JsonObjectSchemaTests {

    @Test
    fun `min properties in object definition`() {
        val schemaContent = """ "minProperties": 7 """.toTestSchema()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_MIN_PROPERTIES_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `max properties in object definition`() {
        val schemaContent = """ "maxProperties": 7 """.toTestSchema()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_MAX_PROPERTIES_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `additional properties in object definition`() {
        val schemaContent = "".toTestSchema().replace(""""additionalProperties": false""", """"additionalProperties": true""")
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ADDITIONAL_PROPERTIES_NOT_SUPPORTED,
                details = null,
            )
        )
    }

    @Test
    fun `invalid properties type in object definition`() {
        val schemaContent = "".toPropertyInSchemaContent("object")
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_PROPERTIES_INVALID_TYPE,
                details = "[testPropertyName]",
            )
        )
    }

    @Test
    fun `unprocessed properties in object definition`() {
        val schemaContent = """ "foo": "bar" """.toTestSchema()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "{foo=bar}",
            )
        )
    }
}
