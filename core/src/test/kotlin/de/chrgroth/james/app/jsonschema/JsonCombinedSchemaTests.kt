package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.toIntegerProperty
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class JsonCombinedSchemaTests {

    @Test
    @Disabled
    fun `integer property with oneOf conditions`() =
        """ "oneOf": [ { "multipleOf": 5 }, { "multipleOf": 3 } ] """.toIntegerProperty().validateJsonSchema().expectErrors(
            Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISMATCHING_TYPE,
                details = "testPropertyName"
            )
        )
}
