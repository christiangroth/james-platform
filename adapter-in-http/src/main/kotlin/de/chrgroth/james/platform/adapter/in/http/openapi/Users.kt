package de.chrgroth.james.platform.adapter.`in`.http.openapi

import de.chrgroth.james.platform.adapter.`in`.http.CustomStatusException
import de.chrgroth.james.platform.adapter.`in`.http.DomainErrorsException
import de.chrgroth.james.platform.adapter.incoming.http.api.UserApi
import de.chrgroth.james.platform.adapter.incoming.http.api.model.ChangePasswordData
import de.chrgroth.james.platform.adapter.incoming.http.api.model.DeactivationData
import de.chrgroth.james.platform.adapter.incoming.http.api.model.PasswordStatus
import de.chrgroth.james.platform.adapter.incoming.http.api.model.RegistrationData
import de.chrgroth.james.platform.adapter.incoming.http.api.model.User
import de.chrgroth.james.platform.adapter.incoming.http.api.model.UserRole
import de.chrgroth.james.platform.adapter.incoming.http.api.model.UserStatus
import de.chrgroth.james.platform.domain.user.USER_ROLE_ADMIN
import de.chrgroth.james.platform.domain.user.port.`in`.UserCommandPort
import de.chrgroth.james.platform.domain.user.port.`in`.UserQueryPort
import de.chrgroth.james.platform.domain.user.toUserid
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.ws.rs.PathParam
import jakarta.ws.rs.core.Response

typealias ApiUser = User
typealias ApiPasswordStatus = PasswordStatus
typealias ApiUserStatus = UserStatus
typealias ApiUserRole = UserRole

typealias DomainUser = de.chrgroth.james.platform.domain.user.User
typealias DomainPasswordStatus = de.chrgroth.james.platform.domain.user.PasswordStatus
typealias DomainUserStatus = de.chrgroth.james.platform.domain.user.UserStatus
typealias DomainUserRole = de.chrgroth.james.platform.domain.user.UserRole

// TODO logging
// TODO most part are not rest but command pattern like

@RolesAllowed(value = [USER_ROLE_ADMIN])
@Suppress("Unused")
internal class UsersResource : UserApi {

  @Inject
  private lateinit var query: UserQueryPort

  @Inject
  private lateinit var command: UserCommandPort

  // TODO need List and defined sorting instead of Set?
  override fun getAllUsers(): List<ApiUser> =
    query.all().fold({
      throw DomainErrorsException(it)
    }, { users ->
      users.map { it.toApiUser() }
    })

  // TODO return 201 Created
  // TODO needs to return at least userId
  override fun registration(registrationData: RegistrationData) {
    command.register(
      username = registrationData.username,
      password = registrationData.password,
      roles = registrationData.roles.map { it.toDomainUserRole() }.toSet(),
    ).tapInvalid {
      throw DomainErrorsException(it)
    }
  }

  override fun getUserById(userId: String): ApiUser =
    query.byId(userId.toUserid()).fold({
      throw DomainErrorsException(it)
    }, {
      it?.toApiUser() ?: throw CustomStatusException(Response.Status.NOT_FOUND)
    })

  // TODO return 204 No Content
  // TODO generate and return new password?
  override fun resetPassword(
    @PathParam("userId") userId: String,
    data: ChangePasswordData,
  ) {
    command.resetPassword(userId.toUserid(), data.password).tapInvalid {
      throw DomainErrorsException(it)
    }
  }

  // TODO return 204 No Content
  override fun deactivateUser(@PathParam("userId") userId: String, deactivationData: DeactivationData) {
    command.deactivate(userId.toUserid(), deactivationData.reason).tapInvalid {
      throw DomainErrorsException(it)
    }
  }

  // TODO return 204 No Content
  override fun activateUser(@PathParam("userId") userId: String) {
    command.activate(userId.toUserid()).tapInvalid {
      throw DomainErrorsException(it)
    }
  }

  // TODO return 204 No Content
  override fun deleteUserById(@PathParam("userId") userId: String) {
    command.delete(userId.toUserid()).tapInvalid {
      throw DomainErrorsException(it)
    }
  }
}

fun DomainUser.toApiUser() =
  ApiUser(
    id = id.value,
    username = username,
    roles = roles.map { it.toApiUserRole() },
    passwordStatus = passwordStatus.toApiUserPasswordStatus(),
    status = status.toApiUserStatus(),
    statusReason = statusReason,
    deactivationCounter = deactivationCounter.toInt(),
  )

fun DomainPasswordStatus.toApiUserPasswordStatus(): ApiPasswordStatus =
  when (this) {
    DomainPasswordStatus.ONE_TIME -> ApiPasswordStatus.ONE_TIME
    DomainPasswordStatus.PERMANENT -> ApiPasswordStatus.PERMANENT
  }

fun DomainUserStatus.toApiUserStatus(): ApiUserStatus =
  when (this) {
    DomainUserStatus.ACTIVE -> ApiUserStatus.ACTIVE
    DomainUserStatus.INACTIVE -> ApiUserStatus.INACTIVE
  }

fun DomainUserRole.toApiUserRole(): ApiUserRole =
  when (this) {
    DomainUserRole.ADMIN -> ApiUserRole.ADMIN
    DomainUserRole.DEVELOPER -> ApiUserRole.DEVELOPER
    DomainUserRole.USER -> ApiUserRole.USER
  }

fun ApiUserRole.toDomainUserRole(): DomainUserRole =
  when (this) {
    ApiUserRole.ADMIN -> DomainUserRole.ADMIN
    ApiUserRole.DEVELOPER -> DomainUserRole.DEVELOPER
    ApiUserRole.USER -> DomainUserRole.USER
  }
