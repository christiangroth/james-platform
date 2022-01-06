package de.chrgroth.james.workspace

import de.chrgroth.james.ErrorCode

enum class WorkspaceErrorCodes : ErrorCode {
    ORDER_NEGATIVE,
    NAME_BLANK,

    REORDER_APPS_UNKNOWN_IDS,
    REORDER_APPS_MISSING_IDS,

    APP_INSTALLATION_NOT_FOUND,

    DELETE_WORKSPACE_INSTALLED_APPS,
    APP_UNINSTALL_NOT_SUPPORTED,

    REORDER_WORKSPACES_UNKNOWN_IDS,
    REORDER_WORKSPACES_MISSING_IDS,

    NOT_FOUND;

    override val prefix = "WORKSPACE"
    override val id = ordinal.toLong()
}
