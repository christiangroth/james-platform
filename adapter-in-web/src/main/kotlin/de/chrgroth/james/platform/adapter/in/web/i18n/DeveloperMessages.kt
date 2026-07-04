package de.chrgroth.james.platform.adapter.`in`.web.i18n

import io.quarkus.qute.i18n.Message
import io.quarkus.qute.i18n.MessageBundle

@MessageBundle("developer")
interface DeveloperMessages {

  // shared developer breadcrumb / labels
  @Message
  fun developerBreadcrumbDevelopment(): String

  @Message
  fun developerDraftLabel(): String

  @Message
  fun developerPublishedLabel(): String

  @Message
  fun developerOnePropertyLabel(): String

  @Message
  fun developerPropertiesCountLabel(count: Int): String

  @Message
  fun developerColName(): String

  @Message
  fun developerColType(): String

  @Message
  fun developerColConstraints(): String

  @Message
  fun developerColTargetEntity(): String

  @Message
  fun developerColDefault(): String

  @Message
  fun developerColSmartDefault(): String

  @Message
  fun developerColScript(): String

  @Message
  fun developerColProperties(): String

  @Message
  fun developerScriptBadge(): String

  @Message
  fun developerEntitiesHeading(): String

  @Message
  fun developerReportsHeading(): String

  @Message
  fun developerNoEntitiesMessage(): String

  @Message
  fun developerNoReportsMessage(): String

  // developer dashboard
  @Message
  fun developerDashboardNewAppTile(): String

  @Message
  fun developerDashboardNewAppAriaLabel(): String

  @Message
  fun developerDashboardOpenAppAriaLabel(name: String): String

  @Message
  fun developerDashboardTitle(): String

  @Message
  fun developerCreateAppModalTitle(): String

  @Message
  fun developerDescriptionLabel(): String

  @Message
  fun developerCreateAppSubmitButton(): String

  // developer app overview
  @Message
  fun developerEditAppAriaLabel(): String

  @Message
  fun developerVersionsHeading(): String

  @Message
  fun developerNewVersionTile(): String

  @Message
  fun developerNewVersionAriaLabel(): String

  @Message
  fun developerOpenVersionAriaLabel(version: String): String

  @Message
  fun developerOpenDraftVersionAriaLabel(): String

  @Message
  fun developerDiffButton(): String

  @Message
  fun developerEditAppModalTitle(): String

  // developer version editor
  @Message
  fun developerBreakingChangesBadge(): String

  @Message
  fun developerReleaseNotesLabel(): String

  @Message
  fun developerEntityHeading(name: String): String

  @Message
  fun developerDisplayTextHeading(): String

  @Message
  fun developerEditDisplayTextAriaLabel(): String

  @Message
  fun developerSortOrderHeading(): String

  @Message
  fun developerEditSortOrderAriaLabel(): String

  @Message
  fun developerPropertiesHeading(): String

  @Message
  fun developerNoPropertiesMessage(): String

  @Message
  fun developerEditPropertyAriaLabel(name: String): String

  @Message
  fun developerComputedPropertiesHeading(): String

  @Message
  fun developerNoComputedPropertiesMessage(): String

  @Message
  fun developerEditComputedPropertyAriaLabel(name: String): String

  @Message
  fun developerReportHeading(name: String): String

  @Message
  fun developerOpenEntityAriaLabel(name: String): String

  @Message
  fun developerOpenReportAriaLabel(name: String): String

  @Message
  fun developerDeleteDraftAriaLabel(): String

  @Message
  fun developerPublishVersionAriaLabel(): String

  @Message
  fun developerPublishButton(): String

  @Message
  fun developerNoEntitiesReadonly(): String

  @Message
  fun developerNoPropertiesReadonly(): String

  @Message
  fun developerNoReportsReadonly(): String

  @Message
  fun developerHtmlLabel(): String

  @Message
  fun developerScriptLabel(): String

  @Message
  fun developerNoContentMessage(): String

  @Message
  fun developerDeleteDraftVersionModalTitle(): String

  @Message
  fun developerDeleteDraftVersionConfirm(): String

  @Message
  fun developerAddEntityModalTitle(): String

  @Message
  fun developerEntityNamePlaceholder(): String

  @Message
  fun developerAddReportModalTitle(): String

  @Message
  fun developerReportNamePlaceholder(): String

  @Message
  fun developerDeleteEntityModalTitle(): String

  @Message
  fun developerDeleteEntityConfirm(): String

  @Message
  fun developerDeleteReportModalTitle(): String

  @Message
  fun developerDeleteReportConfirm(): String

  @Message
  fun developerAddComputedPropertyModalTitle(): String

  @Message
  fun developerComputedPropertyNamePlaceholder(): String

