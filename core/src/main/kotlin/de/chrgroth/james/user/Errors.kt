package de.chrgroth.james.user

import de.chrgroth.james.ErrorCode

enum class UserErrorCodes : ErrorCode {
    REGISTRATION_EMAIL_EXISTS,
    EMAIL_BLANK,
    EMAIL_INVALID,
    NAME_BLANK,

    DELETE_NOT_SUPPORTED;

    override val prefix = "USER"
    override val id = ordinal.toLong()
}
