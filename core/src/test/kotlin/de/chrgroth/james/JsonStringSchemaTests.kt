package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.app.AppErrorCodes
import org.assertj.core.api.Assertions.assertThat
import org.everit.json.schema.StringSchema
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #17 assert details
class JsonSchemaStringPropertyValidationTests {

    @Test
    fun `min length negative`() {
        val schemaContent = """{ "type": "string", "minLength": -1 }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(
            AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_MIN_LENGTH
        )
    }

    @Test
    fun `max length zero`() {
        val schemaContent = """{ "type": "string", "maxLength": 0 }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(
            AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_OR_ZERO_MAX_LENGTH
        )
    }

    @Test
    fun `max length smaller min length`() {
        val schemaContent = """{ "type": "string", "minLength": 5, "maxLength": 2 }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(
            AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_MAX_LENGTH_SMALLER_MIN_LENGTH
        )
    }

    @Test
    fun `max length equals min length`() {
        val schemaContent = """{ "type": "string", "minLength": 5, "maxLength": 5 }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Result::class.java)
    }

    @Test
    fun `valid min max length`() {
        val schemaContent = """{ "type": "string", "minLength": 5, "maxLength": 25 }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Result::class.java)
    }

    @Test
    fun `unknown format is ignored`() {
        val schemaContent = """{ "type": "string", "format": "unknown" }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Result::class.java)
    }

    @Test
    fun `unsupported format`() {
        val schemaContent = """{ "type": "string", "format": "json-pointer" }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(
            AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_UNSUPPORTED_FORMAT
        )
    }

    @Test
    fun `pattern instead of format`() {
        val schemaContent = """{ "type": "string", "pattern": "some-pattern" }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(
            AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_PATTERN_INSTEAD_OF_FORMAT_REGEX
        )
    }

    @Test
    fun `valid regex format`() {
        val schemaContent = """{ "type": "string", "format": "regex" }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Result::class.java)
    }

    @Test
    fun `valid string property`() {
        val schemaContent = """{ "type": "string" }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Result::class.java)
    }

    @Test
    fun `unprocessed properties in string property`() {
        val schemaContent = """{ "type": "string", "bar": "baz" }""".toPropertyInSchemaContent()
        val result = schemaContent.validateJsonSchema()
        assertThat(result).isInstanceOf(Error::class.java)
        assertThat((result as Error).code).isEqualTo(
            AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES
        )
    }
}
