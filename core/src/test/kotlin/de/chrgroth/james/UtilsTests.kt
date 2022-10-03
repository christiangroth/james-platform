package de.chrgroth.james

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UtilsTests {

    @Test
    fun `test trimToNull with null`() {
        assertThat((null as String?).trimToNull()).isNull()
    }

    @Test
    fun `test trimToNull with blank string`() {
        assertThat("".trimToNull()).isNull()
    }

    @Test
    fun `test trimToNull with empty string`() {
        assertThat(" ".trimToNull()).isNull()
    }

    @Test
    fun `test trimToNull with non empty string`() {
        assertThat(" Some Value   ".trimToNull()).isEqualTo("Some Value")
    }
}
