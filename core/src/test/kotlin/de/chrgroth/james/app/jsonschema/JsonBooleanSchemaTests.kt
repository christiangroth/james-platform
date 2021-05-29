package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toArrayProperty
import de.chrgroth.james.toBooleanProperty
import org.junit.jupiter.api.Test

class JsonBooleanSchemaTests : JsonSchemaAnnotationsBaseTests() {

    override val toPropertyConverter: (String) -> String
        get() = { it.toBooleanProperty() }

    override val expectedDetails = "testPropertyName"

    @Test
    fun `title not allowed`() =
        """ $prefixForAnnotationTests "title": "Some title" """.toBooleanProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_TITLE_ONLY_SUPPORTED_FOR_TOP_LEVEL,
                details = "testPropertyName"
            )
        )

    @Test
    fun `default allowed`() =
        """ $prefixForAnnotationTests "default": true """.toBooleanProperty().validateJsonSchema().expectSuccess()

    @Test
    fun `unprocessed properties in boolean property`() {
        val schemaContent = """ "bar": "baz" """.toBooleanProperty()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "testPropertyName: {bar=baz}"
            )
        )
    }
}
