package de.chrgroth.james.platform.domain.error

sealed interface DomainError {
  val code: String
}

enum class LoginError(override val code: String) : DomainError {
  INVALID_CREDENTIALS("LOGIN-001"),
  ;
}

enum class TokenError(override val code: String) : DomainError {
  ENCRYPTION_FAILED("TOKEN-001"),
  DECRYPTION_FAILED("TOKEN-002"),
  INVALID_FORMAT("TOKEN-003"),
  ;
}

enum class ApiTokenError(override val code: String) : DomainError {
  NAME_BLANK("API-TOKEN-001"),
  NO_SCOPES("API-TOKEN-002"),
  SCOPE_NOT_PERMITTED("API-TOKEN-003"),
  TOKEN_NOT_FOUND("API-TOKEN-004"),
  USER_NOT_FOUND("API-TOKEN-005"),
  ;
}

enum class UserProfileError(override val code: String) : DomainError {
  USER_NOT_FOUND("PROFILE-001"),
  USERNAME_ALREADY_EXISTS("PROFILE-002"),
  INVALID_CURRENT_PASSWORD("PROFILE-003"),
  BLANK_INPUT("PROFILE-004"),
  PASSWORDS_DO_NOT_MATCH("PROFILE-005"),
  ;
}

enum class UserAdminError(override val code: String) : DomainError {
  USER_NOT_FOUND("ADMIN-001"),
  USERNAME_ALREADY_EXISTS("ADMIN-002"),
  BLANK_INPUT("ADMIN-003"),
  CANNOT_DEACTIVATE_SELF("ADMIN-004"),
  CANNOT_DELETE_SELF("ADMIN-005"),
  PASSWORDS_DO_NOT_MATCH("ADMIN-006"),
  CANNOT_REMOVE_OWN_ADMIN_ROLE("ADMIN-007"),
  SINGLE_ADMIN_VIOLATION("ADMIN-008"),
  ;
}

enum class AppError(override val code: String) : DomainError {
  APP_NOT_FOUND("APP-001"),
  APP_NAME_ALREADY_EXISTS("APP-002"),
  BLANK_INPUT("APP-003"),
  ALREADY_INACTIVE("APP-004"),
  HAS_ACTIVE_INSTALLATIONS("APP-005"),
  ALREADY_ACTIVE("APP-006"),
  APP_INACTIVE("APP-007"),
  ;
}

