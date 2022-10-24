package de.chrgroth.james

import arrow.core.Invalid
import arrow.core.ValidatedNel
import arrow.core.getOrHandle
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat

fun <T> ValidatedNel<Error, T>.expectSuccess(): T =
    toEither().getOrHandle {
        Assertions.fail("Expected success, but got errors: $it")
    }

fun <T> ValidatedNel<Error, T>.expectErrors(vararg expectedErrors: Error) {
    tap {
        Assertions.fail("Expected errors $expectedErrors, but got result: $it")
    }
    assertThat((this as Invalid).value).containsExactlyInAnyOrder(*expectedErrors)
}
