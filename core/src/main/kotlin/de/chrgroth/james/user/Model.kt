package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import de.chrgroth.james.foldErrors
import de.chrgroth.james.shrink
import java.util.UUID

private val simpleEmailPattern = Regex(".+@.+\\..+")

// TODO #25 ensure trimmed values / enforce usage of create function (https://youtrack.jetbrains.com/issue/KT-11914)
data class User(
    val id: UUID,
    val email: String,
    val name: String,
) {

    companion object {
        internal fun validateEmail(email: String): Maybe<String> =
            email.trim().let {
                if (it.matches(simpleEmailPattern)) {
                    Result(it)
                } else {
                    Error(
                        code = UserErrorCodes.EMAIL_INVALID,
                        details = null,
                    )
                }
            }

        internal fun validateName(name: String): Maybe<String> {
            if (name.isBlank()) {
                return Error(
                    code = UserErrorCodes.NAME_BLANK,
                    details = null,
                )
            }

            return Result(name.trim())
        }

        internal fun create(email: String, name: String): Maybe<User> {
            val emailValidation = validateEmail(email)
            val nameValidation = validateName(name)
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
                        email = validEmail,
                        name = validName,
                    )
                }
            }
        }
    }

    // TODO #22 send user to revalidation status?
    internal fun changeEmail(email: String): Maybe<User> =
        validateEmail(email).map { validEmail ->
            copy(email = validEmail)
        }

    internal fun changeName(name: String): Maybe<User> =
        validateName(name).map { validName ->
            copy(name = validName)
        }

    // TODO #22 define when User deletion is supported
    internal fun verifyDeletion(): Maybe<Unit> {
        return Error(
            code = UserErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )
    }
}