enum class AppVersionError(override val code: String) : DomainError {
  APP_NOT_FOUND("APPVER-001"),
  VERSION_NOT_FOUND("APPVER-002"),
  VERSION_NUMBER_ALREADY_EXISTS("APPVER-003"),
  BLANK_INPUT("APPVER-004"),
  INVALID_VERSION_NUMBER_FORMAT("APPVER-005"),
  VERSION_NOT_IN_DRAFT("APPVER-006"),
  DRAFT_VERSION_ALREADY_EXISTS("APPVER-007"),
  ENTITY_NOT_FOUND("APPVER-008"),
  ENTITY_NAME_ALREADY_EXISTS("APPVER-009"),
  PROPERTY_NOT_FOUND("APPVER-010"),
  PROPERTY_NAME_ALREADY_EXISTS("APPVER-011"),
  REPORT_NOT_FOUND("APPVER-012"),
  REPORT_NAME_ALREADY_EXISTS("APPVER-013"),
  INVALID_PROPERTY_TYPE("APPVER-015"),
  INVALID_BUMP_TYPE("APPVER-016"),
  BLANK_RELEASE_NOTES("APPVER-017"),
  NO_CHANGES("APPVER-018"),
  VERSION_NOT_PUBLISHED("APPVER-019"),
  NO_PREDECESSOR_VERSION("APPVER-020"),
  DISPLAY_TEXT_USES_NULLABLE_PROPERTY("APPVER-021"),
  DISPLAY_TEXT_INVALID("APPVER-022"),
  ENTITY_IDS_MISMATCH("APPVER-023"),
  DEFAULT_NOT_SUPPORTED("APPVER-024"),
  DEFAULT_VALUE_INVALID("APPVER-025"),
  SMART_DEFAULT_NOT_SUPPORTED("APPVER-026"),
  // APPVER-027 was SMART_DEFAULT_SCRIPT_INVALID (Kotlin smart default scripts, replaced by expressions in ADR 0029); code not reused.
  VALUE_PROPOSALS_NOT_SUPPORTED("APPVER-028"),
  COMPUTED_PROPERTY_NOT_FOUND("APPVER-029"),
  COMPUTED_PROPERTY_NAME_ALREADY_EXISTS("APPVER-030"),
  COMPUTED_PROPERTY_TYPE_NOT_SUPPORTED("APPVER-031"),
  BOTH_DEFAULTS_SET("APPVER-032"),
  PROPERTY_IDS_MISMATCH("APPVER-033"),
  TARGET_ENTITY_NOT_SUPPORTED("APPVER-034"),
  TARGET_ENTITY_NOT_FOUND("APPVER-035"),
  TARGET_ENTITY_REQUIRED("APPVER-036"),
  LIST_ITEM_TYPE_NOT_SUPPORTED("APPVER-037"),
  LIST_ITEM_TYPE_REQUIRED("APPVER-038"),
  LIST_ITEM_TYPE_INVALID("APPVER-039"),
  INVALID_OBJECT_STRUCTURE("APPVER-041"),
  APP_INACTIVE("APPVER-042"),
  UNIT_NOT_SUPPORTED("APPVER-043"),
  UNIT_FAMILY_INVALID("APPVER-044"),
  UNIT_GRANULARITY_INVALID("APPVER-045"),
  INVALID_AGGREGATION_DEFINITION("APPVER-046"),
  AGGREGATION_NOT_FOUND("APPVER-047"),
  AGGREGATION_NAME_ALREADY_EXISTS("APPVER-048"),
  AGGREGATION_FUNCTION_INVALID("APPVER-049"),
  AGGREGATION_SOURCE_PROPERTY_INVALID("APPVER-050"),
  AGGREGATION_REF_PATH_INVALID("APPVER-051"),
  AGGREGATION_TIME_BUCKET_INVALID("APPVER-052"),
  AGGREGATION_TIME_PROPERTY_INVALID("APPVER-053"),
  AGGREGATION_GROUP_BY_INVALID("APPVER-054"),
  AGGREGATION_REF_PATH_AND_GROUP_BY_EXCLUSIVE("APPVER-055"),
  AGGREGATION_PERIOD_START_INVALID("APPVER-056"),
  MIGRATION_STEP_NOT_FOUND("APPVER-057"),
  MIGRATION_STEP_SOURCE_PROPERTY_NOT_FOUND("APPVER-058"),
  MIGRATION_STEP_TARGET_PROPERTY_NOT_FOUND("APPVER-059"),
  MIGRATION_STEP_TYPE_NOT_CONVERTIBLE("APPVER-060"),
  MIGRATION_STEP_TARGET_ALREADY_USED("APPVER-061"),
  MIGRATION_STEP_IDS_MISMATCH("APPVER-062"),
  INVALID_MIGRATION_STEP("APPVER-063"),
  MIGRATION_STEP_TYPE_INVALID("APPVER-064"),
  MIGRATION_STEP_UNIT_REQUIRED("APPVER-065"),
  MIGRATION_STEP_UNIT_GRANULARITY_INVALID("APPVER-066"),
  MIGRATION_STEP_FILL_VALUE_INVALID("APPVER-067"),
  REFERENCE_LOOKUP_NOT_SUPPORTED("APPVER-068"),
  REFERENCE_LOOKUP_PROPERTY_INVALID("APPVER-069"),
  INVALID_REFERENCE_LOOKUP("APPVER-070"),
  INVALID_REPORT_DEFINITION("APPVER-071"),
  INVALID_REPORTS("APPVER-072"),

  /** Another Entity of the version already is the configuration (ADR 0027). */
  CONFIGURATION_ALREADY_DEFINED("APPVER-073"),

  /** The configuration Entity cannot be the target of a REF property (ADR 0027). */
  CONFIGURATION_REFERENCED("APPVER-074"),
  INVALID_CONFIGURATION("APPVER-075"),

  /** A computed property's or smart default's expression is invalid (ADR 0029), see [InvalidExpressionError]. */
  INVALID_EXPRESSION("APPVER-076"),
  ;
}

enum class UserAppStoreError(override val code: String) : DomainError {
  APP_NOT_FOUND("STORE-001"),
  NO_PUBLISHED_VERSION("STORE-002"),
  ALREADY_INSTALLED("STORE-003"),
  NOT_INSTALLED("STORE-004"),
  INSTALLED_APP_NOT_FOUND("STORE-005"),
  ALREADY_UP_TO_DATE("STORE-006"),
  ;
}

