package de.chrgroth.james

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

enum class TestErrorCodes : ErrorCode {
    ZERO, SOME_ERROR, THIS_IS_BAD;

    override val prefix = "TEST"
    override val id = ordinal.toLong()
}

class ErrorCodeTests {

    @Test
    fun `global representation is as expected`() {
        assertThat(TestErrorCodes.ZERO.toGlobalRepresentation()).isEqualTo("TEST_000_ZERO")
        assertThat(TestErrorCodes.SOME_ERROR.toGlobalRepresentation()).isEqualTo("TEST_001_SOME_ERROR")
        assertThat(TestErrorCodes.THIS_IS_BAD.toGlobalRepresentation()).isEqualTo("TEST_002_THIS_IS_BAD")
    }
}
