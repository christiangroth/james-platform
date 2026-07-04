package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

@MessageBundle("admin")
interface AdminMessages {

  // admin dashboard
  @Message
  fun adminDashboardTitle(): String

  @Message
  fun adminUserManagementAriaLabel(): String

  @Message
  fun adminUsersTileLabel(): String

  // admin users page
  @Message
  fun adminUserManagementTitle(): String

  @Message
  fun adminNewUserButtonLabel(): String

  @Message
  fun adminColRoles(): String

  @Message
  fun adminColStatus(): String

  @Message
  fun adminColSince(): String

  @Message
  fun adminColLastLogin(): String

  @Message
  fun adminColActions(): String

  @Message
  fun adminManageRolesTitle(): String

  @Message
  fun adminAddRolesButton(): String

  @Message
  fun adminStatusActive(): String

  @Message
  fun adminStatusInactive(): String

  @Message
  fun adminClickToDeactivateTitle(): String

  @Message
  fun adminClickToActivateTitle(): String

  @Message
  fun adminSetPasswordTitle(): String

  @Message
  fun adminDeleteUserTitle(): String

  @Message
  fun adminDeleteUserModalTitle(): String

  @Message
  fun adminDeleteUserConfirmButton(): String

  @Message
  fun adminCreateUserModalTitle(): String

  @Message
  fun adminUsernameLabel(): String

  @Message
  fun adminPasswordLabel(): String

  @Message
  fun adminCreateButton(): String

  @Message
  fun adminSetPasswordModalTitlePrefix(): String

  @Message
  fun adminNewPasswordLabel(): String

  @Message
  fun adminSetPasswordSubmitButton(): String

  @Message
  fun adminManageRolesModalTitlePrefix(): String

  @Message
  fun adminRolesLabel(): String

  @Message
  fun adminSaveRolesButton(): String

  // AdminUserManagementResource / users.html script messages
  @Message
  fun adminRefreshFailedMessage(): String

  @Message
  fun adminUsernameRequiredMessage(): String

  @Message
  fun adminDeleteUserConfirmTemplate(): String

  @Message
  fun adminUserCreatedMessage(): String

  @Message
  fun adminUserActivatedMessage(): String

  @Message
  fun adminUserDeactivatedMessage(): String

  @Message
  fun adminPasswordSetMessage(): String

  @Message
  fun adminRolesUpdatedMessage(): String

  @Message
  fun adminUserDeletedMessage(): String

  @Message
  fun adminUserNotFoundError(): String

  @Message
  fun adminUsernameExistsError(): String

  @Message
  fun adminAllFieldsRequiredError(): String

  @Message
  fun adminCannotDeactivateSelfError(): String

  @Message
  fun adminCannotDeleteSelfError(): String

  @Message
  fun adminCannotRemoveOwnAdminRoleError(): String

  @Message
  fun adminSingleAdminViolationError(): String

  @Message
  fun adminPasswordBlankError(): String
}
