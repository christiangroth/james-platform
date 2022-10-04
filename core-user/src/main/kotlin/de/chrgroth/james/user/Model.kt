package de.chrgroth.james.user

import arrow.core.ValidatedNel
import com.sksamuel.tribune.core.Parser
import com.sksamuel.tribune.core.compose
import de.chrgroth.james.Error
import de.chrgroth.james.notBlankParser
import de.chrgroth.james.regexParer
import java.util.UUID

data class User private constructor(
    val id: UUID,
    private var emailField: String,
    private var nameField: String,
) {

    companion object {

        private val emailParser = regexParer(
            UserErrorCodes.EMAIL_BLANK,
            Regex(".+@.+\\..+"),
            UserErrorCodes.EMAIL_INVALID,
        )

        private val nameParser = notBlankParser(
            UserErrorCodes.NAME_BLANK
        )

        private data class UserParserInput(val email: String, val name: String)

        fun create(id: UUID = UUID.randomUUID(), email: String, name: String): ValidatedNel<Error, User> {
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

            return userParser.parse(UserParserInput(email, name))
        }
    }

    val email get() = emailField
    val name get() = nameField

    // TODO #22 send user to revalidation status?
    internal fun changeEmail(email: String): ValidatedNel<Error, User> = create(id, email, nameField)
    internal fun changeName(name: String): ValidatedNel<Error, User> = create(id, emailField, name)
}
