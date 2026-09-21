package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.VersionDiff
import de.chrgroth.james.platform.domain.model.infra.ConfigurationStats
import de.chrgroth.james.platform.domain.model.infra.HealthStats
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.viewer.MongoViewerResult
import de.chrgroth.james.platform.domain.port.`in`.app.InstalledAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.PublishedAppDetail
import de.chrgroth.james.platform.domain.port.`in`.app.PublishedAppInfo
import io.quarkus.qute.CheckedTemplate
import io.quarkus.qute.RawString
import io.quarkus.qute.TemplateInstance
import java.time.Instant

// Type-safe template/fragment bindings, validated at build time by Qute. Method names must match the template file's
// base name (for a page) or "templateBaseName$fragmentId" (for a `{#fragment id="..."}` section), and are kept in a
// single top-level object per basePath (rather than nested per resource) so the default "flat" template lookup
// applies within each directory. Templates nested in a subdirectory of src/main/resources/templates (ui/admin,
// ui/developer, ui, ui/user) need their own @CheckedTemplate class with a matching basePath, since basePath applies
// to the whole class/object.
@CheckedTemplate
object Templates {

  @JvmStatic
  external fun config(stats: ConfigurationStats): TemplateInstance

  @JvmStatic
  external fun docs(title: String, markdownContent: String): TemplateInstance

  @JvmStatic
  external fun error(statusCode: Int, errorType: String, errorMessage: String?, stackTrace: String?): TemplateInstance

