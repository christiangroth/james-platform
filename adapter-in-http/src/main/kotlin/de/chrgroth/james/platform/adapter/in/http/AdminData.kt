package de.chrgroth.james.platform.adapter.`in`.http

import de.chrgroth.james.platform.domain.user.USER_ROLE_ADMIN
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.port.`in`.UserQueryPort
import jakarta.annotation.security.RolesAllowed
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/api/admin/users")
@RolesAllowed(value = [USER_ROLE_ADMIN])
class UserResourceManual @Inject constructor(
  private val userQueryPort: UserQueryPort,
) {

  // TOD use OpenAPU
  // TODO error handling
  // TODO paging
  // TODO logging
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  fun getAllUsers(): List<UserResponse> {
    return userQueryPort.all()
      .fold(
        { emptyList() },
        { users -> users.map { UserResponse.fromDomain(it) } }
      )
  }
}

data class UserResponse(
  val id: String,
  val username: String,
  val roles: List<String>,
  val passwordStatus: String,
  val status: String,
  val statusReason: String?,
  val deactivationCounter: Int,
) {
  companion object {
    fun fromDomain(user: User) = UserResponse(
      id = user.id.value,
      username = user.username,
      roles = user.roles.map { it.value },
      passwordStatus = user.passwordStatus.name,
      status = user.status.name,
      statusReason = user.statusReason,
      deactivationCounter = user.deactivationCounter.toInt()
    )
  }
}
