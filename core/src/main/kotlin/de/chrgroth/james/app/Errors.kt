package de.chrgroth.james.app

import de.chrgroth.james.ErrorCode

enum class AppErrorCodes : ErrorCode {
    NOT_FOUND,
    APP_DISCONTINUED_NO_CHANGES_ALLOWED,

    CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS,
    UPDATE_DEVELOPMENT_VERSION_DRAFT_MISSING,
    RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING,

    DELETE_STATUS_IS_NOT_DISCONTINUED;

    override val prefix = "APP"
    override val id = ordinal.toLong()
}
