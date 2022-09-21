package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import de.chrgroth.james.foldAndShrink
import de.chrgroth.james.validateMatches
import de.chrgroth.james.validateNotBlank
import java.util.UUID

data class User private constructor(
    val id: UUID,
    private var emailField: String,
    private var nameField: String,
) {

    companion object {
        private val simpleEmailPattern = Regex(".+@.+\\..+")

        private fun validateEmail(email: String) = validateMatches(
            value = email,
            pattern = simpleEmailPattern,
            codeBlank = UserErrorCodes.EMAIL_BLANK,
            codeNoMatch = UserErrorCodes.EMAIL_INVALID,
        )

        private fun validateName(name: String) = validateNotBlank(
            value = name,
            codeBlank = UserErrorCodes.NAME_BLANK,
        )

        fun create(id: UUID = UUID.randomUUID(), email: String, name: String): Maybe<User> {
            val emailValidation = validateEmail(email)
            val nameValidation = validateName(name)
            return listOf(emailValidation, nameValidation).foldAndShrink()
                ?: emailValidation.flatMap { validEmail ->
                    nameValidation.map { validName ->
                        User(
                            id = id,
                            emailField = validEmail,
                            nameField = validName,
                        )
                    }
                }
        }
    }

    val email get() = emailField
    val name get() = nameField

    // TODO #22 send user to revalidation status?
    internal fun changeEmail(email: String): Maybe<User> = create(id, email, nameField)
    internal fun changeName(name: String): Maybe<User> = create(id, emailField, name)
}
