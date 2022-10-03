package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.Maybe.Result
import org.assertj.core.api.Assertions

// TODO #31 deduplicate

fun <T> Maybe<T>.expectSuccess(): T {
    Assertions.assertThat(this).isInstanceOf(Result::class.java)
    return (this as Result).value
}

fun <T : Any> Maybe<T>.expectError(code: ErrorCode, details: String?) {
    Assertions.assertThat(this).isInstanceOf(Error::class.java)
    val resultError = this as Error
    Assertions.assertThat(resultError).isEqualTo(Error<T>(code, details))
}

fun <T> Maybe<T>.expectErrors(vararg expectedErrors: Error<T>) {
    Assertions.assertThat(this).isInstanceOf(Errors::class.java)
    val resultErrors = this as Errors
    for (expectedError in expectedErrors) {
        Assertions.assertThat(resultErrors.errors).contains(expectedError)
    }
    Assertions.assertThat(resultErrors.errors.minus(expectedErrors)).describedAs("Found unexpected errors").isEmpty()
}
