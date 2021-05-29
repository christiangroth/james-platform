package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import org.junit.jupiter.api.Test

class JsonSchemaStringPropertyValidationTests {

    @Test
    fun `min length negative`() {
        val schemaContent = """{ "type": "string", "minLength": -1 }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_MIN_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length zero`() {
        val schemaContent = """{ "type": "string", "maxLength": 0 }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_OR_ZERO_MAX_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length smaller min length`() {
        val schemaContent = """{ "type": "string", "minLength": 5, "maxLength": 2 }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_MAX_LENGTH_SMALLER_MIN_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length equals min length`() {
        val schemaContent = """{ "type": "string", "minLength": 5, "maxLength": 5 }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `valid min max length`() {
        val schemaContent = """{ "type": "string", "minLength": 5, "maxLength": 25 }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `unknown format is ignored`() {
        val schemaContent = """{ "type": "string", "format": "unknown" }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `known but unsupported format`() {
        val schemaContent = """{ "type": "string", "format": "json-pointer" }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_UNSUPPORTED_FORMAT,
                details = "testPropertyName: format=json-pointer",
            )
        )
    }

    @Test
    fun `pattern instead of format`() {
        val schemaContent = """{ "type": "string", "pattern": "some-pattern" }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_PATTERN_INSTEAD_OF_FORMAT_REGEX,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `valid regex format`() {
        val schemaContent = """{ "type": "string", "format": "regex" }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `valid string property`() {
        val schemaContent = """{ "type": "string" }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `unprocessed properties in string property`() {
        val schemaContent = """{ "type": "string", "bar": "baz" }""".toPropertyInSchemaContent()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "testPropertyName: {bar=baz}"
            )
        )
    }
}
