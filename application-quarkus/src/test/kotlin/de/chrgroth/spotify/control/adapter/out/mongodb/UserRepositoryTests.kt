package de.chrgroth.spotify.control.adapter.out.mongodb

import de.chrgroth.spotify.control.domain.model.user.User
import de.chrgroth.spotify.control.domain.model.user.UserRole
import de.chrgroth.spotify.control.domain.port.out.user.UserRepositoryPort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

@QuarkusTest
class UserRepositoryTests {

  @Inject
  lateinit var userRepository: UserRepositoryPort

  @Test
  fun `findByUsername returns null when user does not exist`() {
    assertThat(userRepository.findByUsername("unknown-user")).isNull()
  }

  @Test
  fun `save creates user and findByUsername retrieves it`() {
    val username = "test-${UUID.randomUUID()}"
    val user = User(
      username = username,
      passwordHash = "hashed-password",
      roles = setOf(UserRole.USER),
      createdAt = Instant.now(),
    )

    userRepository.save(user)

    val found = userRepository.findByUsername(username)
    assertThat(found).isNotNull()
    assertThat(found!!.username).isEqualTo(username)
    assertThat(found.passwordHash).isEqualTo("hashed-password")
    assertThat(found.roles).containsExactly(UserRole.USER)
  }

  @Test
  fun `save updates existing user`() {
    val username = "test-${UUID.randomUUID()}"
    val original = User(
      username = username,
      passwordHash = "original-hash",
      roles = setOf(UserRole.USER),
      createdAt = Instant.now(),
    )
    userRepository.save(original)

    val updated = original.copy(passwordHash = "updated-hash", roles = setOf(UserRole.ADMIN))
    userRepository.save(updated)

    val found = userRepository.findByUsername(username)!!
    assertThat(found.passwordHash).isEqualTo("updated-hash")
    assertThat(found.roles).containsExactly(UserRole.ADMIN)
  }

  @Test
  fun `findAll returns all users`() {
    val username1 = "test-${UUID.randomUUID()}"
    val username2 = "test-${UUID.randomUUID()}"
    userRepository.save(User(username1, "hash1", setOf(UserRole.USER), Instant.now()))
    userRepository.save(User(username2, "hash2", setOf(UserRole.DEVELOPER), Instant.now()))

    val all = userRepository.findAll().map { it.username }
    assertThat(all).contains(username1, username2)
  }
}
