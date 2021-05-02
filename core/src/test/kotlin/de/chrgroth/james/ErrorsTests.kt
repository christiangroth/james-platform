package de.chrgroth.james

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
        val maybe = Maybe.Result("Foo")
        val mapped = maybe.map { it.toUpperCase().reversed() }
        assertThat(mapped).isInstanceOf(Maybe.Result::class.java)
        assertThat((mapped as Maybe.Result).value).isEqualTo("OOF")
    }

    @Test
    fun `an error maybe is mapped correctly`() {
        val maybe = Maybe.Error<Unit>(TestErrors.ZERO)
        val mapped = maybe.map { it }
        assertThat(mapped).isInstanceOf(Maybe.Error::class.java)
        assertThat((mapped as Maybe.Error).code).isEqualTo(TestErrors.ZERO)
    }
}