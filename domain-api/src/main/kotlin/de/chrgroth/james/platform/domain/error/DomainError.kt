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
  ;
}
