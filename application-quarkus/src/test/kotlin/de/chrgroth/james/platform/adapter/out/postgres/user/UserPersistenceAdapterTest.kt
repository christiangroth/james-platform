package de.chrgroth.james.platform.adapter.out.postgres.user

import de.chrgroth.james.expectSuccess
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.UserId
import de.chrgroth.james.platform.domain.user.UserRole
import de.chrgroth.james.platform.domain.user.UserStatus
import de.chrgroth.james.platform.domain.user.port.out.UserPersistencePort
import io.quarkus.test.junit.QuarkusTest
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@QuarkusTest
class UserPersistenceAdapterTest {

  @Inject
  private lateinit var persistence: UserPersistencePort

  @Test
  fun `ensure entity lifecycle`() {
    persistence.all().expectSuccess().let {
      assertThat(it).hasSize(1) // automatic admin user
    }

    val initialUser = createTestUser()
    persistence.create(initialUser).expectSuccess()

    persistence.all().expectSuccess().let {
      assertThat(it).hasSize(2)
      assertThat(it).contains(initialUser)
    }
    persistence.byId(initialUser.id).expectSuccess().let {
      assertThat(it).isEqualTo(initialUser)
    }
    persistence.byUsername(initialUser.username).expectSuccess().let {
      assertThat(it).isEqualTo(initialUser)
    }

    val updatedUser = initialUser.deactivate("deactivated for tests").expectSuccess()
    persistence.update(updatedUser).expectSuccess()
    persistence.byId(updatedUser.id).expectSuccess().let {
      assertThat(it).isNotNull
      assertThat(it!!.status).isEqualTo(UserStatus.INACTIVE)
      assertThat(it.statusReason).isEqualTo("deactivated for tests")
    }

    persistence.delete(updatedUser.id).expectSuccess()
    persistence.all().expectSuccess().let {
      assertThat(it).hasSize(1)
    }
  }

  @Test
  fun `find non existing user`() {
    persistence.byId(UserId()).expectSuccess().let {
      assertThat(it).isNull()
    }
    persistence.byUsername("non-existent-username").expectSuccess().let {
      assertThat(it).isNull()
    }
  }

  @Test
  fun `update non persistent user`() {
    createTestUser().also { testUser ->
      persistence.update(testUser).expectSuccess()
      persistence.byId(testUser.id).expectSuccess().also {
        assertThat(it).isNull()
      }
    }
  }

  @Test
  fun `delete non existing user`() {
    persistence.delete(UserId()).expectSuccess()
  }

  private fun createTestUser() = User(
    username = "test-user",
    passwordHash = "password-hash",
    roles = setOf(UserRole.USER, UserRole.DEVELOPER)
  ).expectSuccess()
}
