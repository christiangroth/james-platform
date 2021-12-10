package de.chrgroth.james.user

import de.chrgroth.james.Maybe
import de.chrgroth.james.Maybe.Error
import de.chrgroth.james.Maybe.Result
import java.util.UUID

// TODO #25 refactor usage of canBeDeleted to be private

private val simpleEmailPattern = Regex(".+@.+\\..+")

// TODO #25 methods for changing name and email
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
                        code = UserErrorCodes.REGISTRATION_EMAIL_INVALID,
                        details = null,
                    )
                }
            }


        internal fun create(email: String, name: String): Maybe<User> {
            if (name.isBlank()) {
                return Error(
                    code = UserErrorCodes.REGISTRATION_NAME_BLANK,
                    details = null,
                )
            }

            return validateEmail(email).map {
                User(
                    id = UUID.randomUUID(),
                    email = it,
                    name = name.trim(),
                )
            }
        }
    }

    // TODO #25 define when User deletion is supported
    internal fun canBeDeleted(): Maybe<Unit> {
        return Error(
            code = UserErrorCodes.DELETE_NOT_SUPPORTED,
            details = null,
        )
    }
}
