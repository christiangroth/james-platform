package de.chrgroth.james.user

import arrow.core.ValidatedNel
import com.sksamuel.tribune.core.Parser
import com.sksamuel.tribune.core.compose
import com.sksamuel.tribune.core.strings.match
import com.sksamuel.tribune.core.strings.notNullOrBlank
import com.sksamuel.tribune.core.strings.trim
import de.chrgroth.james.Error
import java.util.UUID

data class User private constructor(
    val id: UUID,
    private var emailField: String,
    private var nameField: String,
) {

    companion object {
        private val simpleEmailPattern = Regex(".+@.+\\..+")

        data class UserParserInput(val email: String, val name: String)

        // TODO #29 create factory function
        val emailParser = Parser
            .fromNullableString()
            .notNullOrBlank { Error(UserErrorCodes.EMAIL_BLANK) }
            .match(simpleEmailPattern) { Error(UserErrorCodes.EMAIL_INVALID, "'$it' does not match $simpleEmailPattern") }
            .trim()

        // TODO #29 create factory function
        val nameParser = Parser
            .fromNullableString()
            .notNullOrBlank { Error(UserErrorCodes.NAME_BLANK) }
            .trim()

        fun create(id: UUID = UUID.randomUUID(), input: UserParserInput): ValidatedNel<Error, User> {
            // TODO #29 optimize: store combined parser as val
            val userParser: Parser<UserParserInput, User, Error> = Parser
                .compose(
                    emailParser.contramap { it.email },
                    nameParser.contramap { it.name },
                ) { validEmail, validName ->
                    User(
                        id = id,
                        emailField = validEmail,
                        nameField = validName,
                    )
                }

            return userParser.parse(input)
        }
    }

    val email get() = emailField
    val name get() = nameField

    // TODO #22 send user to revalidation status?
    internal fun changeEmail(email: String): ValidatedNel<Error, User> = create(id, UserParserInput(email, nameField))
    internal fun changeName(name: String): ValidatedNel<Error, User> = create(id, UserParserInput(emailField, name))
}