  @Message
  fun developerEditComputedPropertyModalTitle(): String

  @Message
  fun developerScriptKotlinLabel(): String

  @Message
  fun developerComputedPropertyScriptHelp(): String

  @Message
  fun developerComputedPropertyScriptPlaceholder(): String

  @Message
  fun developerRemoveComputedPropertyModalTitle(): String

  @Message
  fun developerRemoveComputedPropertyConfirm(): String

  @Message
  fun developerEditDisplayTextModalTitle(): String

  @Message
  fun developerTemplateLabel(): String

  @Message
  fun developerDisplayTextPlaceholder(): String

  @Message
  fun developerIdBadge(): String

  @Message
  fun developerEditSortOrderModalTitle(): String

  @Message
  fun developerPropertyPlaceholder(): String

  @Message
  fun developerAscendingLabel(): String

  @Message
  fun developerDescendingLabel(): String

  @Message
  fun developerAddSortFieldButton(): String

  @Message
  fun developerDragToReorderTitle(): String

  @Message
  fun developerLoadInfoErrorMessage(): String

  // developer edit-property page
  @Message
  fun developerEditPropertyLabel(): String

  @Message
  fun developerAddPropertyLabel(): String

  @Message
  fun developerEditPropertyTitleWithName(name: String): String

  @Message
  fun developerListItemTypeLabel(): String

  @Message
  fun developerTargetEntityLabel(): String

  @Message
  fun developerNullableLabel(): String

  @Message
  fun developerTypeChangeNote(): String

  @Message
  fun developerConstraintsLabel(): String

  @Message
  fun developerUniqueKeyLabel(): String

  @Message
  fun developerMinValueLabel(): String

  @Message
  fun developerMaxValueLabel(): String

  @Message
  fun developerStepLabel(): String

  @Message
  fun developerNoMinimumPlaceholder(): String

  @Message
  fun developerNoMaximumPlaceholder(): String

  @Message
  fun developerNoStepPlaceholder(): String

  @Message
  fun developerMinDateLabel(): String

  @Message
  fun developerMaxDateLabel(): String

  @Message
  fun developerMinTimeLabel(): String

  @Message
  fun developerMaxTimeLabel(): String

  @Message
  fun developerMinDatetimeLabel(): String

  @Message
  fun developerMaxDatetimeLabel(): String

  @Message
  fun developerMinDurationLabel(): String

  @Message
  fun developerMaxDurationLabel(): String

  @Message
  fun developerDurationPlaceholder(): String

  @Message
  fun developerMinLengthLabel(): String

  @Message
  fun developerMaxLengthLabel(): String

  @Message
  fun developerPatternLabel(): String

  @Message
  fun developerPatternPlaceholder(): String

  @Message
  fun developerMinSizeLabel(): String

  @Message
  fun developerMaxSizeLabel(): String

  @Message
  fun developerNoConstraintsMessage(): String

  @Message
  fun developerItemConstraintsLabel(): String

  @Message
  fun developerNestedPropertiesHeading(): String

  @Message
  fun developerNoNestedPropertiesMessage(): String

  @Message
  fun developerDefaultValueLabel(): String

  @Message
  fun developerNoDefaultPlaceholder(): String

  @Message
  fun developerCheckedTrueLabel(): String

  @Message
  fun developerSmartDefaultLabel(): String

  @Message
  fun developerSmartDefaultHelp(): String

  @Message
  fun developerPredefinedLabel(): String

  @Message
  fun developerSmartDefaultPlaceholder(): String

  @Message
  fun developerValueProposalsLabel(): String

  @Message
  fun developerValueProposalsHelp(): String

  @Message
  fun developerRemovePropertyModalTitle(): String

  @Message
  fun developerRemovePropertyConfirm(): String

  @Message
  fun developerNoOtherPropertiesMessage(): String

  // developer publish-version page
  @Message
  fun developerPublishBreadcrumb(): String

  @Message
  fun developerPublishVersionTitle(): String

  @Message
  fun developerLoadingVersionInfo(): String

  @Message
  fun developerNoChangesWarning(): String

  @Message
  fun developerFirstVersionText(): String

  @Message
  fun developerBreakingChangesText(): String

  @Message
  fun developerChooseReleaseTypeText(): String

  @Message
  fun developerFeaturePrefix(): String

  @Message
  fun developerBugfixPrefix(): String

  @Message
  fun developerReleaseNotesFieldLabel(): String

  @Message
  fun developerReleaseNotesPlaceholder(): String

  // developer version-diff page
  @Message
  fun developerDiffBreadcrumb(): String

  @Message
  fun developerDiffTitlePrefix(): String

  @Message
  fun developerDiffSubtitlePrefix(): String

  @Message
  fun developerAndConnector(): String

