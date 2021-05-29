package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.toBooleanProperty
import org.junit.jupiter.api.Test

class JsonBooleanSchemaTests {

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
