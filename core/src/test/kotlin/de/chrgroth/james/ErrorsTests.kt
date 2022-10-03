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
        val maybe = Error<Unit>(TestErrorCodes.ZERO, null)

        @Suppress("UNUSED_EXPRESSION")
        val mapped = maybe.map { it }
        assertThat(mapped).isInstanceOf(Error::class.java)
        assertThat((mapped as Error).code).isEqualTo(TestErrorCodes.ZERO)
    }

    @Test
    fun `an errors maybe is mapped correctly`() {
        val maybe = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO, null)))

        @Suppress("UNUSED_EXPRESSION")
        val mapped = maybe.map { it }
        assertThat(mapped).isInstanceOf(Errors::class.java)
        assertThat((mapped as Errors).errors).isEqualTo(listOf(Error<Unit>(TestErrorCodes.ZERO, null)))
    }

    @Test
    fun `a result maybe is transformed correctly`() {
        val maybe = Result("Foo")
        val mapped = maybe.flatMap { Result(it.uppercase().reversed()) }
        assertThat(mapped).isInstanceOf(Result::class.java)
        assertThat((mapped as Result).value).isEqualTo("OOF")
    }

    @Test
    fun `an error maybe is transformed correctly`() {
        val maybe = Error<Unit>(TestErrorCodes.ZERO, null)
        val mapped = maybe.flatMap { Error<Unit>(TestErrorCodes.ZERO, null) }
        assertThat(mapped).isInstanceOf(Error::class.java)
        assertThat((mapped as Error).code).isEqualTo(TestErrorCodes.ZERO)
    }

    @Test
    fun `an errors maybe is transformed correctly`() {
        val maybe = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO, null)))
        val mapped = maybe.flatMap { Errors<Unit>(listOf(Error(TestErrorCodes.ZERO, null))) }
        assertThat(mapped).isInstanceOf(Errors::class.java)
        assertThat((mapped as Errors).errors).isEqualTo(listOf(Error<Unit>(TestErrorCodes.ZERO, null)))
    }
}

class ErrorsCombinationTests {

