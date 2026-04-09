package de.chrgroth.spotify.control.domain.user

import de.chrgroth.spotify.control.domain.error.LoginError
import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserRole
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class LoginServiceTests {

  private val userRepository: UserRepositoryPort = mockk()
  private val loginService = LoginService(userRepository)

  private val testPassword = "test-password"
  private val testPasswordHash = LoginService.hashPassword(testPassword)
  private val testUser = User(
    username = "test-user",
    passwordHash = testPasswordHash,
    roles = setOf(UserRole.USER),
    createdAt = Instant.now(),
  )

  @Test
  fun `login succeeds with correct credentials`() {
    every { userRepository.findByUsername("test-user") } returns testUser

    val result = loginService.login("test-user", testPassword)

    assertThat(result.isRight()).isTrue()
    assertThat(result.getOrNull()?.username).isEqualTo("test-user")
  }

  @Test
  fun `login fails with wrong password`() {
    every { userRepository.findByUsername("test-user") } returns testUser

    val result = loginService.login("test-user", "wrong-password")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(LoginError.INVALID_CREDENTIALS)
  }

  @Test
  fun `login fails when user not found`() {
    every { userRepository.findByUsername("unknown") } returns null

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
