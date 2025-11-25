package de.chrgroth.james.workspace

import de.chrgroth.james.data.DataDomainErrorCodes
import de.chrgroth.james.data.DataDomainErrorCodes.NOT_FOUND
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DataDomainErrorCodesTests {

  @Test
  fun ensureErrorCodesNotChanged() {
    assertThat(NOT_FOUND.toGlobalRepresentation()).isEqualTo("DATA_000_NOT_FOUND")
  }

  @Test
  fun ensureNumberOfErrorCodesNotChanged() {
    assertThat(DataDomainErrorCodes.values()).hasSize(1)
  }
}
