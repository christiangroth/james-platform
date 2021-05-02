package de.chrgroth.james.app

import de.chrgroth.james.ErrorCode

enum class AppErrorCodes : ErrorCode {
    NOT_FOUND,
    RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING,
    CREATE_DEVELOPMENT_VERSION_DRAFT_EXISTS,
    DISCONTINUE_STATUS_IS_DISCONTINUED,
    DELETE_STATUS_IS_NOT_DISCONTINUED;

    override val prefix = "APP"
    override val id = ordinal.toLong()
}
