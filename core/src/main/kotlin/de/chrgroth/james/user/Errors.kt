package de.chrgroth.james.user

import de.chrgroth.james.ErrorCode

// TODO #25 test global representation to never change the codes!
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