enum class DeveloperTestInstallationError(override val code: String) : DomainError {
  APP_NOT_FOUND("TESTINST-001"),
  VERSION_NOT_FOUND("TESTINST-002"),
  INSTALLATION_NOT_FOUND("TESTINST-003"),
  ;
}

enum class TestDataGeneratorError(override val code: String) : DomainError {
  APP_NOT_FOUND("TESTDATA-001"),
  INSTALLATION_NOT_FOUND("TESTDATA-002"),
  ENTITY_NOT_FOUND("TESTDATA-003"),
  INVALID_COUNT("TESTDATA-004"),
  GENERATION_FAILED("TESTDATA-005"),
  /** Returned by an async (enqueue) [de.chrgroth.james.platform.domain.port.`in`.app.TestDataGeneratorPort.generateTestData] call while a run for the same entity is already queued/running. */
  ALREADY_GENERATING("TESTDATA-006"),
  ;
}

/** See docs/adr/0018-app-version-migration-execution-trigger.md and docs/adr/0023-migration-steps.md. */
enum class AppVersionMigrationError(override val code: String) : DomainError {
  // MIGRATION-001 was SCRIPT_FAILED (Kotlin migration scripts, removed with ADR 0029); code not reused.
  VALIDATION_FAILED_AFTER_MIGRATION("MIGRATION-002"),
  STEP_FAILED("MIGRATION-003"),
  ;
}

enum class AppDataError(override val code: String) : DomainError {
  INSTALLED_APP_NOT_FOUND("APPDATA-001"),
  ENTITY_NOT_FOUND("APPDATA-002"),
  CONSTRAINT_VIOLATION("APPDATA-003"),
  APP_DATA_NOT_FOUND("APPDATA-004"),
  REFERENCED_BY_NON_NULLABLE_PROPERTY("APPDATA-005"),
  PROPERTY_NOT_FOUND("APPDATA-006"),

  /** The installed version defines no configuration Entity (ADR 0027). */
  CONFIGURATION_NOT_DEFINED("APPDATA-007"),

  /** The configuration object exists exactly once per installation; a second one cannot be created (ADR 0027). */
  CONFIGURATION_ALREADY_EXISTS("APPDATA-008"),

  /** The configuration object can be edited but not deleted (ADR 0027). */
  CONFIGURATION_NOT_DELETABLE("APPDATA-009"),
  ;
}

/** Executing a report for an installation, see arc42 "Reports". */
enum class ReportError(override val code: String) : DomainError {
  INSTALLED_APP_NOT_FOUND("REPORT-001"),
  REPORT_NOT_FOUND("REPORT-002"),
  ;
}

enum class AggregationError(override val code: String) : DomainError {
  INSTALLED_APP_NOT_FOUND("AGG-001"),
  ;
}

enum class ImportError(override val code: String) : DomainError {
  INSTALLED_APP_NOT_FOUND("IMPORT-001"),
  INVALID_URL("IMPORT-004"),
  FETCH_FAILED("IMPORT-005"),
  INVALID_JSON_RESPONSE("IMPORT-006"),
  NOT_A_JSON_OBJECT("IMPORT-007"),
  RESPONSE_TOO_LARGE("IMPORT-008"),
  IMPORT_JOB_NOT_FOUND("IMPORT-009"),
  IMPORT_JOB_NOT_DOWNLOADED("IMPORT-010"),
  BLANK_DATA_PATH("IMPORT-011"),
  INVALID_DATA_PATH("IMPORT-012"),
  IMPORT_JOB_NOT_MAPPABLE("IMPORT-013"),
  ENTITY_DEFINITION_NOT_FOUND("IMPORT-015"),
  MAPPING_PROPERTY_NOT_FOUND("IMPORT-016"),
  IMPORT_JOB_NOT_READY("IMPORT-017"),
  CONNECTION_NOT_FOUND("IMPORT-018"),
  IMPORT_JOB_NOT_FILTERABLE("IMPORT-019"),
  DEFINITION_NOT_FOUND("IMPORT-020"),
  INVALID_CRON_SCHEDULE("IMPORT-021"),

  /** The definition has no selected data path/mapping configured yet, so a schedule cannot be set or run for it. */
  DEFINITION_NOT_CONFIGURED("IMPORT-022"),

  /** A scheduled run's freshly detected schema deviates from the definition's stored baseline; the run aborts without accepting (see `ImportService.triggerScheduledImport`). */
  SCHEMA_DRIFT_DETECTED("IMPORT-023"),

  /** A definition cannot be renamed to a blank name (see `ImportPort.renameImportDefinition`). */
  BLANK_DEFINITION_NAME("IMPORT-024"),
  ;
}

