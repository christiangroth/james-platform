package de.chrgroth.james.user

import de.chrgroth.james.ErrorCode

// TODO #25 cleanup
enum class UserErrorCodes : ErrorCode {
    EMAIL_EXISTS,
    EMAIL_BLANK,
    EMAIL_INVALID,
    NAME_BLANK,
    NOT_FOUND,

    // TODO #25 introduce some general technical errors?
    PERSISTENCE_ERROR,

    DELETE_NOT_SUPPORTED;

    override val prefix = "USER"
    override val id = ordinal.toLong()
}
