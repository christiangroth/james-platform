package de.chrgroth.james

import de.chrgroth.james.ValidationTestErrorCodes.ERROR
import de.chrgroth.james.ValidationTestErrorCodes.SIDEKICK
import org.junit.jupiter.api.Test

enum class ValidationTestErrorCodes : ErrorCode {
    ERROR, SIDEKICK;

    override val prefix = "VALIDATION"
    override val id = ordinal.toLong()
}

val testRegex = Regex("[A-Z]+")

class ValidationTests {

    @Test
    fun `test validateMatches with blank string`() {
        validateMatches("", testRegex, SIDEKICK, ERROR).expectError(
            code = SIDEKICK,
            details = null,
        )
    }

    @Test
    fun `test validateMatches with not matching string`() {
        validateMatches("123", testRegex, SIDEKICK, ERROR).expectError(
            code = ERROR,
            details = "'123' does not match [A-Z]+",
        )
    }

    @Test
    fun `test validateMatches with matching string`() {
        validateMatches("OK", testRegex, SIDEKICK, ERROR).expectSuccess()
    }

    @Test
    fun `test validateNotBlank with empty string`() {
        validateNotBlank("", ERROR).expectError(
            code = ERROR,
            details = null,
        )
    }

    @Test
    fun `test validateNotBlank with whitespace string`() {
        validateNotBlank(" ", ERROR).expectError(
            code = ERROR,
            details = null,
        )
    }

    @Test
    fun `test validateNotBlank with non blank string`() {
        validateNotBlank("test", ERROR).expectSuccess()
    }

    @Test
    fun `test validateNotNegative with negative number`() {
        validateNotNegative(-1, ERROR).expectError(
            code = ERROR,
            details = "-1",
        )
    }

    @Test
    fun `test validateNotNegative with zero`() {
        validateNotNegative(0, ERROR).expectSuccess()
    }

    @Test
    fun `test validateNotNegative with positive number`() {
        validateNotNegative(1, ERROR).expectSuccess()
    }
}