enum class ImportConnectionError(override val code: String) : DomainError {
  CONNECTION_NOT_FOUND("IMPORTCONN-001"),
  BLANK_NAME("IMPORTCONN-002"),
  BLANK_BASE_URL("IMPORTCONN-003"),
  TEST_URL_FOREIGN_ORIGIN("IMPORTCONN-004"),
  ;
}

/**
 * Carries the technical reason a source URL was rejected (malformed, unsupported scheme, unresolvable host,
 * disallowed target address, ...) alongside the generic [ImportError.INVALID_URL] code, so the UI can show it
 * to the user for debugging instead of only a generic message.
 */
data class ImportInvalidUrlError(val detail: String) : DomainError {
  override val code: String = ImportError.INVALID_URL.code
}

/**
 * Carries the technical reason fetching the source URL failed (HTTP status, timeout, connection error, ...)
 * alongside the generic [ImportError.FETCH_FAILED] code, so the UI can show it to the user for debugging
 * instead of only a generic message.
 */
data class ImportFetchFailedError(val detail: String) : DomainError {
  override val code: String = ImportError.FETCH_FAILED.code
}

data class AppDataConstraintViolationError(
  val propertyViolations: Map<String, List<PropertyConstraintViolation>>,
  val pathedViolations: List<PathedConstraintViolation> = emptyList(),
) : DomainError {
  override val code: String = AppDataError.CONSTRAINT_VIOLATION.code
}

/** Pairs a constraint violation with the human-readable property path (e.g. "Address > Street") that produced it, so nested violations remain identifiable without navigating into the nested property. */
data class PathedConstraintViolation(
  val path: String,
  val violation: PropertyConstraintViolation,
)


/** An Entity's migration step (see MigrationStep) could not be applied to one AppData object during an installation upgrade, e.g. an unconvertible value. */
data class AppVersionMigrationStepFailedError(
  val entityName: String,
  val appDataId: String,
  val versionNumber: String,
  val reason: String,
) : DomainError {
  override val code: String = AppVersionMigrationError.STEP_FAILED.code
}

/** The migrated data (after the migration steps) still violates the new EntityDefinition's constraints. */
data class AppVersionMigrationValidationFailedError(
  val entityName: String,
  val appDataId: String,
  val versionNumber: String,
  val propertyViolations: Map<String, List<PropertyConstraintViolation>>,
) : DomainError {
  override val code: String = AppVersionMigrationError.VALIDATION_FAILED_AFTER_MIGRATION.code
}

data class DisplayTextInvalidError(
  val entityNames: List<String>,
) : DomainError {
  override val code: String = AppVersionError.DISPLAY_TEXT_INVALID.code
}

data class InvalidObjectStructureError(
  val entityNames: List<String>,
) : DomainError {
  override val code: String = AppVersionError.INVALID_OBJECT_STRUCTURE.code
}

/** See docs/adr/0020-aggregation-definitions.md — an AggregationDefinition became invalid, e.g. its sourceProperty/refPath/groupBy was deleted or retyped. */
data class InvalidAggregationDefinitionError(
  val entityNames: List<String>,
) : DomainError {
  override val code: String = AppVersionError.INVALID_AGGREGATION_DEFINITION.code
}

/** See docs/adr/0023-migration-steps.md — a MigrationStep became invalid, e.g. a property it references was deleted after the step was added. */
data class InvalidMigrationStepError(
  val entityNames: List<String>,
) : DomainError {
  override val code: String = AppVersionError.INVALID_MIGRATION_STEP.code
}

/** See docs/adr/0026-reference-lookup.md — a REF property's lookup became invalid, e.g. its lookup property was deleted or is no longer the target's only unique key. */
data class InvalidReferenceLookupError(
  val entityNames: List<String>,
) : DomainError {
  override val code: String = AppVersionError.INVALID_REFERENCE_LOOKUP.code
}

/** A report definition (YAML) failed parsing or validation on save or preview; [errors] locate every problem found. */
data class InvalidReportDefinitionError(
  val errors: List<de.chrgroth.james.platform.domain.model.app.ReportDefinitionError>,
) : DomainError {
  override val code: String = AppVersionError.INVALID_REPORT_DEFINITION.code
}

/** Publishing failed because the definitions of these reports are invalid, e.g. an entity or property they use was deleted or renamed. */
/** Publish found configuration Entities (ADR 0027) that are invalid: several of them, referenced by a REF, or a required property without default. */
data class InvalidConfigurationError(
  val entityNames: List<String>,
) : DomainError {
  override val code: String = AppVersionError.INVALID_CONFIGURATION.code
}

