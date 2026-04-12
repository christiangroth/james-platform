package de.chrgroth.james.platform.domain.user

import de.chrgroth.james.platform.domain.error.LoginError
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class LoginServiceTests {

  private val userRepository: UserRepositoryPort = mockk()
  private val loginService = LoginService(userRepository)

  private val testPassword = "test-password"
  private val testPasswordHash = LoginService.hashPassword(testPassword)
  private val testUser = User(
    username = Username("test-user"),
    passwordHash = testPasswordHash,
    roles = setOf(UserRole.USER),
    createdAt = Instant.now(),
  )

  @Test
  fun `login succeeds with correct credentials`() {
    every { userRepository.findByUsername(Username("test-user")) } returns testUser
    justRun { userRepository.save(any()) }

    val result = loginService.login("test-user", testPassword)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.username).isEqualTo(Username("test-user"))
    verify { userRepository.save(match { it.lastLoginAt != null }) }
  }

  @Test
  fun `login fails with wrong password`() {
    every { userRepository.findByUsername(Username("test-user")) } returns testUser

    val result = loginService.login("test-user", "wrong-password")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(LoginError.INVALID_CREDENTIALS)
  }

  @Test
  fun `login fails when user not found`() {
    every { userRepository.findByUsername(Username("unknown")) } returns null

    val result = loginService.login("unknown", "any-password")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(LoginError.INVALID_CREDENTIALS)
  }

  @Test
  fun `password hash and verify round-trip`() {
    val hash = LoginService.hashPassword("my-secret")
    assertThat(LoginService.verifyPassword("my-secret", hash)).isTrue()
    assertThat(LoginService.verifyPassword("wrong", hash)).isFalse()
  }
}