  @Message
  fun developerNoChangesBetweenVersions(): String

  @Message
  fun developerDiffAdded(): String

  @Message
  fun developerDiffRemoved(): String

  @Message
  fun developerDiffModified(): String

  // DeveloperAppResource messages/errors
  @Message
  fun developerAppNameRequiredError(): String

  @Message
  fun developerUserNotFoundError(): String

  @Message
  fun developerAppCreatedMessage(): String

  @Message
  fun developerAppUpdatedMessage(): String

  @Message
  fun developerVersionCreatedMessage(): String

  @Message
  fun developerDraftVersionDeletedMessage(): String

  @Message
  fun developerEntityNameRequiredError(): String

  @Message
  fun developerEntityAddedMessage(): String

  @Message
  fun developerEntityDeletedMessage(): String

  @Message
  fun developerEntitiesReorderedMessage(): String

  @Message
  fun developerSortCriteriaSavedMessage(): String

  @Message
  fun developerDisplayTextSavedMessage(): String

  @Message
  fun developerPropertyNameRequiredError(): String

  @Message
  fun developerPropertyTypeRequiredError(): String

  @Message
  fun developerPropertyAddedMessage(): String

  @Message
  fun developerPropertyUpdatedMessage(): String

  @Message
  fun developerConstraintsSavedMessage(): String

  @Message
  fun developerDefaultValueSavedMessage(): String

  @Message
  fun developerSmartDefaultSavedMessage(): String

  @Message
  fun developerValueProposalsSavedMessage(): String

  @Message
  fun developerTargetEntitySavedMessage(): String

  @Message
  fun developerListItemTypeSavedMessage(): String

  @Message
  fun developerItemConstraintsSavedMessage(): String

  @Message
  fun developerPropertiesReorderedMessage(): String

  @Message
  fun developerPropertyDeletedMessage(): String

  @Message
  fun developerComputedPropertyNameRequiredError(): String

  @Message
  fun developerComputedPropertyAddedMessage(): String

  @Message
  fun developerComputedPropertyUpdatedMessage(): String

  @Message
  fun developerComputedPropertyScriptSavedMessage(): String

  @Message
  fun developerComputedPropertiesReorderedMessage(): String

  @Message
  fun developerComputedPropertyDeletedMessage(): String

  @Message
  fun developerReportNameRequiredError(): String

  @Message
  fun developerReportAddedMessage(): String

  @Message
  fun developerReportSavedMessage(): String

  @Message
  fun developerReportDeletedMessage(): String

  @Message
  fun developerVersionPublishedMessage(): String

  @Message
  fun developerInvalidDisplayTextError(names: String): String

  @Message
  fun developerInvalidObjectStructureError(names: String): String

  @Message
  fun developerAppNameExistsError(): String

  @Message
  fun developerInvalidBumpTypeError(): String

  @Message
  fun developerDraftVersionExistsError(): String

  @Message
  fun developerVersionNumberExistsError(): String

  @Message
  fun developerReleaseNotesRequiredError(): String

  @Message
  fun developerInvalidObjectStructureGenericError(): String

  @Message
  fun developerEntityNameExistsError(): String

  @Message
  fun developerEntityNotFoundError(): String

  @Message
  fun developerEntityIdsMismatchError(): String

  @Message
  fun developerPropertyNameExistsError(): String

  @Message
  fun developerPropertyNotFoundError(): String

  @Message
  fun developerInvalidPropertyTypeError(): String

  @Message
  fun developerVersionNotInDraftError(): String

  @Message
  fun developerDisplayTextNullablePropertyError(): String

  @Message
  fun developerDefaultNotSupportedError(): String

  @Message
  fun developerDefaultValueInvalidError(): String

  @Message
  fun developerSmartDefaultNotSupportedError(): String

  @Message
  fun developerSmartDefaultScriptInvalidError(): String

  @Message
  fun developerValueProposalsNotSupportedError(): String

  @Message
  fun developerBothDefaultsSetError(): String

  @Message
  fun developerPropertyIdsMismatchError(): String

  @Message
  fun developerTargetEntityNotSupportedError(): String

  @Message
  fun developerTargetEntityNotFoundError(): String

  @Message
  fun developerTargetEntityRequiredError(): String

  @Message
  fun developerComputedPropertyNotFoundError(): String

  @Message
  fun developerComputedPropertyNameExistsError(): String

  @Message
  fun developerComputedPropertyTypeNotSupportedError(): String

  @Message
  fun developerListItemTypeNotSupportedError(): String

  @Message
  fun developerListItemTypeRequiredError(): String

  @Message
  fun developerListItemTypeInvalidError(): String

  @Message
  fun developerReportNameExistsError(): String

  @Message
  fun developerReportNotFoundError(): String
}
