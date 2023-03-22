package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.DomainError
import de.chrgroth.james.app.AppDomainErrorCodes
import de.chrgroth.james.expectDomainErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toStringProperty
import org.junit.jupiter.api.Test

class StringSchemaTests : AnnotationsBaseTests() {

    override val toPropertyConverter: (String) -> String
        get() = { it.toStringProperty() }

    override val expectedDetails = "testPropertyName"

    @Test
    fun `title not allowed`() =
        """ $prefixForAnnotationTests "title": "Some title" """.toStringProperty().parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_TITLE_MANDATORY_FOR_TOP_LEVEL_NOT_SUPPORTED_FOR_EVERYTHING_ELSE,
                details = "testPropertyName"
            )
        )

    @Test
    fun `default allowed`() {
        """ $prefixForAnnotationTests "default": "Some value" """.toStringProperty().parseToObjectSchema().expectSuccess()
    }

    @Test
    fun `min length negative`() {
        val schemaContent = """ "minLength": -1 """.toStringProperty()
        schemaContent.parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_MIN_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length zero`() {
        val schemaContent = """ "maxLength": 0 """.toStringProperty()
        schemaContent.parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_OR_ZERO_MAX_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length negative`() {
        val schemaContent = """ "maxLength": -1 """.toStringProperty()
        schemaContent.parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_NEGATIVE_OR_ZERO_MAX_LENGTH,
                details = "testPropertyName",
            ),
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_MAX_LENGTH_SMALLER_MIN_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length smaller min length`() {
        val schemaContent = """ "minLength": 5, "maxLength": 2 """.toStringProperty()
        schemaContent.parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_MAX_LENGTH_SMALLER_MIN_LENGTH,
                details = "testPropertyName",
            )
        )
    }

    @Test
    fun `max length equals min length`() {
        val schemaContent = """ "minLength": 5, "maxLength": 5 """.toStringProperty()
        schemaContent.parseToObjectSchema().expectSuccess()
    }

    @Test
    fun `valid min max length`() {
        val schemaContent = """ "minLength": 5, "maxLength": 25 """.toStringProperty()
        schemaContent.parseToObjectSchema().expectSuccess()
    }

    @Test
    fun `unknown format is ignored`() {
        val schemaContent = """ "format": "unknown" """.toStringProperty()
        schemaContent.parseToObjectSchema().expectSuccess()
    }

    @Test
    fun `known but unsupported format`() {
        val schemaContent = """ "format": "json-pointer" """.toStringProperty()
        schemaContent.parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_STRING_PROPERTY_UNSUPPORTED_FORMAT,
                details = "testPropertyName: format=json-pointer",
            )
        )
    }

    @Test
    fun `invalid pattern syntax`() {
        val schemaContent = """ "pattern": "^(\\([0-9]{3}\\)))?[0-9]{3}-[0-9]{4}${'$'}" """.toStringProperty()
        schemaContent.parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_INVALID,
                details = """Unmatched closing ')' near index 14
^(\([0-9]{3}\)))?[0-9]{3}-[0-9]{4}${'$'}
              ^""".trimIndent(),
            )
        )
    }

    @Test
    fun `valid regex format`() {
        val schemaContent = """ "format": "regex" """.toStringProperty()
        schemaContent.parseToObjectSchema().expectSuccess()
    }

    @Test
    fun `unprocessed properties in string property`() {
        val schemaContent = """ "bar": "baz" """.toStringProperty()
        schemaContent.parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "testPropertyName: {bar=baz}"
            )
        )
    }

    @Test
    fun `empty enum values`() {
        val schemaContent = """ "enum": [ ] """.toStringProperty()
        schemaContent.parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISSING,
                details = "testPropertyName"
            )
        )
    }

    @Test
    fun `non string enum values`() {
        val schemaContent = """ "enum": [ "foo", true, 13 ] """.toStringProperty()
        schemaContent.parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISMATCHING_TYPE,
                details = "testPropertyName"
            )
        )
    }

    @Test
    fun `valid enum values`() {
        val schemaContent = """ "enum": [ "foo", "bar" ] """.toStringProperty()
        schemaContent.parseToObjectSchema().expectSuccess()
    }
}
