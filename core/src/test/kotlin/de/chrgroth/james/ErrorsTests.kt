package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

enum class TestErrors : ErrorCode {
    ZERO, SOME_ERROR, THIS_IS_BAD;

    override val prefix = "TEST"
    override val id = ordinal.toLong()
}

class ErrorCodeTests {

    @Test
    fun `global representation is as expected`() {
        assertThat(TestErrors.ZERO.toGlobalRepresentation()).isEqualTo("TEST_000_ZERO")
        assertThat(TestErrors.SOME_ERROR.toGlobalRepresentation()).isEqualTo("TEST_001_SOME_ERROR")
        assertThat(TestErrors.THIS_IS_BAD.toGlobalRepresentation()).isEqualTo("TEST_002_THIS_IS_BAD")
    }
}

class MaybeTests {

    @Test
    fun `a result maybe is mapped correctly`() {
        val maybe = Result("Foo")
        val mapped = maybe.map { it.toUpperCase().reversed() }
        assertThat(mapped).isInstanceOf(Result::class.java)
        assertThat((mapped as Result).value).isEqualTo("OOF")
    }

    @Test
    fun `an error maybe is mapped correctly`() {
        val maybe = Error<Unit>(TestErrors.ZERO)
        val mapped = maybe.map { it }
        assertThat(mapped).isInstanceOf(Error::class.java)
        assertThat((mapped as Error).code).isEqualTo(TestErrors.ZERO)
    }
}