  @JvmStatic
  external fun health(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_cronjobs`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_mongodb_collections`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_mongodb_queries`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_http_responses`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_scripting`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun `health$snippet_import_cleanup`(stats: HealthStats): TemplateInstance

  @JvmStatic
  external fun login(errorMessage: String?): TemplateInstance

  @JvmStatic
  external fun logs(entries: List<UiLogEntry>, isGroupedView: Boolean, groups: List<UiLogGroup>): TemplateInstance

  @JvmStatic
  external fun `mongodb-viewer`(result: MongoViewerResult, pageSizes: List<Int>): TemplateInstance

  @JvmStatic
  external fun `release-notes`(groups: List<ReleaseNotesMinorVersionGroup>): TemplateInstance
}

@CheckedTemplate(basePath = "ui/admin")
object AdminTemplates {

  @JvmStatic
  external fun users(users: List<User>, allRoles: List<UserRole>): TemplateInstance

  @JvmStatic
  external fun `users$users_table`(users: List<User>, allRoles: List<UserRole>): TemplateInstance

  @JvmStatic
  external fun dashboard(userCount: Int): TemplateInstance
}

@CheckedTemplate(basePath = "ui/developer")
object DeveloperTemplates {

  @JvmStatic
  external fun dashboard(username: String, apps: List<DashboardAppInfo>): TemplateInstance

  @JvmStatic
  external fun `app-overview`(
    app: App,
    versions: List<AppVersion>,
    hasDraft: Boolean,
    versionsWithDiff: Set<String>,
    installationCount: Int,
    testInstallations: List<TestInstallationInfo>,
  ): TemplateInstance

  @JvmStatic
  external fun `version-editor`(
    app: App,
    version: AppVersion,
    isDraft: Boolean,
    hasDiff: Boolean,
    selectedEntity: EntityDefinition?,
    selectedReport: Report?,
    predefinedSmartDefaultsJson: RawString,
    currentProperties: List<Property>,
    currentPropertiesJson: RawString,
    path: String,
    breadcrumb: List<PropertyBreadcrumb>,
    isNestedLevel: Boolean,
    testInstallations: List<TestInstallationInfo>,
  ): TemplateInstance

  @JvmStatic
  external fun `version-diff`(app: App, diff: VersionDiff): TemplateInstance

  @JvmStatic
  external fun `edit-property`(
    app: App,
    version: AppVersion,
    selectedEntity: EntityDefinition,
    selectedProperty: Property?,
    path: String,
    breadcrumb: List<PropertyBreadcrumb>,
    parentPropertyId: String?,
    parentPath: String,
    predefinedSmartDefaultsJson: RawString,
    currentPropertiesJson: RawString,
  ): TemplateInstance

  @JvmStatic
  external fun `publish-version`(app: App, version: AppVersion): TemplateInstance
}

@CheckedTemplate(basePath = "ui")
object UiTemplates {

  @JvmStatic
  external fun profile(username: String, createdAt: Instant?, lastLoginAt: Instant?, successMessage: String?, errorMessage: String?): TemplateInstance
}

@CheckedTemplate(basePath = "ui/user")
object UserTemplates {

  @JvmStatic
  external fun `app-store`(apps: List<PublishedAppInfo>, installedAppIds: Set<String>): TemplateInstance

  @JvmStatic
  external fun `app-store-detail`(detail: PublishedAppDetail, installedApp: InstalledAppInfo?): TemplateInstance

  @JvmStatic
  external fun `app-detail`(info: InstalledAppInfo, entityTabs: List<EntityTab>, pageSize: Int): TemplateInstance

  @JvmStatic
  external fun `app-entity-detail`(info: InstalledAppInfo, entity: EntityTab, aggregations: List<AggregationView>, pageSize: Int): TemplateInstance

  @JvmStatic
  external fun `app-data-new`(
    info: InstalledAppInfo,
    entity: EntityDefinition,
    smartDefaults: Map<String, String?>,
    referenceOptions: Map<String, List<AppDataRow>>,
    entityListUrl: String,
    objectFieldsJson: RawString,
    referenceOptionsJson: RawString,
  ): TemplateInstance

  @JvmStatic
  external fun `app-data-edit`(
    info: InstalledAppInfo,
    detail: AppDataDetail,
    entityListUrl: String,
    objectFieldsJson: RawString,
    objectValuesJson: RawString,
    referenceOptionsJson: RawString,
  ): TemplateInstance

  @JvmStatic
  external fun dashboard(username: String, installedApps: List<DashboardInstalledApp>): TemplateInstance

  @JvmStatic
  external fun `import-connections`(connections: List<ImportConnectionRow>): TemplateInstance

  @JvmStatic
  external fun `import-connections$connections_table`(connections: List<ImportConnectionRow>): TemplateInstance

  @JvmStatic
  external fun `import-definitions`(definitions: List<ImportDefinitionRow>): TemplateInstance

  @JvmStatic
  external fun `import-definitions$definitions_table`(definitions: List<ImportDefinitionRow>): TemplateInstance

  @JvmStatic
  external fun imports(
    jobs: List<ImportJobRow>,
    appOptions: List<AppOptionRow>,
    connectionOptions: List<ConnectionOptionRow>,
    hasConnections: Boolean,
  ): TemplateInstance

  @JvmStatic
  external fun `imports$imports_table`(jobs: List<ImportJobRow>): TemplateInstance

  @JvmStatic
  external fun `import-job`(
    job: ImportJobRow,
    targetEntityName: String,
    targetEntityUrl: String,
    pageHeading: String,
    sourceUrl: String,
    structureRows: List<JsonStructureRow>,
    schemaPanelRows: List<SchemaPanelRow>,
    appActive: Boolean,
  ): TemplateInstance

  @JvmStatic
  external fun `import-filter`(
    importJobId: String,
    targetEntityName: String,
    pageHeading: String,
    filterRuleRows: List<FilterRuleRow>,
    schemaFieldOptions: List<SchemaFieldOptionRow>,
    schemaPanelRows: List<SchemaPanelRow>,
    modeOptions: List<FilterModeOptionRow>,
    operatorOptions: List<FilterOperatorOptionRow>,
    totalRecordCount: Int,
    matchingRecordCount: Int,
    awaitingDataPathSelection: Boolean,
    filterable: Boolean,
    mappable: Boolean,
    readyForDryRun: Boolean,
    appActive: Boolean,
  ): TemplateInstance

  @JvmStatic
  external fun `import-mapping`(
    importJobId: String,
    isReady: Boolean,
    targetEntityName: String,
    pageHeading: String,
    propertyRows: List<MappingPropertyRow>,
    schemaFieldOptions: List<SchemaFieldOptionRow>,
    schemaPanelRows: List<SchemaPanelRow>,
    conversionOptions: List<ConversionOptionRow>,
    awaitingDataPathSelection: Boolean,
    filterable: Boolean,
    mappable: Boolean,
    readyForDryRun: Boolean,
    appActive: Boolean,
  ): TemplateInstance

  @JvmStatic
  external fun `import-dry-run`(
    importJobId: String,
    targetEntityName: String,
    pageHeading: String,
    totalCount: Int,
    validCount: Int,
    skippedCount: Int,
    invalidCount: Int,
    validObjectsColumns: List<String>,
    validObjects: List<DryRunObjectRow>,
    invalidObjects: List<DryRunObjectRow>,
    skippedReasons: List<DryRunSkippedReasonRow>,
    awaitingDataPathSelection: Boolean,
    filterable: Boolean,
    mappable: Boolean,
    readyForDryRun: Boolean,
    appActive: Boolean,
  ): TemplateInstance
}
