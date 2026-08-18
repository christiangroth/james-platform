package de.chrgroth.james.platform.domain.user

import de.chrgroth.james.platform.domain.error.UserAdminError
import de.chrgroth.james.platform.domain.model.app.AppId
import de.chrgroth.james.platform.domain.model.app.InstalledApp
import de.chrgroth.james.platform.domain.model.app.InstalledAppId
import de.chrgroth.james.platform.domain.model.app.VersionNumber
import de.chrgroth.james.platform.domain.model.user.User
import de.chrgroth.james.platform.domain.model.user.UserId
import de.chrgroth.james.platform.domain.model.user.UserRole
import de.chrgroth.james.platform.domain.model.user.Username
import de.chrgroth.james.platform.domain.outbox.DomainOutboxEvent
import de.chrgroth.james.platform.domain.port.out.app.AppDataRepositoryPort
import de.chrgroth.james.platform.domain.port.out.app.InstalledAppRepositoryPort
import de.chrgroth.james.platform.domain.port.out.infra.OutboxPort
import de.chrgroth.james.platform.domain.port.out.user.UserRepositoryPort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AdminUserManagementServiceTests {

  private val userRepository: UserRepositoryPort = mockk()
  private val installedAppRepository: InstalledAppRepositoryPort = mockk()
  private val appDataRepository: AppDataRepositoryPort = mockk()
  private val outbox: OutboxPort = mockk()
  private val service = AdminUserManagementService(userRepository, installedAppRepository, appDataRepository, outbox)

  private val adminUser = User(
    id = UserId(UUID.randomUUID().toString()),
    username = Username("admin"),
    passwordHash = LoginService.hashPassword("password"),
    roles = setOf(UserRole.ADMIN),
    createdAt = Instant.now(),
  )

  private val regularUser = User(
    id = UserId(UUID.randomUUID().toString()),
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
      id = UserId(UUID.randomUUID().toString()),
      username = Username("user2"),
      passwordHash = LoginService.hashPassword("password"),
      roles = setOf(UserRole.USER),
      createdAt = Instant.now(),
    )
    every { userRepository.findByUsername(Username("user2")) } returns userWithNoAdmin
    every { userRepository.findAll() } returns listOf(userWithNoAdmin, regularUser)
    justRun { userRepository.save(any()) }

    val result = service.setRoles("user2", setOf(UserRole.ADMIN), "admin")

    assertThat(result.isRight()).isTrue()
  }

  @Test
  fun `setRoles succeeds when updating roles of existing admin user`() {
    every { userRepository.findByUsername(Username("admin")) } returns adminUser
    justRun { userRepository.save(any()) }

    val result = service.setRoles("admin", setOf(UserRole.ADMIN), "admin")

    assertThat(result.isRight()).isTrue()
  }

  // region deleteUser

  @Test
  fun `deleteUser enqueues a DeleteUser outbox event`() {
    every { userRepository.findByUsername(Username("user")) } returns regularUser
    justRun { outbox.enqueue(any()) }

    val result = service.deleteUser("user", "admin")

    assertThat(result.isRight()).isTrue()
    verify { outbox.enqueue(DomainOutboxEvent.DeleteUser(userId = regularUser.id.value, username = "user")) }
    verify(exactly = 0) { userRepository.delete(any()) }
  }

  @Test
  fun `deleteUser fails when deleting self`() {
    val result = service.deleteUser("admin", "admin")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAdminError.CANNOT_DELETE_SELF)
  }

  @Test
  fun `deleteUser fails when user not found`() {
    every { userRepository.findByUsername(Username("unknown")) } returns null

    val result = service.deleteUser("unknown", "admin")

    assertThat(result.isLeft()).isTrue()
    assertThat(result.leftOrNull()).isEqualTo(UserAdminError.USER_NOT_FOUND)
  }

  // endregion

  // region handle(DeleteUser)

  @Test
  fun `handle DeleteUser cascades installed app and app data deletion and deletes the user`() {
    val installedApp1 = InstalledApp(
      id = InstalledAppId("installed-1"),
      userId = regularUser.id.value,
      appId = AppId("app-1"),
      installedVersionNumber = VersionNumber("1.0.0"),
      installedAt = Instant.now(),
    )
    val installedApp2 = installedApp1.copy(id = InstalledAppId("installed-2"), appId = AppId("app-2"))
    every { userRepository.findById(regularUser.id) } returns regularUser
    every { installedAppRepository.findAllByUserId(regularUser.id.value) } returns listOf(installedApp1, installedApp2)
    justRun { appDataRepository.deleteAllByInstalledAppId(any()) }
    justRun { installedAppRepository.delete(any()) }
    justRun { userRepository.delete(any()) }

    val result = service.handle(DomainOutboxEvent.DeleteUser(userId = regularUser.id.value, username = "user"))

    assertThat(result.isRight()).isTrue()
    verify { appDataRepository.deleteAllByInstalledAppId(InstalledAppId("installed-1")) }
    verify { appDataRepository.deleteAllByInstalledAppId(InstalledAppId("installed-2")) }
    verify { installedAppRepository.delete(InstalledAppId("installed-1")) }
    verify { installedAppRepository.delete(InstalledAppId("installed-2")) }
    verify { userRepository.delete(regularUser.id) }
  }

  @Test
  fun `handle DeleteUser succeeds when user has no installed apps`() {
    every { userRepository.findById(regularUser.id) } returns regularUser
    every { installedAppRepository.findAllByUserId(regularUser.id.value) } returns emptyList()
    justRun { userRepository.delete(any()) }

    val result = service.handle(DomainOutboxEvent.DeleteUser(userId = regularUser.id.value, username = "user"))

    assertThat(result.isRight()).isTrue()
    verify { userRepository.delete(regularUser.id) }
  }

  @Test
  fun `handle DeleteUser treats an already deleted user as already processed`() {
    every { userRepository.findById(regularUser.id) } returns null

    val result = service.handle(DomainOutboxEvent.DeleteUser(userId = regularUser.id.value, username = "user"))

    assertThat(result.isRight()).isTrue()
    verify(exactly = 0) { userRepository.delete(any()) }
  }

  // endregion
}
