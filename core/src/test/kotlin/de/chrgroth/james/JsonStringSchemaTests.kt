package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import org.junit.jupiter.api.Test

class JsonStringSchemaTests {

    @Test
    fun `min length negative`() {
        val schemaContent = """ "minLength": -1 """.toStringProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_MIN_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length zero`() {
        val schemaContent = """ "maxLength": 0 """.toStringProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_OR_ZERO_MAX_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length smaller min length`() {
        val schemaContent = """ "minLength": 5, "maxLength": 2 """.toStringProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_MAX_LENGTH_SMALLER_MIN_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length equals min length`() {
        val schemaContent = """ "minLength": 5, "maxLength": 5 """.toStringProperty()
        schemaContent.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `valid min max length`() {
        val schemaContent = """ "minLength": 5, "maxLength": 25 """.toStringProperty()
        schemaContent.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `unknown format is ignored`() {
        val schemaContent = """ "format": "unknown" """.toStringProperty()
        schemaContent.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `known but unsupported format`() {
        val schemaContent = """ "format": "json-pointer" """.toStringProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_UNSUPPORTED_FORMAT,
                details = "testPropertyName: format=json-pointer",
            )
        )
    }

    @Test
    fun `pattern instead of format`() {
        val schemaContent = """ "pattern": "some-pattern" """.toStringProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_PATTERN_INSTEAD_OF_FORMAT_REGEX,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `valid regex format`() {
        val schemaContent = """ "format": "regex" """.toStringProperty()
        schemaContent.validateJsonSchema().expectSuccess()
    }

    @Test
    fun `unprocessed properties in string property`() {
        val schemaContent = """ "bar": "baz" """.toStringProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "testPropertyName: {bar=baz}"
            )
        )
    }
}
