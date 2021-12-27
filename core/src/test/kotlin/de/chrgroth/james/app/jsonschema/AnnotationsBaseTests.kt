package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.app.AppErrorCodes
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import org.everit.json.schema.ObjectSchema
import org.junit.jupiter.api.Test

abstract class AnnotationsBaseTests {

    open val validDefinitionForAnnotationTests: String = ""
    abstract val toPropertyConverter: (String) -> String

    open val expectedDetails: String? = null

    val prefixForAnnotationTests: String
        get() {
            return if (validDefinitionForAnnotationTests.isBlank()) "" else "$validDefinitionForAnnotationTests, "
        }

    @Test
    fun `readOnly explicitly disabled`() {
        toPropertyConverter(""" $prefixForAnnotationTests "readOnly": false """).loadAsTopLevelObjectSchema().expectSuccess()
    }

    @Test
    fun `readOnly not allowed`() =
        toPropertyConverter(""" $prefixForAnnotationTests "readOnly": true """).loadAsTopLevelObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.APP_DATATYPE_SCHEMA_ANNOTATIONS_READ_ONLY_NOT_SUPPORTED,
                details = expectedDetails
            )
        )

    @Test
    fun `writeOnly explicitly disabled`() {
        toPropertyConverter(""" $prefixForAnnotationTests "writeOnly": false """).loadAsTopLevelObjectSchema().expectSuccess()
    }

    @Test
    fun `writeOnly not allowed`() =
        toPropertyConverter(""" $prefixForAnnotationTests "writeOnly": true """).loadAsTopLevelObjectSchema().expectErrors(
            Error(
                code = AppErrorCodes.APP_DATATYPE_SCHEMA_ANNOTATIONS_WRITE_ONLY_NOT_SUPPORTED,
                details = expectedDetails
            )
        )
}
