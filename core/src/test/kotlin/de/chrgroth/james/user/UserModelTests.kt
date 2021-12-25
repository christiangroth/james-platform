package de.chrgroth.james.user

import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.expectError
import de.chrgroth.james.expectErrors
import de.chrgroth.james.expectSuccess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #25 switch to testing of Command

class UserModelTests {

    @Test
    fun `create user with multiple errors`() {
        User.create("", "").expectErrors(
            Error(
                code = UserErrorCodes.EMAIL_BLANK,
                details = null,
            ),
            Error(
                code = UserErrorCodes.NAME_BLANK,
                details = null,
            )
        )
    }

    @Test
    fun `change email`() {
        val user = createUser().changeEmail("better@gmail.com").expectSuccess()
        assertThat(user.email).isEqualTo("better@gmail.com")
    }

    @Test
    fun `change name`() {
        val user = createUser().changeName("Heinz").expectSuccess()
        assertThat(user.name).isEqualTo("Heinz")
    }

    @Test
    fun `user deletion not supported`() {
        createUser().verifyDeletion().expectError(
            code = UserErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )
    }
}

private fun createUser() = UUID.randomUUID().let { id ->
    User.create(
        email = "${id}@gmail.com",
        name = id.toString(),
    ).expectSuccess()
}
