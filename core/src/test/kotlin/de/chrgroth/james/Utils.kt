package de.chrgroth.james

import org.assertj.core.api.Assertions

internal fun String.toStringProperty() = toPropertyInSchemaContent("string")
internal fun String.toBooleanProperty() = toPropertyInSchemaContent("boolean")
internal fun String.toIntegerProperty() = toPropertyInSchemaContent("integer")
internal fun String.toNumberProperty() = toPropertyInSchemaContent("number")

internal fun String.toPropertyInSchemaContent(propertyType: String): String = """|"properties": {
    |  "testPropertyName": {
    |    "type": "$propertyType"${if(this.isNotBlank()) "," else ""}
    |    $this
    |  }
    |}""".trimMargin().toTestSchema()

internal fun String.toTestSchema() =
    jsonObjectSchemaFor("FooType", "A test schema", this)

fun <T> Maybe<T>.expectSuccess() {
    Assertions.assertThat(this).isInstanceOf(Maybe.Result::class.java)
}

fun <T : Any> Maybe<T>.expectError(code: ErrorCode, details: String?) {
    Assertions.assertThat(this).isInstanceOf(Maybe.Error::class.java)
    val resultError = this as Maybe.Error
    Assertions.assertThat(resultError).isEqualTo(Maybe.Error<T>(code, details))
}

fun <T> Maybe<T>.expectErrors(vararg expectedErrors: Maybe.Error<T>) {
    Assertions.assertThat(this).isInstanceOf(Maybe.Errors::class.java)
    val resultErrors = this as Maybe.Errors
    for (expectedError in expectedErrors) {
        Assertions.assertThat(resultErrors.errors).contains(expectedError)
    }
    Assertions.assertThat(resultErrors.errors.minus(expectedErrors)).isEmpty()
}
