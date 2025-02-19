package de.chrgroth.james

import arrow.core.ValidatedNel
import arrow.core.valueOr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

enum class ValidationTestDomainErrorCodes : DomainErrorCode {
  ERROR {
    override fun logLevel() =
      DomainErrorCode.LogLevel.ERROR
  },
  SIDEKICK {
    override fun logLevel() =
      DomainErrorCode.LogLevel.ERROR
  };

  override val prefix = "VALIDATION"
  override val id = ordinal.toLong()
}

class RegexParserTests {

  private val parser = regexParer(
    ValidationTestDomainErrorCodes.SIDEKICK,
    Regex("[A-Z]+"),
    ValidationTestDomainErrorCodes.ERROR,
  )

  @Test
  fun `parse null`() {
    parser.parse(null).expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.SIDEKICK)
    )
  }

  @Test
  fun `parse empty`() {
    parser.parse("").expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.SIDEKICK)
    )
  }

  @Test
  fun `parse blank`() {
    parser.parse(" ").expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.SIDEKICK)
    )
  }

  @Test
  fun `parse matching`() {
    parser.parse("FOO").expectSuccess()
  }

  @Test
  fun `parse non matching`() {
    parser.parse("foobar").expectDomainErrors(
      DomainError(
        code = ValidationTestDomainErrorCodes.ERROR,
        errorMessage = "'foobar' does not match [A-Z]+",
      )
    )
  }
}

class NotBlankParserTests {

  private val parser = notBlankParser(
    ValidationTestDomainErrorCodes.ERROR,
  )

  @Test
  fun `parse null`() {
    parser.parse(null).expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.ERROR)
    )
  }

  @Test
  fun `parse empty`() {
    parser.parse("").expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.ERROR)
    )
  }

  @Test
  fun `parse blank`() {
    parser.parse(" ").expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.ERROR)
    )
  }

  @Test
  fun `parse not blank`() {
    parser.parse("FOO").expectSuccess()
  }
}

class NotEmptyListParserTests {

  private val parser = notEmptyListParser(
    ValidationTestDomainErrorCodes.ERROR,
  )

  @Test
  fun `parse empty list`() {
    parser.parse(emptyList()).expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.ERROR)
    )
  }

  @Test
  fun `parse filled list`() {
    parser.parse(listOf("A value!")).expectSuccess()
  }
}

class NotNegativeLongParserTests {

  private val parser = notNegativeLongParser(
    ValidationTestDomainErrorCodes.ERROR,
  )

  @Test
  fun `parse null`() {
    parser.parse(null).expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.ERROR)
    )
  }

  @Test
  fun `parse empty`() {
    parser.parse("").expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.ERROR)
    )
  }

  @Test
  fun `parse blank`() {
    parser.parse(" ").expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.ERROR)
    )
  }

  @Test
  fun `parse non number`() {
    parser.parse("FOO").expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.ERROR)
    )
  }

  @Test
  fun `parse negative`() {
    parser.parse((-1).toString()).expectDomainErrors(
      DomainError(code = ValidationTestDomainErrorCodes.ERROR)
    )
  }

  @Test
  fun `parse zero`() {
    parser.parse(0.toString()).expectSuccess()
  }

  @Test
  fun `parse positive`() {
    parser.parse(1.toString()).expectSuccess()
  }
}

class ValidationTests {

  @Test
  fun `valid validation`() {
    val validation = createValidation(
      errorCondition = false,
      domainErrorCode = ValidationTestDomainErrorCodes.ERROR,
      errorMessage = null,
    ) { 42 }
    assertThat(validation.isValid).isTrue
    assertThat(validation.isInvalid).isFalse

    validation.expectSuccess()
  }

