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
