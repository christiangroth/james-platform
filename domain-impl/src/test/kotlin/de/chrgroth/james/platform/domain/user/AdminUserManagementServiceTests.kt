package de.chrgroth.james.platform.domain.user

import de.chrgroth.james.platform.domain.error.UserAdminError
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class AdminUserManagementServiceTests {

  private val userRepository: UserRepositoryPort = mockk()
  private val service = AdminUserManagementService(userRepository)

  private val adminUser = User(
    username = Username("admin"),
    passwordHash = LoginService.hashPassword("password"),
    roles = setOf(UserRole.ADMIN),
    createdAt = Instant.now(),
  )

  private val regularUser = User(
    username = Username("user"),
    passwordHash = LoginService.hashPassword("password"),
    roles = setOf(UserRole.USER),
    createdAt = Instant.now(),
  )

  @Test
  fun `setRoles fails when assigning admin role to non-admin user and another admin already exists`() {
    every { userRepository.findByUsername(Username("user")) } returns regularUser
    every { userRepository.findAll() } returns listOf(adminUser, regularUser)

    val result = service.setRoles("user", setOf(UserRole.ADMIN), "admin")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAdminError.SINGLE_ADMIN_VIOLATION)
  }

  @Test
  fun `setRoles succeeds when assigning admin role to non-admin user and no other admin exists`() {
    val userWithNoAdmin = User(
      username = Username("user2"),
      passwordHash = LoginService.hashPassword("password"),
      roles = setOf(UserRole.USER),
      createdAt = Instant.now(),
    )
    every { userRepository.findByUsername(Username("user2")) } returns userWithNoAdmin
    every { userRepository.findAll() } returns listOf(userWithNoAdmin)
    justRun { userRepository.save(any()) }

    val result = service.setRoles("user2", setOf(UserRole.ADMIN), "user2")

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `setRoles succeeds when updating roles of existing admin user`() {
    every { userRepository.findByUsername(Username("admin")) } returns adminUser
    justRun { userRepository.save(any()) }

    val result = service.setRoles("admin", setOf(UserRole.ADMIN), "admin")

    assertThat(result.isRight()).isTrue()
  }
}
