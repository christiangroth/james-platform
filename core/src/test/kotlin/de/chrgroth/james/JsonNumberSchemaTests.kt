package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class JsonNumberSchemaTests {

    @TestFactory
    fun `minimum and exclusiveMinimum`() =
        testForIntegerAndNumberProperty(""" "minimum": 2, "exclusiveMinimum": 2 """) {
            it.validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MIN_AND_EXCLUSIVE_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `maximum and exclusiveMaximum`() =
        testForIntegerAndNumberProperty(""" "maximum": 2, "exclusiveMaximum": 2 """) {
            it.validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_AND_EXCLUSIVE_MAX_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `maximum smaller minimum`() =
        testForIntegerAndNumberProperty(""" "minimum": 3, "maximum": 2 """) {
            it.validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `exclusiveMaximum smaller minimum`() =
        testForIntegerAndNumberProperty(""" "minimum": 3, "exclusiveMaximum": 2 """) {
            it.validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `maximum smaller exclusiveMinimum`() =
        testForIntegerAndNumberProperty(""" "exclusiveMinimum": 3, "maximum": 2 """) {
            it.validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `exclusiveMaximum smaller exclusiveMinimum`() =
        testForIntegerAndNumberProperty(""" "exclusiveMinimum": 3, "exclusiveMaximum": 2 """) {
            it.validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `multipleOf is zero`() =
        testForIntegerAndNumberProperty(""" "multipleOf": 0 """) {
            it.validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_NEGATIVE_OR_ZERO,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `multipleOf is negative`() =
        testForIntegerAndNumberProperty(""" "multipleOf": -1 """) {
            it.validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_NEGATIVE_OR_ZERO,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `multipleOf of type integer`() =
        testForIntegerAndNumberProperty(""" "multipleOf": 2 """) {
            it.validateJsonSchema().expectSuccess()
        }

    @Test
    fun `multipleOf of type float for integer property`() =
        """ "multipleOf": 0.5 """.toIntegerProperty().validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_FLOATING_POINT_FOR_INTEGER,
                details = "testPropertyName"
            )
        )

    @Test
    fun `multipleOf of type float for number property`() =
        """ "multipleOf": 0.5 """.toNumberProperty().validateJsonSchema().expectSuccess()

    @TestFactory
    fun `unprocessed properties in integer property`() =
        testForIntegerAndNumberProperty(""" "bar": "baz" """) {
            it.validateJsonSchema().expectErrors(
                Error(
                    code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                    details = "testPropertyName: {bar=baz}"
                )
            )
        }

    private fun testForIntegerAndNumberProperty(propertySource: String, testLogic: (String) -> Unit): Collection<DynamicTest> {
        val baseDisplayName = Thread.currentThread().stackTrace[2].methodName
        return listOf(
            DynamicTest.dynamicTest("$baseDisplayName (integer)") {
                testLogic(propertySource.toIntegerProperty())
            },
            DynamicTest.dynamicTest("$baseDisplayName (number)") {
                testLogic(propertySource.toNumberProperty())
            }
        )
    }
}
