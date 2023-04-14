package de.chrgroth.james.app.jsonschema

import de.chrgroth.james.DomainError
import de.chrgroth.james.app.AppDomainErrorCodes
import de.chrgroth.james.expectDomainErrors
import de.chrgroth.james.expectSuccess
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
        toPropertyConverter(""" $prefixForAnnotationTests "readOnly": false """).parseToObjectSchema().expectSuccess()
    }

    @Test
    fun `readOnly not allowed`() =
        toPropertyConverter(""" $prefixForAnnotationTests "readOnly": true """).parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_READ_ONLY_NOT_SUPPORTED,
                errorMessage = expectedDetails
            )
        )

    @Test
    fun `writeOnly explicitly disabled`() {
        toPropertyConverter(""" $prefixForAnnotationTests "writeOnly": false """).parseToObjectSchema().expectSuccess()
    }

    @Test
    fun `writeOnly not allowed`() =
        toPropertyConverter(""" $prefixForAnnotationTests "writeOnly": true """).parseToObjectSchema().expectDomainErrors(
            DomainError(
                code = AppDomainErrorCodes.DATATYPE_SCHEMA_ANNOTATIONS_WRITE_ONLY_NOT_SUPPORTED,
                errorMessage = expectedDetails
            )
        )
}
