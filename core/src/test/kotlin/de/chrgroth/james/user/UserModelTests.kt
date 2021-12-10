package de.chrgroth.james.user

import de.chrgroth.james.expectError
import de.chrgroth.james.expectSuccess
import org.junit.jupiter.api.Test
import java.util.UUID

// TODO #25 switch to testing of Command

class UserEmailValidationTests {

    @Test
    fun `valid email examples`() {
        User.validateEmail("someone@gmx.de").expectSuccess()
        User.validateEmail("someone@gmx.net").expectSuccess()
        User.validateEmail("some.one@gmail.com").expectSuccess()
    }

    @Test
    fun `empty email validation`() {
        User.validateEmail("").expectError(
            code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
            details = null,
        )
        User.validateEmail(" ").expectError(
            code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
            details = null,
        )
    }

    @Test
    fun `invalid email examples`() {
        User.validateEmail("@gmx.de").expectError(
            code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
            details = null,
        )
        User.validateEmail("someone@gmx").expectError(
            code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
            details = null,
        )
    }
}

class UserModelTests {

    @Test
    fun `register with blank name`() {
        User.create("good@mail.com", "").expectError(
            code = UserErrorCodes.REGISTRATION_NAME_BLANK,
            details = null,
        )
    }

    @Test
    fun `register with valid name`() {
        User.create("good@mail.com", "chris").expectSuccess()
    }

    @Test
    fun `user deletion not supported`() {
        createUser().canBeDeleted().expectError(
            code = UserErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )
    }
}

private fun createUser() = UUID.randomUUID().let { id ->
    User(
        id = id,
        email = "${id}@gmail.com",
        name = id.toString(),
    )
}
