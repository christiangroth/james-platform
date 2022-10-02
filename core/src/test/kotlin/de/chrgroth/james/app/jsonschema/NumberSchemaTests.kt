package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toIntegerProperty
import de.chrgroth.james.toNumberProperty
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class NumberSchemaTests : AnnotationsBaseTests() {

    override val toPropertyConverter: (String) -> String
        get() = { it.toNumberProperty() }

    override val expectedDetails = "testPropertyName"

    @TestFactory
    fun `title not allowed`() =
        testForIntegerAndNumberProperty(""" $prefixForAnnotationTests "title": "Some title" """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_TITLE_MANDATORY_FOR_TOP_LEVEL_NOT_SUPPORTED_FOR_EVERYTHING_ELSE,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `default allowed`() =
        testForIntegerAndNumberProperty(""" $prefixForAnnotationTests "default": 1 """) {
            it.parseToObjectSchema().expectSuccess()
        }

    @TestFactory
    fun `minimum and exclusiveMinimum`() =
        testForIntegerAndNumberProperty(""" "minimum": 2, "exclusiveMinimum": 2 """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MIN_AND_EXCLUSIVE_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `maximum and exclusiveMaximum`() =
        testForIntegerAndNumberProperty(""" "maximum": 2, "exclusiveMaximum": 2 """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_AND_EXCLUSIVE_MAX_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `maximum smaller minimum`() =
        testForIntegerAndNumberProperty(""" "minimum": 3, "maximum": 2 """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `exclusiveMaximum smaller minimum`() =
        testForIntegerAndNumberProperty(""" "minimum": 3, "exclusiveMaximum": 2 """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `maximum smaller exclusiveMinimum`() =
        testForIntegerAndNumberProperty(""" "exclusiveMinimum": 3, "maximum": 2 """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `exclusiveMaximum smaller exclusiveMinimum`() =
        testForIntegerAndNumberProperty(""" "exclusiveMinimum": 3, "exclusiveMaximum": 2 """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MAX_LIMIT_SMALLER_MIN_LIMIT,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `multipleOf is zero`() =
        testForIntegerAndNumberProperty(""" "multipleOf": 0 """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_NEGATIVE_OR_ZERO,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `multipleOf is negative`() =
        testForIntegerAndNumberProperty(""" "multipleOf": -1 """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_NEGATIVE_OR_ZERO,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `multipleOf of type integer`() =
        testForIntegerAndNumberProperty(""" "multipleOf": 2 """) {
            it.parseToObjectSchema().expectSuccess()
        }

    @Test
    fun `multipleOf of type float for integer property`() =
        """ "multipleOf": 0.5 """.toIntegerProperty().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_NUMBER_PROPERTY_MULTIPLE_OF_FLOATING_POINT_FOR_INTEGER,
                details = "testPropertyName"
            )
        )

    @Test
    fun `multipleOf of type float for number property`() {
        """ "multipleOf": 0.5 """.toNumberProperty().parseToObjectSchema().expectSuccess()
    }

    @TestFactory
    fun `unprocessed properties in integer property`() =
        testForIntegerAndNumberProperty(""" "bar": "baz" """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_CONTAINS_UNPROCESSED_PROPERTIES,
                    details = "testPropertyName: {bar=baz}"
                )
            )
        }

    @TestFactory
    fun `empty enum values`() =
        testForIntegerAndNumberProperty(""" "enum": [ ] """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISSING,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `non number enum values`() =
        testForIntegerAndNumberProperty(""" "enum": [ "foo", true, 13 ] """) {
            it.parseToObjectSchema().expectErrors(
                Error(
                    code = AppErrorCodes.DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISMATCHING_TYPE,
                    details = "testPropertyName"
                )
            )
        }

    @TestFactory
    fun `integer enum values`() =
        testForIntegerAndNumberProperty(""" "enum": [ 2, 3 ] """) {
            it.parseToObjectSchema().expectSuccess()
        }

    @Test
    fun `decimal enum values for integer property`() =
        """ "enum": [ 2, 3, 2.4 ] """.toIntegerProperty().parseToObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.DATATYPE_SCHEMA_ENUM_PROPERTY_VALUES_MISMATCHING_TYPE,
                details = "testPropertyName"
            )
        )

    @Test
    fun `decimal enum values for number property`() {
        """ "enum": [ 2, 3, 2.4 ] """.toNumberProperty().parseToObjectSchema().expectSuccess()
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
