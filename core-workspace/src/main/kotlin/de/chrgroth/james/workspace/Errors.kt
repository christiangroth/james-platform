package de.chrgroth.james.workspace

import de.chrgroth.james.DomainErrorCode

enum class WorkspaceDomainErrorCodes : DomainErrorCode {
    ORDER_NEGATIVE,
    NAME_BLANK,

    REORDER_APPS_UNKNOWN_IDS,
    REORDER_APPS_MISSING_IDS,

    INSTALLATION_NOT_FOUND,
    DOWNGRADE_NOT_SUPPORTED,

    DELETE_WORKSPACE_INSTALLED_APPS,
    UNINSTALL_NOT_SUPPORTED,

    REORDER_WORKSPACES_UNKNOWN_IDS,
    REORDER_WORKSPACES_MISSING_IDS,

    NOT_FOUND;

    override val prefix = "WORKSPACE"
    override val id = ordinal.toLong()
}
