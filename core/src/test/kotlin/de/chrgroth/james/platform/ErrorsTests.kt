package de.chrgroth.james.platform

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

enum class TestDomainErrorCodes : DomainErrorCode {
  ZERO,
  SOME_ERROR {
    override fun logLevel() =
      DomainErrorCode.LogLevel.WARN
  },
  THIS_IS_BAD {
    override fun logLevel() =
      DomainErrorCode.LogLevel.ERROR
  };

  override val prefix = "TEST"
  override val id = ordinal.toLong()
}

class DomainErrorTests {

  @Test
  fun `default has o details`() {
    val domainError = DomainError(code = TestDomainErrorCodes.THIS_IS_BAD)
    assertThat(domainError.code).isEqualTo(TestDomainErrorCodes.THIS_IS_BAD)
  }
}

class DomainErrorCodeTests {

  @Test
  fun `global representation is as expected`() {
    assertThat(TestDomainErrorCodes.ZERO.toGlobalRepresentation()).isEqualTo("TEST_000_ZERO")
    assertThat(TestDomainErrorCodes.SOME_ERROR.toGlobalRepresentation()).isEqualTo("TEST_001_SOME_ERROR")
    assertThat(TestDomainErrorCodes.THIS_IS_BAD.toGlobalRepresentation()).isEqualTo("TEST_002_THIS_IS_BAD")
  }
}
