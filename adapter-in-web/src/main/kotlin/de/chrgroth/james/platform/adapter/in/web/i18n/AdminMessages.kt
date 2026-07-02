package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

@MessageBundle("admin")
interface AdminMessages {

  // admin dashboard
  @Message
  fun adminDashboardTitle(): String

  @Message
  fun adminBreadcrumbAdmin(): String

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

  // health page
  @Message
  fun adminHealthTitle(): String

  @Message
  fun adminCronjobsStateHeading(): String

  @Message
  fun adminCronjobsSectionLabel(): String

  @Message
  fun adminColJob(): String

  @Message
  fun adminColCron(): String

  @Message
  fun adminColNext(): String

  @Message
  fun adminNowLabel(): String

  @Message
  fun adminMongoDbHeading(): String

  @Message
  fun adminCollectionsSectionLabel(): String

  @Message
  fun adminColCollection(): String

  @Message
  fun adminColCount(): String

  @Message
  fun adminColSize(): String

  @Message
  fun adminTotalLabel(): String

  @Message
  fun adminQueriesSectionLabel(): String

  @Message
  fun adminColQuery(): String

  @Message
  fun adminColExecutions(): String

  @Message
  fun adminScriptingHeading(): String

  @Message
  fun adminScriptExecutionsSectionLabel(): String

  @Message
  fun adminNoScriptStatsMessage(): String

  @Message
  fun adminColEntity(): String

  @Message
  fun adminColProperty(): String

  @Message
  fun adminColErrors(): String

  @Message
  fun adminColTotalMs(): String

  // logs page
  @Message
  fun adminLogsTitle(): String

  @Message
  fun adminLogsSubtitle(): String

  @Message
  fun adminLogsChronologicalTab(): String

  @Message
  fun adminLogsGroupedTab(): String

  @Message
  fun adminLogsEmptyState(): String

  @Message
  fun adminLogGroupEntriesLabel(count: Int): String

  @Message
  fun adminColTimestamp(): String

  @Message
  fun adminColMessage(): String

  @Message
  fun adminColStacktrace(): String

  @Message
  fun adminShowLabel(): String

  @Message
  fun adminColLevel(): String

  @Message
  fun adminColClass(): String

  // mongodb viewer page
  @Message
  fun adminMongoViewerTitle(): String

  @Message
  fun adminMongoCollectionLabel(): String

  @Message
  fun adminMongoSelectCollectionOption(): String

  @Message
  fun adminMongoNoDocumentsForSchemaMessage(): String

  @Message
  fun adminMongoFiltersHeading(): String

  @Message
  fun adminMongoContainsBadge(): String

  @Message
  fun adminMongoContainsPlaceholder(): String

  @Message
  fun adminMongoIdBadge(): String

  @Message
  fun adminMongoEqualsPlaceholder(): String

  @Message
  fun adminMongoInPlaceholder(): String

  @Message
  fun adminMongoNotInPlaceholder(): String

  @Message
  fun adminMongoSortHeading(): String

  @Message
  fun adminMongoNoSortOption(): String

  @Message
  fun adminMongoSortAscLabel(): String

  @Message
  fun adminMongoSortDescLabel(): String

  @Message
  fun adminMongoApplyFiltersButton(): String

  @Message
  fun adminMongoResultsHeading(): String

  @Message
  fun adminMongoDocumentsTotalLabel(count: Int): String

  @Message
  fun adminMongoPerPageLabel(): String

  @Message
  fun adminMongoNoDocumentsMatchMessage(): String

  @Message
  fun adminMongoPageLabel(page: Int, totalPages: Int): String

  @Message
  fun adminMongoPrevButton(): String

  @Message
  fun adminMongoNextButton(): String

  // config page
  @Message
  fun adminConfigTitle(): String

  @Message
  fun adminConfigEnvironmentSectionLabel(): String

  @Message
  fun adminConfigSectionLabel(): String

  @Message
  fun adminColKey(): String

  @Message
  fun adminColValue(): String
}