/** What is wrong with an expression of a computed property or smart default (ADR 0029); the args of [InvalidExpressionError] by code. */
enum class ExpressionErrorCode {
  /** args: 1-based position, detail (machine-oriented, e.g. `unexpected ')'`). */
  SYNTAX,

  /** args: function, allowed functions (comma separated). */
  UNKNOWN_FUNCTION,

  /** args: function, allowed functions (comma separated). The function exists, but not here (e.g. `last` in a computed property). */
  FUNCTION_NOT_AVAILABLE,

  /** args: function. `last`/`next_day` need a field name as argument. */
  FIELD_ARGUMENT_REQUIRED,

  /** args: field name. Neither a field of the entity nor a computed property defined before this one. */
  UNKNOWN_FIELD,

  /** args: field path after `config.`. The version has no configuration or the configuration no such field. */
  UNKNOWN_CONFIG_FIELD,

  /** args: operator, type (an expression type: NUMBER, TEXT, BOOLEAN, DATE, DATETIME, TIME). */
  OPERAND_TYPE,

  /** args: function, 1-based argument, type. */
  ARGUMENT_TYPE,

  /** args: operator (or `case`), left type, right type. */
  INCOMPARABLE,

  /** args: function, types (comma separated). */
  MIXED_TYPES,

  /** args: expected property type (e.g. DOUBLE), actual expression type. */
  RESULT_TYPE,
}

/** A computed property's or smart default's expression failed parsing or type checking on save (ADR 0029). */
data class InvalidExpressionError(
  val reason: ExpressionErrorCode,
  val args: List<String> = emptyList(),
) : DomainError {
  override val code: String = AppVersionError.INVALID_EXPRESSION.code
}

data class InvalidReportsError(
  val reportNames: List<String>,
) : DomainError {
  override val code: String = AppVersionError.INVALID_REPORTS.code
}

sealed class PropertyConstraintViolation(override val code: String) : DomainError {
  data object UniqueKeyViolation : PropertyConstraintViolation("PROP-002")
  data class MinValueViolation(val min: Number) : PropertyConstraintViolation("PROP-003")
  data class MaxValueViolation(val max: Number) : PropertyConstraintViolation("PROP-004")
  data class MinLengthViolation(val min: Int) : PropertyConstraintViolation("PROP-005")
  data class MaxLengthViolation(val max: Int) : PropertyConstraintViolation("PROP-006")
  data class PatternViolation(val regex: String) : PropertyConstraintViolation("PROP-007")
  data class MinSizeViolation(val min: Int) : PropertyConstraintViolation("PROP-008")
  data class MaxSizeViolation(val max: Int) : PropertyConstraintViolation("PROP-009")
  data object InvalidReferenceViolation : PropertyConstraintViolation("PROP-010")
  data class MinDateViolation(val min: java.time.LocalDate) : PropertyConstraintViolation("PROP-011")
  data class MaxDateViolation(val max: java.time.LocalDate) : PropertyConstraintViolation("PROP-012")
  data class MinTimeViolation(val min: java.time.LocalTime) : PropertyConstraintViolation("PROP-013")
  data class MaxTimeViolation(val max: java.time.LocalTime) : PropertyConstraintViolation("PROP-014")
  data class MinDatetimeViolation(val min: java.time.LocalDateTime) : PropertyConstraintViolation("PROP-015")
  data class MaxDatetimeViolation(val max: java.time.LocalDateTime) : PropertyConstraintViolation("PROP-016")
  // PROP-017, PROP-018, PROP-020 were MinDurationViolation/MaxDurationViolation/InvalidDurationFormatViolation, removed with DURATION (ADR 0017); codes not reused.
  data class StepViolation(val step: Number) : PropertyConstraintViolation("PROP-019")
  data object InvalidUnitFormatViolation : PropertyConstraintViolation("PROP-021")
  data object NonIntegerUnitValueViolation : PropertyConstraintViolation("PROP-022")
  // A REF property entered by lookup text (ADR 0026) matched no target object and the property does not auto-create one.
  data object ReferenceLookupNotFoundViolation : PropertyConstraintViolation("PROP-023")
  // A non-nullable property got no value on create/update (BOOLEAN excluded: an unchecked checkbox submits nothing and means false).
  data object RequiredValueViolation : PropertyConstraintViolation("PROP-024")
}