  @Test
  fun `invalid validation`() {
    val validation = createValidation(
      errorCondition = true,
      domainErrorCode = ValidationTestDomainErrorCodes.ERROR,
      errorMessage = "TEST DETAILS",
    ) { 42 }
    assertThat(validation.isValid).isFalse
    assertThat(validation.isInvalid).isTrue

    validation.expectDomainErrors(
      DomainError(
        code = ValidationTestDomainErrorCodes.ERROR,
        errorMessage = "TEST DETAILS",
      )
    )
  }
}

class ValidationReduceTests {

  private val validOne = createValidation(
    errorCondition = false,
    domainErrorCode = ValidationTestDomainErrorCodes.ERROR,
    errorMessage = null,
  ) { 42 }

  private val validTwo = createValidation(
    errorCondition = false,
    domainErrorCode = ValidationTestDomainErrorCodes.ERROR,
    errorMessage = null,
  ) { 13 }

  private val invalidOne = createValidation(
    errorCondition = true,
    domainErrorCode = ValidationTestDomainErrorCodes.ERROR,
    errorMessage = "TEST DETAILS",
  ) { }

  private val invalidTwo = createValidation(
    errorCondition = true,
    domainErrorCode = ValidationTestDomainErrorCodes.SIDEKICK,
    errorMessage = "TEST DETAILS",
  ) { }

  @Test
  fun `empty list reduced to first throws exception`() {
    val combined: List<ValidatedNel<DomainError, Unit>> = emptyList()
    assertThrows<UnsupportedOperationException>("Empty collection can't be reduced.") {
      combined.reduceWithFirstValue()
    }
    combined.reduceWithAllValues().expectSuccess()
  }

  @Test
  fun `empty list reduced to first using fallback`() {
    val combined: List<ValidatedNel<DomainError, Unit>> = emptyList()
    combined.reduceWithFirstValue { 42 }.expectSuccess()
  }

  @Test
  fun `empty list reduced to all is success`() {
    val combined: List<ValidatedNel<DomainError, Unit>> = emptyList()
    combined.reduceWithAllValues().expectSuccess()
  }

  @Test
  fun `invalid only`() {
    val combined = listOf(invalidOne, invalidTwo)
    val withFirst = combined.reduceWithFirstValue()
    val withAll = combined.reduceWithAllValues()

    withFirst.expectDomainErrors(
      DomainError(
        code = ValidationTestDomainErrorCodes.ERROR,
        errorMessage = "TEST DETAILS",
      ),
      DomainError(
        code = ValidationTestDomainErrorCodes.SIDEKICK,
        errorMessage = "TEST DETAILS",
      ),
    )

    withAll.expectDomainErrors(
      DomainError(
        code = ValidationTestDomainErrorCodes.ERROR,
        errorMessage = "TEST DETAILS",
      ),
      DomainError(
        code = ValidationTestDomainErrorCodes.SIDEKICK,
        errorMessage = "TEST DETAILS",
      ),
    )
  }

  @Test
  fun `mixed`() {
    val combined = listOf(validOne, invalidOne)
    val withFirst = combined.reduceWithFirstValue()
    val withAll = combined.reduceWithAllValues()

    withFirst.expectDomainErrors(
      DomainError(
        code = ValidationTestDomainErrorCodes.ERROR,
        errorMessage = "TEST DETAILS",
      ),
    )

    withAll.expectDomainErrors(
      DomainError(
        code = ValidationTestDomainErrorCodes.ERROR,
        errorMessage = "TEST DETAILS",
      ),
    )
  }

  @Test
  fun `valid only`() {
    val combined = listOf(validOne, validTwo)
    val withFirst = combined.reduceWithFirstValue()
    val withAll = combined.reduceWithAllValues()

    withFirst.expectSuccess()
    assertThat(withFirst.valueOr { throw AssertionError("value missing unexpectedly") })
      .isEqualTo(42)
    withAll.expectSuccess()
    assertThat(withAll.valueOr { throw AssertionError("value missing unexpectedly") })
      .containsExactly(42, 13)
  }
}
