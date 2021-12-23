package de.chrgroth.james.user

import de.chrgroth.james.InvalidInstanceException
import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.foldErrors
import de.chrgroth.james.shrink
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
        fun create(email: String, name: String): Maybe<User> {
            val emailValidation = validateMatches(
                value = email,
                pattern = simpleEmailPattern,
                codeBlank = UserErrorCodes.EMAIL_BLANK,
                codeNoMatch = UserErrorCodes.EMAIL_INVALID,
            )
            val nameValidation = validateNotBlank(
                value = name,
                codeBlank = UserErrorCodes.NAME_BLANK,
            )
            val validationErrors = listOf(
                emailValidation, nameValidation
            ).foldErrors<User>().shrink()
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

        val emailValidation = validateMatches(
            value = email,
            pattern = simpleEmailPattern,
            codeBlank = UserErrorCodes.EMAIL_BLANK,
            codeNoMatch = UserErrorCodes.EMAIL_INVALID,
        )
        val nameValidation = validateNotBlank(
            value = name,
            codeBlank = UserErrorCodes.NAME_BLANK,
        )

        listOf(emailValidation, nameValidation).foldErrors<User>()?.also {
            throw InvalidInstanceException(javaClass.simpleName, it.errors)
        }
    }

    val email get() = emailField
    val name get() = nameField

    // TODO #22 send user to revalidation status?
    internal fun changeEmail(email: String): Maybe<User> =
        validateMatches(
            value = email,
            pattern = simpleEmailPattern,
            codeBlank = UserErrorCodes.EMAIL_BLANK,
            codeNoMatch = UserErrorCodes.EMAIL_INVALID,
        ).map { validEmail ->
            copy(emailField = validEmail)
        }

    internal fun changeName(name: String): Maybe<User> =
        validateNotBlank(
            value = name,
            codeBlank = UserErrorCodes.NAME_BLANK,
        ).map { validName ->
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
