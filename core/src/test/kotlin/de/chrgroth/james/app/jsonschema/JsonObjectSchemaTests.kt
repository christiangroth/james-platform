package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toPropertyInSchemaContent
import de.chrgroth.james.toTestSchema
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JsonObjectSchemaTests: JsonSchemaAnnotationsBaseTests() {

    override val toPropertyConverter: (String) -> String
        get() = { it.toTestSchema() }

    @Test
    fun `title allowed`() {
        val testSchema = "".toTestSchema()
        assertThat(testSchema).contains(""""title": """")
        testSchema.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `default not allowed`() =
        """ $prefixForAnnotationTests "default": { "foo": "bar" } """.toTestSchema().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_DEFAULT_ONLY_SUPPORTED_BOOLEAN_NUMBER_STRING,
                details = null
            )
        )

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
