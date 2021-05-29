package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import org.junit.jupiter.api.Test

class JsonObjectSchemaTests {

    @Test
    fun `unknown properties are rejected`() {
        val schemaContent = """ "foo": "bar" """.toTestSchema()
        schemaContent.validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                details = "{foo=bar}",
            )
        )
    }
}
