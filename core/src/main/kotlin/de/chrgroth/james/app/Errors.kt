package de.chrgroth.james.app

import de.chrgroth.james.ErrorCodeProvider

enum class AppErrorCodes : ErrorCodeProvider {
    NOT_FOUND,
    RELEASE_DEVELOPMENT_VERSION_DRAFT_MISSING,
    PREPARE_DEVELOPMENT_VERSION_DRAFT_EXISTS,
    DISCONTINUE_STATUS_IS_DISCONTINUED,
    DELETE_STATUS_IS_NOT_DISCONTINUED;

    override val prefix = "APP"
    override val id = ordinal.toLong()
}
