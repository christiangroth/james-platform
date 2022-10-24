package de.chrgroth.james

enum class ValidationTestErrorCodes : ErrorCode {
    ERROR, SIDEKICK;

    override val prefix = "VALIDATION"
    override val id = ordinal.toLong()
}

val testRegex = Regex("[A-Z]+")

class ValidationTests {

}
