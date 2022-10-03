package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.toBooleanProperty
import de.chrgroth.james.toIntegerProperty
import org.junit.jupiter.api.Test

internal class CombinedSchemaTests {

    @Test
    fun `property not supporting enum`() =
        """ "enum": [true, false] """.toBooleanProperty().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
                details = "testPropertyName: [BooleanSchema, EnumSchema]"
            )
        )

    @Test
    fun `property with allOf composition not supported`() =
        """ "allOf": [ { "multipleOf": 5 }, { "minimum": 7 } ] """.toIntegerProperty().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
                details = "testPropertyName: [CombinedSchema, NumberSchema]"
            )
        )

    @Test
    fun `property with anyOf composition not supported`() =
        """ "anyOf": [ { "multipleOf": 5 }, { "multipleOf": 7 } ] """.toIntegerProperty().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
                details = "testPropertyName: [CombinedSchema, NumberSchema]"
            )
        )

    @Test
    fun `property with oneOf composition not supported`() =
        """ "oneOf": [ { "multipleOf": 5 }, { "multipleOf": 7 } ] """.toIntegerProperty().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
                details = "testPropertyName: [CombinedSchema, NumberSchema]"
            )
        )

    @Test
    fun `property with not condition not supported`() =
        """ "not": { "multipleOf": 7 } """.toIntegerProperty().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_COMBINED_PROPERTY_ONLY_SUPPORTS_ENUM_USECASE_FOR_STRING_AND_NUMBER,
                details = "testPropertyName: [NotSchema, NumberSchema]"
            )
        )
}
