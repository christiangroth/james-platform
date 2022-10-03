package de.chrgroth.james

import arrow.core.Invalid
import arrow.core.ValidatedNel
import arrow.core.getOrHandle
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.Maybe.Result
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat

fun <T> ValidatedNel<de.chrgroth.james.Error, T>.expectSuccess(): T =
    toEither().getOrHandle {
        Assertions.fail("Expected success, but got errors: $it")
    }

fun <T> ValidatedNel<de.chrgroth.james.Error, T>.expectErrors(vararg expectedErrors: de.chrgroth.james.Error) {
    tap {
        Assertions.fail("Expected errors $expectedErrors, but got result: $it")
    }
    assertThat((this as Invalid).value).containsExactlyInAnyOrder(*expectedErrors)
}

fun <T> Maybe<T>.expectSuccess(): T {
    assertThat(this).isInstanceOf(Result::class.java)
    return (this as Result).value
}

fun <T : Any> Maybe<T>.expectError(code: ErrorCode, details: String?) {
    assertThat(this).isInstanceOf(Error::class.java)
    val resultError = this as Error
    assertThat(resultError).isEqualTo(Error<T>(code, details))
}

fun <T> Maybe<T>.expectErrors(vararg expectedErrors: Error<T>) {
    assertThat(this).isInstanceOf(Errors::class.java)
    val resultErrors = this as Errors
    for (expectedError in expectedErrors) {
        assertThat(resultErrors.errors).contains(expectedError)
    }
    assertThat(resultErrors.errors.minus(expectedErrors)).describedAs("Found unexpected errors").isEmpty()
}
