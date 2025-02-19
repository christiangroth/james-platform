package de.chrgroth.james.platform.domain.user

import de.chrgroth.james.DomainErrorCode
import de.chrgroth.james.DomainErrorCode.LogLevel
import de.chrgroth.james.DomainErrorCode.LogLevel.ERROR

enum class UserDomainErrorCodes : DomainErrorCode {
  REGISTRATION_USERNAME_EXISTS,
  REGISTRATION_USER_ROLES_EMPTY,
  REGISTRATION_USER_ROLES_ADMIN_MUST_BE_EXCLUSIVE,

  USER_UNKNOWN,
  USER_AUTHENTICATION_FAILED,
  USER_DEACTIVATED,
  USER_ALREADY_INACTIVE,
  USER_ALREADY_ACTIVE,

  USER_QUERY_FAILED {
    override fun logLevel(): LogLevel = ERROR
  };

  override val prefix = "TYPESYSTEM"
  override val id = ordinal.toLong()
}
