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
  SMART_DEFAULT_SCRIPT_INVALID("APPVER-027"),
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
  NESTED_PROPERTIES_NOT_SUPPORTED("APPVER-040"),
  INVALID_OBJECT_STRUCTURE("APPVER-041"),
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

enum class AppDataError(override val code: String) : DomainError {
  INSTALLED_APP_NOT_FOUND("APPDATA-001"),
  ENTITY_NOT_FOUND("APPDATA-002"),
  CONSTRAINT_VIOLATION("APPDATA-003"),
  APP_DATA_NOT_FOUND("APPDATA-004"),
  REFERENCED_BY_NON_NULLABLE_PROPERTY("APPDATA-005"),
  PROPERTY_NOT_FOUND("APPDATA-006"),
  ;
}

data class AppDataConstraintViolationError(
  val propertyViolations: Map<String, List<PropertyConstraintViolation>>,
) : DomainError {
  override val code: String = AppDataError.CONSTRAINT_VIOLATION.code
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
}
