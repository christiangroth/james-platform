package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.Maybe.Result
import org.assertj.core.api.Assertions.assertThat

fun <T> Maybe<T>.expectSuccess() {
    assertThat(this).isInstanceOf(Result::class.java)
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
    assertThat(resultErrors.errors.minus(expectedErrors)).isEmpty()
}
