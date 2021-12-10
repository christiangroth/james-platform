package de.chrgroth.james.user

import de.chrgroth.james.ErrorCode

enum class UserErrorCodes : ErrorCode {
    NOT_FOUND,

    REGISTRATION_EMAIL_EXISTS,
    EMAIL_INVALID,
    NAME_BLANK,

    DELETE_NOT_SUPPORTED;

    override val prefix = "USER"
    override val id = ordinal.toLong()
}
