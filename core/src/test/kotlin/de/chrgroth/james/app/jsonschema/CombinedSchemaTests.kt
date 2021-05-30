package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.toIntegerProperty
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

internal class CombinedSchemaTests {

    @Test
    fun `property with allOf composition not supported`() =
        """ "allOf": [ { "multipleOf": 5 }, { "minimum": 7 } ] """.toIntegerProperty().validateJsonSchema().expectErrors(
            Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
                details = "testPropertyName: [CombinedSchema, NumberSchema]"
            )
        )

    @Test
    fun `property with anyOf composition not supported`() =
        """ "anyOf": [ { "multipleOf": 5 }, { "multipleOf": 7 } ] """.toIntegerProperty().validateJsonSchema().expectErrors(
            Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
                details = "testPropertyName: [CombinedSchema, NumberSchema]"
            )
        )

    @Test
    fun `property with oneOf composition not supported`() =
        """ "oneOf": [ { "multipleOf": 5 }, { "multipleOf": 7 } ] """.toIntegerProperty().validateJsonSchema().expectErrors(
            Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
                details = "testPropertyName: [CombinedSchema, NumberSchema]"
            )
        )

    @Test
    fun `property with not condition not supported`() =
        """ "not": { "multipleOf": 7 } """.toIntegerProperty().validateJsonSchema().expectErrors(
            Maybe.Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
                details = "testPropertyName: [NotSchema, NumberSchema]"
            )
        )
}
