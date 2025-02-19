package de.chrgroth.james.platform.domain.user

import arrow.core.ValidatedNel
import arrow.core.invalidNel
import arrow.core.validNel
import arrow.core.zip
import de.chrgroth.james.DomainError
import java.util.UUID

@JvmInline
// TODO change to UUID, but it's not serializable
value class UserId(val value: String) {
  companion object {
    operator fun invoke(): UserId = UserId(UUID.randomUUID().toString())
  }
}

fun String.toUserid(): UserId =
  UserId(this)

data class User(
  val id: UserId,
  val username: String,
  val passwordHash: String,
  val passwordStatus: PasswordStatus,
  val roles: Set<UserRole>,
  val status: UserStatus,
  val statusReason: String?,
  val deactivationCounter: UShort,
) {

  fun changePassword(passwordHash: String): ValidatedNel<DomainError, User> =
    ensure(UserStatus.ACTIVE, UserDomainErrorCodes.USER_DEACTIVATED)
      .map {
        it.copy(
          passwordHash = passwordHash,
          passwordStatus = PasswordStatus.PERMANENT,
          deactivationCounter = 0u,
        )
      }

  fun resetPassword(passwordHash: String): ValidatedNel<DomainError, User> =
    ensure(UserStatus.ACTIVE, UserDomainErrorCodes.USER_DEACTIVATED)
      .map {
        it.copy(
          passwordHash = passwordHash,
          passwordStatus = PasswordStatus.ONE_TIME,
          deactivationCounter = 0u,
        )
      }

  fun deactivate(statusReason: String): ValidatedNel<DomainError, User> =
    ensure(UserStatus.ACTIVE, UserDomainErrorCodes.USER_ALREADY_INACTIVE)
      .map {
        it.copy(
          status = UserStatus.INACTIVE,
          statusReason = statusReason,
        )
      }

  fun activate(): ValidatedNel<DomainError, User> =
    ensure(UserStatus.INACTIVE, UserDomainErrorCodes.USER_ALREADY_ACTIVE)
      .map {
        it.copy(
          status = UserStatus.ACTIVE,
          statusReason = null,
        )
      }

  private fun ensure(expectedStatus: UserStatus, errorCode: UserDomainErrorCodes): ValidatedNel<DomainError, User> =
    if (status == expectedStatus) {
      this.validNel()
    } else {
      DomainError(
        code = errorCode,
        errorMessage = null
      ).invalidNel()
    }

  companion object {

    // TODO run checks in parallel
    operator fun invoke(username: String, passwordHash: String, roles: Set<UserRole>): ValidatedNel<DomainError, User> =
      roles.check().zip(username.check()) { _, checkedUsername ->
        User(
          id = UserId(),
          username = checkedUsername,
          passwordHash = passwordHash,
          passwordStatus = PasswordStatus.ONE_TIME,
          roles = roles,
          status = UserStatus.ACTIVE,
          statusReason = null,
          deactivationCounter = 0u,
        )
      }

    private fun Set<UserRole>.check(): ValidatedNel<DomainError, Unit> = when {
      isEmpty() ->
        DomainError(
          code = UserDomainErrorCodes.REGISTRATION_USER_ROLES_EMPTY,
          errorMessage = null,
        ).invalidNel()

      contains(UserRole.ADMIN) && size > 1 ->
        DomainError(
          code = UserDomainErrorCodes.REGISTRATION_USER_ROLES_ADMIN_MUST_BE_EXCLUSIVE,
          errorMessage = null,
        ).invalidNel()

      else -> ValidatedNel.validNel(Unit)
    }

    // TODO validate username
    private fun String.check(): ValidatedNel<DomainError, String> =
      this.trim().validNel()

    @Suppress("LongParameterList")
    fun fromEntity(
      id: UserId,
      username: String,
      passwordHash: String,
      passwordStatus: PasswordStatus,
      roles: Set<UserRole>,
      status: UserStatus,
      statusReason: String?,
      deactivationCounter: UShort,
    ): User = User(
      id = id,
      username = username,
      passwordHash = passwordHash,
      passwordStatus = passwordStatus,
      roles = roles,
      status = status,
      statusReason = statusReason,
      deactivationCounter = deactivationCounter,
    )
  }
}

enum class PasswordStatus {
  ONE_TIME, PERMANENT
}

const val USER_ROLE_ADMIN = "ADMIN"
const val USER_ROLE_DEVELOPER = "DEVELOPER"
const val USER_ROLE_USER = "USER"

enum class UserRole(val value: String) {
  ADMIN(USER_ROLE_ADMIN),
  DEVELOPER(USER_ROLE_DEVELOPER),
  USER(USER_ROLE_USER)
}

enum class UserStatus {
  ACTIVE, INACTIVE
}
