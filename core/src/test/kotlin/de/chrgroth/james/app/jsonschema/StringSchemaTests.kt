package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toStringProperty
import org.junit.jupiter.api.Test

class StringSchemaTests: AnnotationsBaseTests() {

    override val toPropertyConverter: (String) -> String
        get() = { it.toStringProperty() }

    override val expectedDetails = "testPropertyName"

    @Test
    fun `title not allowed`() =
        """ $prefixForAnnotationTests "title": "Some title" """.toStringProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_TITLE_MANDATORY_FOR_TOP_LEVEL_NOT_SUPPORTED_FOR_EVERYTHING_ELSE,
                details = "testPropertyName"
            )
        )

    @Test
    fun `default allowed`() =
        """ $prefixForAnnotationTests "default": "Some value" """.toStringProperty().validateJsonSchema().expectSuccess()

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
    fun `max length negative`() {
        val schemaContent = """ "maxLength": -1 """.toStringProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_OR_ZERO_MAX_LENGTH,
                details = "testPropertyName",
            ),
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_STRING_PROPERTY_MAX_LENGTH_SMALLER_MIN_LENGTH,
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

    @Test
    fun `empty enum values`() {
        val schemaContent = """ "enum": [ ] """.toStringProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISSING,
                details = "testPropertyName"
            )
        )
    }

    @Test
    fun `non string enum values`() {
        val schemaContent = """ "enum": [ "foo", true, 13 ] """.toStringProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISMATCHING_TYPE,
                details = "testPropertyName"
            )
        )
    }

    @Test
    fun `valid enum values`() {
        val schemaContent = """ "enum": [ "foo", "bar" ] """.toStringProperty()
        schemaContent.validateJsonSchema().expectSuccess()
    }
}
