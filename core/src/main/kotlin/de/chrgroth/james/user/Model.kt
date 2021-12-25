package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.foldAndShrink
import de.chrgroth.james.foldErrors
import de.chrgroth.james.throwOnError
import de.chrgroth.james.validateMatches
import de.chrgroth.james.validateNotBlank
import java.util.UUID

private val simpleEmailPattern = Regex(".+@.+\\..+")

data class User private constructor(
    val id: UUID,
    private var emailField: String,
    private var nameField: String,
) {

    companion object {
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

        fun create(email: String, name: String): Maybe<User> {
            val emailValidation = validateEmail(email)
            val nameValidation = validateName(name)
            val validationErrors = listOf(emailValidation, nameValidation).foldAndShrink<User>()
            if (validationErrors != null) {
                return validationErrors
            }

            return emailValidation.flatMap { validEmail ->
                nameValidation.map { validName ->
                    User(
                        id = UUID.randomUUID(),
                        emailField = validEmail,
                        nameField = validName,
                    )
                }
            }
        }
    }

    // TODO #25 test exception usecase
    init {
        emailField = emailField.trim()
        nameField = nameField.trim()

        listOf(validateEmail(emailField), validateName(nameField)).foldErrors<User>().throwOnError(javaClass.simpleName)
    }

    val email get() = emailField
    val name get() = nameField

    // TODO #22 send user to revalidation status?
    internal fun changeEmail(email: String): Maybe<User> =
        validateEmail(email).map { validEmail ->
            copy(emailField = validEmail)
        }

    internal fun changeName(name: String): Maybe<User> =
        validateName(name).map { validName ->
            copy(nameField = validName)
        }

    // TODO #22 define when User deletion is supported
    internal fun verifyDeletion(): Maybe<Unit> {
        return Error(
            code = UserErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )
    }
}
