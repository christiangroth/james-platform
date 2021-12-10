package de.chrgroth.james.user

import de.chrgroth.james.ErrorCode

enum class UserErrorCodes : ErrorCode {
    NOT_FOUND,

    REGISTRATION_EMAIL_EXISTS,
    REGISTRATION_EMAIL_INVALID,
    REGISTRATION_NAME_BLANK,

    CREATE_WORKSPACE_NAME_BLANK,
    RENAME_WORKSPACE_NAME_BLANK,

    DELETE_NOT_SUPPORTED;

    override val prefix = "USER"
    override val id = ordinal.toLong()
}
