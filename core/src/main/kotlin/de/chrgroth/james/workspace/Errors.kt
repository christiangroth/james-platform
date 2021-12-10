package de.chrgroth.james.workspace

import de.chrgroth.james.ErrorCode

enum class WorkspaceErrorCodes : ErrorCode {
    NOT_FOUND,
    APP_NOT_FOUND,

    CREATE_WORKSPACE_NAME_BLANK,
    RENAME_WORKSPACE_NAME_BLANK,

    DELETE_WORKSPACE_INSTALLED_APPS,
    APP_UNINSTALL_NOT_SUPPORTED;

    override val prefix = "WORKSPACE"
    override val id = ordinal.toLong()
}
