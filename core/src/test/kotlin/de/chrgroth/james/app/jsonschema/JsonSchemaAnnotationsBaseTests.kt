package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import de.chrgroth.james.toArrayProperty
import org.junit.jupiter.api.Test

abstract class JsonSchemaAnnotationsBaseTests {

    open val validDefinitionForAnnotationTests: String = ""
    abstract val toPropertyConverter: (String) -> String

    open val expectedDetails: String? = null

    val prefixForAnnotationTests: String
        get() {
            return if (validDefinitionForAnnotationTests.isBlank()) "" else "$validDefinitionForAnnotationTests, "
        }

    @Test
    fun `readOnly explicitly disabled`() =
        toPropertyConverter(""" $prefixForAnnotationTests "readOnly": false """).validateJsonSchema().expectSuccess()

    @Test
    fun `readOnly not allowed`() =
        toPropertyConverter(""" $prefixForAnnotationTests "readOnly": true """).validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_READ_ONLY_NOT_SUPPORTED,
                details = expectedDetails
            )
        )

    @Test
    fun `writeOnly explicitly disabled`() =
        toPropertyConverter(""" $prefixForAnnotationTests "writeOnly": false """).validateJsonSchema().expectSuccess()

    @Test
    fun `writeOnly not allowed`() =
        toPropertyConverter(""" $prefixForAnnotationTests "writeOnly": true """).validateJsonSchema().expectErrors(
            Error(
                code = AppErrorCodes.UPDATE_DEVELOPMENT_VERSION_UPSERT_DATATYPE_SCHEMA_ANNOTATIONS_WRITE_ONLY_NOT_SUPPORTED,
                details = expectedDetails
            )
        )
}
