package de.chrgroth.james

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Errors
import de.chrgroth.james.Maybe.Result
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

class MaybeTests {

    @Test
    fun `a result maybe is mapped correctly`() {
        val maybe = Result("Foo")
        val mapped = maybe.map { it.uppercase().reversed() }
        assertThat(mapped).isInstanceOf(Result::class.java)
        assertThat((mapped as Result).value).isEqualTo("OOF")
    }

    @Test
    fun `an error maybe is mapped correctly`() {
        val maybe = Error<Unit>(TestErrorCodes.ZERO)
        @Suppress("UNUSED_EXPRESSION")
        val mapped = maybe.map { it }
        assertThat(mapped).isInstanceOf(Error::class.java)
        assertThat((mapped as Error).code).isEqualTo(TestErrorCodes.ZERO)
    }

    @Test
    fun `an errors maybe is mapped correctly`() {
        val maybe = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO)))
        @Suppress("UNUSED_EXPRESSION")
        val mapped = maybe.map { it }
        assertThat(mapped).isInstanceOf(Errors::class.java)
        assertThat((mapped as Errors).errors).isEqualTo(listOf(Error<Unit>(TestErrorCodes.ZERO)))
    }

    @Test
    fun `a result maybe is transformed correctly`() {
        val maybe = Result("Foo")
        val mapped = maybe.transform { Result(it.uppercase().reversed()) }
        assertThat(mapped).isInstanceOf(Result::class.java)
        assertThat((mapped as Result).value).isEqualTo("OOF")
    }

    @Test
    fun `an error maybe is transformed correctly`() {
        val maybe = Error<Unit>(TestErrorCodes.ZERO)
        val mapped = maybe.transform { Error<Unit>(TestErrorCodes.ZERO) }
        assertThat(mapped).isInstanceOf(Error::class.java)
        assertThat((mapped as Error).code).isEqualTo(TestErrorCodes.ZERO)
    }

    @Test
    fun `an errors maybe is transformed correctly`() {
        val maybe = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO)))
        val mapped = maybe.transform { Errors<Unit>(listOf(Error(TestErrorCodes.ZERO))) }
        assertThat(mapped).isInstanceOf(Errors::class.java)
        assertThat((mapped as Errors).errors).isEqualTo(listOf(Error<Unit>(TestErrorCodes.ZERO)))
    }
}

class ErrorCombinationTests {

    @Test
    fun `combine null error with null error becomes null`() {
        val result = (null as Error<Unit>?).combine(null)
        assertThat(result).isNull()
    }

    @Test
    fun `combine error with null error contains only non null`() {
        val two: Error<Unit>? = null
        val result = Error<Unit>(TestErrorCodes.ZERO).combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(1)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO))
    }

    @Test
    fun `combine null error with error contains only non null`() {
        val two = Error<Unit>(TestErrorCodes.ZERO)
        val result = (null as Error<Unit>?).combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(1)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO))
    }

    @Test
    fun `combine two errors contains both`() {
        val one = Error<Unit>(TestErrorCodes.ZERO)
        val two = Error<Unit>(TestErrorCodes.SOME_ERROR)
        val result = one.combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(2)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO))
        assertThat(result.errors[1]).isEqualTo(Error<Unit>(TestErrorCodes.SOME_ERROR))
    }
}
