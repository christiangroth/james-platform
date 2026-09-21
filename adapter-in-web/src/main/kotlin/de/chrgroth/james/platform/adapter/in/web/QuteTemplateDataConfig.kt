package de.chrgroth.james.platform.adapter.`in`.web

import de.chrgroth.james.platform.domain.model.app.App
import de.chrgroth.james.platform.domain.model.app.AppVersion
import de.chrgroth.james.platform.domain.model.app.ComputedProperty
import de.chrgroth.james.platform.domain.model.app.DiffLine
import de.chrgroth.james.platform.domain.model.app.EntityDefinition
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.Property
import de.chrgroth.james.platform.domain.model.app.Report
import de.chrgroth.james.platform.domain.model.app.SectionDiff
import de.chrgroth.james.platform.domain.model.app.SortCriteria
import de.chrgroth.james.platform.domain.model.app.VersionDiff
import de.chrgroth.james.platform.domain.model.infra.ConfigEntry
import de.chrgroth.james.platform.domain.model.infra.ConfigurationStats
import de.chrgroth.james.platform.domain.model.infra.CronjobStats
import de.chrgroth.james.platform.domain.model.infra.HealthStats
import de.chrgroth.james.platform.domain.model.infra.HttpResponseStats
import de.chrgroth.james.platform.domain.model.infra.ImportCleanupStats
import de.chrgroth.james.platform.domain.model.infra.MongoCollectionStats
import de.chrgroth.james.platform.domain.model.infra.MongoQueryStats
import de.chrgroth.james.platform.domain.model.infra.ScriptExecutionStats
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.viewer.MongoViewerField
import de.chrgroth.james.platform.domain.model.viewer.MongoViewerResult
import de.chrgroth.james.platform.domain.port.`in`.app.InstalledAppInfo
import de.chrgroth.james.platform.domain.port.`in`.app.PublishedAppDetail
import de.chrgroth.james.platform.domain.port.`in`.app.PublishedAppInfo
import io.quarkus.qute.TemplateData

// Qute resolves properties like `stats.xyz` in templates via reflection when a value is bound untyped (no
// @CheckedTemplate, e.g. `template.data("stats", someObject)`). Classes bound this way aren't reliably reachable by
// GraalVM's native-image analysis, which surfaces as "Property not found"/blank output only in native mode - this
// can hit plain constructor properties too, not just getter-only ones. @TemplateData generates a build-time
// ValueResolver instead of relying on runtime reflection, avoiding the issue entirely (in both JVM and native mode).
// Every class below is bound untyped to a template (directly or as part of another bound class's object graph) and
// is actually read via a raw property expression in at least one .html template - see #672.
//
// Not registered here on purpose, even though reachable from a registered class's fields: types that are part of
// the object graph but are never read via a raw template property expression today (only ever consumed through the
// @TemplateExtension helper functions in TemplateFormattingExtensions.kt / PropertyLabelTemplateExtensions.kt,
// which don't need the underlying type registered) - e.g. PropertyConstraint (sealed) and its implementations,
// PropertyUnit, Granularity/TimeGranularity/DistanceGranularity, UnitFamily, AggregationDefinition/
// AggregationDefinitionId/AggregationFunction/TimeBucket. If a future template starts reading one of these
// directly, register it here at that point.
@TemplateData(target = HealthStats::class)
@TemplateData(target = MongoCollectionStats::class)
@TemplateData(target = MongoQueryStats::class)
@TemplateData(target = HttpResponseStats::class)
@TemplateData(target = CronjobStats::class)
@TemplateData(target = ConfigurationStats::class)
@TemplateData(target = ConfigEntry::class)
@TemplateData(target = ScriptExecutionStats::class)
@TemplateData(target = ImportCleanupStats::class)
@TemplateData(target = MongoViewerResult::class)
@TemplateData(target = MongoViewerField::class)
@TemplateData(target = User::class)
@TemplateData(target = App::class)
@TemplateData(target = AppVersion::class)
@TemplateData(target = EntityDefinition::class)
@TemplateData(target = Property::class)
@TemplateData(target = ComputedProperty::class)
@TemplateData(target = SortCriteria::class)
@TemplateData(target = Report::class)
@TemplateData(target = VersionDiff::class)
@TemplateData(target = SectionDiff::class)
@TemplateData(target = DiffLine::class)
@TemplateData(target = InstalledApp::class)
@TemplateData(target = InstalledAppInfo::class)
@TemplateData(target = PublishedAppInfo::class)
@TemplateData(target = PublishedAppDetail::class)
// local view/row DTOs defined at top level in the Resource files that build them (import wizard, app-store,
// developer app, dashboard, release notes, logs pages)
@TemplateData(target = DataPathRow::class)
@TemplateData(target = JsonStructureRow::class)
@TemplateData(target = ImportJobRow::class)
@TemplateData(target = EntityOptionRow::class)
@TemplateData(target = AppOptionRow::class)
@TemplateData(target = ConnectionOptionRow::class)
@TemplateData(target = SchemaFieldOptionRow::class)
@TemplateData(target = SchemaPanelRow::class)
@TemplateData(target = FilterRuleRow::class)
@TemplateData(target = FilterModeOptionRow::class)
@TemplateData(target = FilterOperatorOptionRow::class)
@TemplateData(target = PropertyOptionRow::class)
@TemplateData(target = ConversionOptionRow::class)
@TemplateData(target = ReferenceLookupCriterionRow::class)
@TemplateData(target = MappingPropertyRow::class)
@TemplateData(target = DryRunIssueRow::class)
@TemplateData(target = DryRunPropertyRow::class)
@TemplateData(target = DryRunObjectRow::class)
@TemplateData(target = DryRunSkippedReasonRow::class)
@TemplateData(target = ImportConnectionRow::class)
@TemplateData(target = ImportDefinitionGroupRow::class)
@TemplateData(target = ImportHistoryRowRow::class)
@TemplateData(target = AppDataRow::class)
@TemplateData(target = AppDataPropertyView::class)
@TemplateData(target = AppDataComputedPropertyView::class)
@TemplateData(target = AppDataDetail::class)
@TemplateData(target = AppDataImportProvenanceView::class)
@TemplateData(target = EntityTab::class)
@TemplateData(target = AggregationView::class)
@TemplateData(target = DashboardAppInfo::class)
@TemplateData(target = TestInstallationInfo::class)
@TemplateData(target = PropertyBreadcrumb::class)
@TemplateData(target = EntityDataCount::class)
@TemplateData(target = DashboardInstalledApp::class)
@TemplateData(target = ReleaseNotesEntry::class)
@TemplateData(target = ReleaseNotesMinorVersionGroup::class)
@TemplateData(target = UiLogGroup::class)
@TemplateData(target = UiLogEntry::class)
class QuteTemplateDataConfig
