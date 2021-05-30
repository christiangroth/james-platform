package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toArrayProperty
import org.junit.jupiter.api.Test

class ArraySchemaTests : AnnotationsBaseTests() {

    override val validDefinitionForAnnotationTests = """ "items": { "type": "number" } """
    override val toPropertyConverter: (String) -> String
        get() = { it.toArrayProperty() }

    override val expectedDetails = "testPropertyName"

    @Test
    fun `title not allowed`() =
        """ $prefixForAnnotationTests "title": "Some title" """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_TITLE_MANDATORY_FOR_TOP_LEVEL_NOT_SUPPORTED_FOR_EVERYTHING_ELSE,
                details = "testPropertyName"
            )
        )

    @Test
    fun `default not allowed`() =
        """ $prefixForAnnotationTests "default": [1, 2, 3] """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_DEFAULT_ONLY_SUPPORTED_BOOLEAN_NUMBER_STRING,
                details = "testPropertyName"
            )
        )

    @Test
    fun `valid list mode`() =
        """ "items": { "type": "number" }, "additionalItems": false """.toArrayProperty().validateJsonSchema().expectSuccess()

    @Test
    fun `list mode with additionalItems disabled explicitly`() =
        """ "items": { "type": "number" }, "additionalItems": false """.toArrayProperty().validateJsonSchema().expectSuccess()

    @Test
    fun `list mode with additionalItems defined`() =
        """ "items": { "type": "number" }, "additionalItems": { "type": "number" } """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_MODE_DEFINES_ADDITIONAL_ITEMS,
                details = "testPropertyName",
            )
        )

    @Test
    fun `valid tuple mode`() =
        """ "items": [ { "type": "number" }, { "type": "string" } ] """.toArrayProperty().validateJsonSchema().expectSuccess()

    @Test
    fun `tuple mode with additionalItems disabled explicitly`() =
        """ "items": [ { "type": "number" }, { "type": "string" } ], "additionalItems": false """.toArrayProperty().validateJsonSchema()
            .expectSuccess()

    @Test
    fun `tuple mode with additionalItems defined`() =
        """ "items": [ { "type": "number" }, { "type": "string" } ], "additionalItems": { "type": "number" } """.toArrayProperty()
            .validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_ADDITIONAL_ITEMS,
                    details = "testPropertyName",
                )
            )

    @Test
    fun `negative minItems in list mode`() =
        """ "items": { "type": "number" }, "minItems": -1 """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_MIN_ITEMS,
                details = "testPropertyName",
            )
        )

    @Test
    fun `zero maxItems in list mode`() =
        """ "items": { "type": "number" }, "maxItems": 0 """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_OR_ZERO_MAX_ITEMS,
                details = "testPropertyName",
            )
        )

    @Test
    fun `negative maxItems in list mode`() =
        """ "items": { "type": "number" }, "maxItems": -1 """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_NEGATIVE_OR_ZERO_MAX_ITEMS,
                details = "testPropertyName",
            ),
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_MAX_ITEMS_SMALLER_MIN_ITEMS,
                details = "testPropertyName",
            )
        )

    @Test
    fun `maxItems smaller minItems in list mode`() =
        """ "items": { "type": "number" }, "minItems": 3, "maxItems": 2 """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_MAX_ITEMS_SMALLER_MIN_ITEMS,
                details = "testPropertyName",
            )
        )

    @Test
    fun `minItems in tuple mode`() =
        """ "items": [ { "type": "number" }, { "type": "string" } ], "minItems": 2 """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MIN_ITEMS,
                details = "testPropertyName",
            )
        )

    @Test
    fun `maxItems in tuple mode`() =
        """ "items": [ { "type": "number" }, { "type": "string" } ], "maxItems": 2 """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_TUPLE_MODE_DEFINES_MAX_ITEMS,
                details = "testPropertyName",
            )
        )

    @Test
    fun `contains schema used`() =
        """ "contains": { "type": "number" } """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_OR_TUPLE_MODE_UNDEFINED,
                details = "testPropertyName",
            ),
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_DEFINES_CONTAINS,
                details = "testPropertyName",
            )
        )

    @Test
    fun `contains object in list mode`() =
        """ "items": { "type": "object" } """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
                details = "testPropertyName",
            )
        )

    @Test
    fun `contains object in tuple mode`() =
        """ "items": [ { "type": "number" }, { "type": "object" } ] """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_CONTAINS_INVALID_TYPE,
                details = "testPropertyName",
            )
        )

    @Test
    fun `unprocessed properties`() {
        """ "bar": "baz" """.toArrayProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ARRAY_PROPERTY_LIST_OR_TUPLE_MODE_UNDEFINED,
                details = "testPropertyName",
            ),
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "testPropertyName: {bar=baz}"
            )
        )
    }
}