    @Test
    fun `fold list of errors`() {
        val result = listOf(
            Error<Unit>(TestErrorCodes.SOME_ERROR, details = null),
            Error<Unit>(TestErrorCodes.THIS_IS_BAD, details = null),
        ).fold()
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(2)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.SOME_ERROR, null))
        assertThat(result.errors[1]).isEqualTo(Error<Unit>(TestErrorCodes.THIS_IS_BAD, null))
    }

    @Test
    fun `fold list of errors without nulls`() {
        val result = listOf(
            Error<Unit>(TestErrorCodes.SOME_ERROR, details = null),
            null,
            Error<Unit>(TestErrorCodes.THIS_IS_BAD, details = null),
        ).fold()
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(2)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.SOME_ERROR, null))
        assertThat(result.errors[1]).isEqualTo(Error<Unit>(TestErrorCodes.THIS_IS_BAD, null))
    }

    @Test
    fun `fold list of nulls only`() {
        val result = listOf(
            null as Error<Unit>?,
            null,
        ).fold()
        assertThat(result).isNull()
    }

    @Test
    fun `foldErrors list of errors without nulls`() {
        val result = listOf(
            Error<Unit>(TestErrorCodes.SOME_ERROR, details = null),
            null,
            Error<Unit>(TestErrorCodes.THIS_IS_BAD, details = null),
        ).foldErrors<Unit>()
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(2)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.SOME_ERROR, null))
        assertThat(result.errors[1]).isEqualTo(Error<Unit>(TestErrorCodes.THIS_IS_BAD, null))
    }

    @Test
    fun `shrink null`() {
        assertThat((null as Errors<Unit>?).shrink()).isNull()
    }

    @Test
    fun `shrink errors with no entries`() {
        val errors: Errors<Unit> = Errors(
            emptyList()
        )
        assertThat(errors.shrink()).isNull()
    }

    @Test
    fun `shrink errors with single entry`() {
        val errors: Errors<Unit> = Errors(
            listOf(
                Error(TestErrorCodes.SOME_ERROR, null)
            )
        )
        val shrinked = errors.shrink()
        assertThat(shrinked).isInstanceOf(Error::class.java)
        assertThat((shrinked as Error<Unit>).code).isEqualTo(TestErrorCodes.SOME_ERROR)
    }

    @Test
    fun `shrink errors with multiple entries`() {
        val errors: Errors<Unit> = Errors(
            listOf(
                Error(TestErrorCodes.SOME_ERROR, null),
                Error(TestErrorCodes.SOME_ERROR, null)
            )
        )
        assertThat(errors.shrink()).isInstanceOf(Errors::class.java)
    }

    @Test
    fun `combine null error with null error becomes null`() {
        val result = (null as Error<Unit>?).combine(null as Error<Unit>?)
        assertThat(result).isNull()
    }

    @Test
    fun `combine error with null error contains only non null`() {
        val two: Error<Unit>? = null
        val result = Error<Unit>(TestErrorCodes.ZERO, null).combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(1)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
    }

    @Test
    fun `combine null error with error contains only non null`() {
        val two = Error<Unit>(TestErrorCodes.ZERO, null)
        val result = (null as Error<Unit>?).combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(1)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
    }

    @Test
    fun `combine two error instances contains both`() {
        val one = Error<Unit>(TestErrorCodes.ZERO, null)
        val two = Error<Unit>(TestErrorCodes.SOME_ERROR, null)
        val result = one.combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(2)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
        assertThat(result.errors[1]).isEqualTo(Error<Unit>(TestErrorCodes.SOME_ERROR, null))
    }

    @Test
    fun `combine null errors with null error becomes null`() {
        val result = (null as Errors<Unit>?).combine(null as Error<Unit>?)
        assertThat(result).isNull()
    }

    @Test
    fun `combine errors with null error contains only non null`() {
        val two: Error<Unit>? = null
        val result = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO, null))).combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(1)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
    }

    @Test
    fun `combine null errors with error contains only non null`() {
        val two = Error<Unit>(TestErrorCodes.ZERO, null)
        val result = (null as Errors<Unit>?).combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(1)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
    }

    @Test
    fun `combine errors with error contains all`() {
        val one = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO, null)))
        val two = Error<Unit>(TestErrorCodes.SOME_ERROR, null)
        val result = one.combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(2)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
        assertThat(result.errors[1]).isEqualTo(Error<Unit>(TestErrorCodes.SOME_ERROR, null))
    }

    @Test
    fun `combine null errors with null errors becomes null`() {
        val result = (null as Errors<Unit>?).combine(null as Errors<Unit>?)
        assertThat(result).isNull()
    }

    @Test
    fun `combine errors with null errors contains only non null`() {
        val two: Errors<Unit>? = null
        val result = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO, null))).combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(1)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
    }

    @Test
    fun `combine null errors with errors contains only non null`() {
        val two = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO, null)))
        val result = (null as Errors<Unit>?).combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(1)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
    }

    @Test
    fun `combine errors with errors contains all`() {
        val one = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO, null)))
        val two = Errors<Unit>(listOf(Error(TestErrorCodes.SOME_ERROR, null)))
        val result = one.combine(two)
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(2)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
        assertThat(result.errors[1]).isEqualTo(Error<Unit>(TestErrorCodes.SOME_ERROR, null))
    }

    @Test
    fun `combine list of errors instances filters out null and contains all`() {
        val one = Errors<Unit>(listOf(Error(TestErrorCodes.ZERO, null)))
        val two = null
        val three = Errors<Unit>(listOf(Error(TestErrorCodes.SOME_ERROR, null)))
        val result = listOf(one, two, three).combine()
        assertThat(result).isNotNull
        assertThat(result!!.errors).hasSize(2)
        assertThat(result.errors[0]).isEqualTo(Error<Unit>(TestErrorCodes.ZERO, null))
        assertThat(result.errors[1]).isEqualTo(Error<Unit>(TestErrorCodes.SOME_ERROR, null))
    }

    @Test
    fun `combine list of errors instances all null becomes null`() {
        val result = listOf(null as Errors<Unit>?, null as Errors<Unit>?, null as Errors<Unit>?).combine()
        assertThat(result).isNull()
    }
}
