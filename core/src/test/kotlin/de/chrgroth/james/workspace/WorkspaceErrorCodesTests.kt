package de.chrgroth.james.workspace

import de.chrgroth.james.workspace.WorkspaceErrorCodes.APP_DOWNGRADE_NOT_SUPPORTED
import de.chrgroth.james.workspace.WorkspaceErrorCodes.APP_INSTALLATION_NOT_FOUND
import de.chrgroth.james.workspace.WorkspaceErrorCodes.APP_UNINSTALL_NOT_SUPPORTED
import de.chrgroth.james.workspace.WorkspaceErrorCodes.DELETE_WORKSPACE_INSTALLED_APPS
import de.chrgroth.james.workspace.WorkspaceErrorCodes.NAME_BLANK
import de.chrgroth.james.workspace.WorkspaceErrorCodes.NOT_FOUND
import de.chrgroth.james.workspace.WorkspaceErrorCodes.ORDER_NEGATIVE
import de.chrgroth.james.workspace.WorkspaceErrorCodes.REORDER_APPS_MISSING_IDS
import de.chrgroth.james.workspace.WorkspaceErrorCodes.REORDER_APPS_UNKNOWN_IDS
import de.chrgroth.james.workspace.WorkspaceErrorCodes.REORDER_WORKSPACES_MISSING_IDS
import de.chrgroth.james.workspace.WorkspaceErrorCodes.REORDER_WORKSPACES_UNKNOWN_IDS
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WorkspaceErrorCodesTests {

    @Test
    fun ensureErrorCodesNotChanged() {
        assertThat(ORDER_NEGATIVE.toGlobalRepresentation()).isEqualTo("WORKSPACE_000_ORDER_NEGATIVE")
        assertThat(NAME_BLANK.toGlobalRepresentation()).isEqualTo("WORKSPACE_001_NAME_BLANK")
        assertThat(REORDER_APPS_UNKNOWN_IDS.toGlobalRepresentation()).isEqualTo("WORKSPACE_002_REORDER_APPS_UNKNOWN_IDS")
        assertThat(REORDER_APPS_MISSING_IDS.toGlobalRepresentation()).isEqualTo("WORKSPACE_003_REORDER_APPS_MISSING_IDS")
        assertThat(APP_INSTALLATION_NOT_FOUND.toGlobalRepresentation()).isEqualTo("WORKSPACE_004_APP_INSTALLATION_NOT_FOUND")
        assertThat(APP_DOWNGRADE_NOT_SUPPORTED.toGlobalRepresentation()).isEqualTo("WORKSPACE_005_APP_DOWNGRADE_NOT_SUPPORTED")
        assertThat(DELETE_WORKSPACE_INSTALLED_APPS.toGlobalRepresentation()).isEqualTo("WORKSPACE_006_DELETE_WORKSPACE_INSTALLED_APPS")
        assertThat(APP_UNINSTALL_NOT_SUPPORTED.toGlobalRepresentation()).isEqualTo("WORKSPACE_007_APP_UNINSTALL_NOT_SUPPORTED")
        assertThat(REORDER_WORKSPACES_UNKNOWN_IDS.toGlobalRepresentation()).isEqualTo("WORKSPACE_008_REORDER_WORKSPACES_UNKNOWN_IDS")
        assertThat(REORDER_WORKSPACES_MISSING_IDS.toGlobalRepresentation()).isEqualTo("WORKSPACE_009_REORDER_WORKSPACES_MISSING_IDS")
        assertThat(NOT_FOUND.toGlobalRepresentation()).isEqualTo("WORKSPACE_010_NOT_FOUND")
    }

    @Test
    fun ensureNumberOfErrorCodesNotChanged() {
        assertThat(WorkspaceErrorCodes.values()).hasSize(11)
    }
}
