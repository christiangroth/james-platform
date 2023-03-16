package de.chrgroth.james.user

import de.chrgroth.james.ErrorCode

// TODO #29 making it internal was a good idea, but we are using some codes cross module
enum class UserErrorCodes : ErrorCode {
    EMAIL_EXISTS,
    EMAIL_BLANK,
    EMAIL_INVALID,
    NAME_BLANK,
    NOT_FOUND,
    DELETE_NOT_SUPPORTED;

    override val prefix = "USER"
    override val id = ordinal.toLong()
}
