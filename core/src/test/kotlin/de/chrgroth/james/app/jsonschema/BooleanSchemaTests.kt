package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toBooleanProperty
import org.everit.json.schema.ObjectSchema
import org.junit.jupiter.api.Test

class BooleanSchemaTests : AnnotationsBaseTests() {

    override val toPropertyConverter: (String) -> String
        get() = { it.toBooleanProperty() }

    override val expectedDetails = "testPropertyName"

    @Test
    fun `title not allowed`() =
        """ $prefixForAnnotationTests "title": "Some title" """.toBooleanProperty().loadAsTopLevelObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_TITLE_MANDATORY_FOR_TOP_LEVEL_NOT_SUPPORTED_FOR_EVERYTHING_ELSE,
                details = "testPropertyName"
            )
        )

    @Test
    fun `default allowed`() {
        """ $prefixForAnnotationTests "default": true """.toBooleanProperty().loadAsTopLevelObjectSchema().expectSuccess()
    }

    @Test
    fun `unprocessed properties in boolean property`() {
        val schemaContent = """ "bar": "baz" """.toBooleanProperty()
        schemaContent.loadAsTopLevelObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "testPropertyName: {bar=baz}"
            )
        )
    }
}
