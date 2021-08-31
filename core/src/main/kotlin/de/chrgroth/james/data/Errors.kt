package de.chrgroth.james.data

import de.chrgroth.james.ErrorCode

enum class UserErrorCodes : ErrorCode {
    NOT_FOUND,

    REGISTRATION_EMAIL_INVALID,
    REGISTRATION_EMAIL_EXISTS;

    override val prefix = "USER"
    override val id = ordinal.toLong()
}

enum class WorkspaceErrorCodes : ErrorCode {
    NOT_FOUND;

    override val prefix = "WORKSPACE"
    override val id = ordinal.toLong()
}
