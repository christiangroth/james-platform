package de.chrgroth.james.user

import de.chrgroth.james.user.UserErrorCodes.DELETE_NOT_SUPPORTED
import de.chrgroth.james.user.UserErrorCodes.EMAIL_BLANK
import de.chrgroth.james.user.UserErrorCodes.EMAIL_EXISTS
import de.chrgroth.james.user.UserErrorCodes.EMAIL_INVALID
import de.chrgroth.james.user.UserErrorCodes.NAME_BLANK
import de.chrgroth.james.user.UserErrorCodes.NOT_FOUND
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserErrorCodesTests {

    @Test
    fun ensureErrorCodesNotChanged() {
        assertThat(EMAIL_EXISTS.toGlobalRepresentation()).isEqualTo("USER_000_EMAIL_EXISTS")
        assertThat(EMAIL_BLANK.toGlobalRepresentation()).isEqualTo("USER_001_EMAIL_BLANK")
        assertThat(EMAIL_INVALID.toGlobalRepresentation()).isEqualTo("USER_002_EMAIL_INVALID")
        assertThat(NAME_BLANK.toGlobalRepresentation()).isEqualTo("USER_003_NAME_BLANK")
        assertThat(NOT_FOUND.toGlobalRepresentation()).isEqualTo("USER_004_NOT_FOUND")
        assertThat(DELETE_NOT_SUPPORTED.toGlobalRepresentation()).isEqualTo("USER_005_DELETE_NOT_SUPPORTED")
    }

    @Test
    fun ensureNumberOfErrorCodesNotChanged() {
        assertThat(UserErrorCodes.values()).hasSize(6)
    }
}
