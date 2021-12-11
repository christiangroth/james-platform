package de.chrgroth.james.workspace

import de.chrgroth.james.ErrorCode

enum class WorkspaceErrorCodes : ErrorCode {
    NOT_FOUND,
    APP_NOT_FOUND,

    NAME_BLANK,
    ORDER_NEGATIVE,

    REORDER_WORKSPACES_UNKNOWN_IDS,
    REORDER_WORKSPACES_MISSING_IDS,

    REORDER_APPS_UNKNOWN_IDS,
    REORDER_APPS_MISSING_IDS,

    DELETE_WORKSPACE_INSTALLED_APPS,
    APP_UNINSTALL_NOT_SUPPORTED;

    override val prefix = "WORKSPACE"
    override val id = ordinal.toLong()
}
