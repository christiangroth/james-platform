package de.chrgroth.james.platform.adapter.out.postgres.user

import arrow.core.ValidatedNel
import arrow.core.invalidNel
import arrow.core.valid
import de.chrgroth.james.DomainError
import de.chrgroth.james.platform.domain.user.PasswordStatus
import de.chrgroth.james.platform.domain.user.User
import de.chrgroth.james.platform.domain.user.UserDomainErrorCodes
import de.chrgroth.james.platform.domain.user.UserId
import de.chrgroth.james.platform.domain.user.UserRole
import de.chrgroth.james.platform.domain.user.UserStatus
import de.chrgroth.james.platform.domain.user.port.`in`.DomainUserEvents
import de.chrgroth.james.platform.domain.user.port.`in`.EVENT_TOPIC_TO_DOMAIN_USER
import de.chrgroth.james.platform.domain.user.port.out.UserPersistencePort
import io.quarkus.runtime.StartupEvent
import io.vertx.core.eventbus.EventBus
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityExistsException
import jakarta.persistence.EntityManager
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import mu.KLogging
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

// TODO check caching
// TODO check Hibernate Envers
// TODO https://quarkus.io/guides/hibernate-orm#multitenancy check for user data entities later on
// TODO https://quarkus.io/guides/hibernate-orm#json_xml_serialization_deserialization might be useful for user type definitions and apps

private const val U_SHORT_MIN: Long = 0
private const val U_SHORT_MAX: Long = 65535

// TODO Hibernate sucks for entity definitions
@Entity
@Table(name = "users", schema = "user_domain")
@Suppress("LongParameterList")
open class UserEntity(

  @Id
  @Column(unique = true, nullable = false, name = "id")
  private val id: String,

  @Column(nullable = false, name = "username")
  private val username: String,

  @Column(nullable = false, name = "password_hash")
  private val passwordHash: String,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, name = "password_status")
  private val passwordStatus: PasswordStatus,

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, name = "roles")
  private val roles: Set<UserRole>,

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, name = "status")
  private val status: UserStatus,

  @Column(nullable = true, name = "status_reason")
  private val statusReason: String?,

  @Column(nullable = false, name = "deactivation_counter")
  @get:Min(U_SHORT_MIN)
  @get:Max(U_SHORT_MAX)
  private val deactivationCounter: Int,
) {
  constructor() : this(
    id = "",
    username = "",
    passwordHash = "",
    passwordStatus = PasswordStatus.ONE_TIME,
    roles = emptySet(),
    status = UserStatus.INACTIVE,
    statusReason = null,
    deactivationCounter = 0
  )

  constructor(user: User) : this(
    user.id.value,
    user.username,
    user.passwordHash,
    user.passwordStatus,
    user.roles,
    user.status,
    user.statusReason,
    user.deactivationCounter.toInt(),
  )

  fun toDomain(): User =
    User.fromEntity(
      id = UserId(id),
      username = username,
      passwordHash = passwordHash,
      passwordStatus = passwordStatus,
      roles = roles,
      status = status,
      statusReason = statusReason,
      deactivationCounter = deactivationCounter.toUShort()
    )
}

@ApplicationScoped
@Suppress("Unused")
class UserPersistenceAdapter : UserPersistencePort {

  @Inject
  private lateinit var eventBus: EventBus

  @Inject
  lateinit var entityManager: EntityManager

  @Suppress("Unused")
  fun startup(@Observes @Suppress("UnusedParameter") event: StartupEvent) {
    logger.info { "UserDatabase created." }
    eventBus.publish(EVENT_TOPIC_TO_DOMAIN_USER, DomainUserEvents.PersistenceInitialized)
  }

  override fun byId(id: UserId): ValidatedNel<DomainError, User?> = runCatching {
    entityManager.find(UserEntity::class.java, id.value)?.toDomain()
  }.fold(
    onSuccess = { it.valid() },
    onFailure = { DomainError(code = UserDomainErrorCodes.USER_QUERY_FAILED, errorMessage = it.message).invalidNel() }
  )

  override fun byUsername(username: String): ValidatedNel<DomainError, User?> = runCatching {
    entityManager.createQuery(
      "SELECT u FROM UserEntity u WHERE u.username = :username",
      UserEntity::class.java
    )
      .setParameter("username", username)
      .resultList
      .firstOrNull()
      ?.toDomain()
  }.fold(
    onSuccess = { it.valid() },
    onFailure = { DomainError(code = UserDomainErrorCodes.USER_QUERY_FAILED, errorMessage = it.message).invalidNel() }
  )

  override fun all(): ValidatedNel<DomainError, Set<User>> = runCatching {
    entityManager.createQuery("SELECT u FROM UserEntity u", UserEntity::class.java)
      .resultList
      .map { it.toDomain() }
      .toSet()
  }.fold(
    onSuccess = { it.valid() },
    onFailure = { DomainError(code = UserDomainErrorCodes.USER_QUERY_FAILED, errorMessage = it.message).invalidNel() }
  )

  override fun create(user: User): ValidatedNel<DomainError, Unit> = runCatching {
    entityManager.persist(UserEntity(user))
  }.fold(
    onSuccess = { Unit.valid() },
    onFailure = {
      if (it is EntityExistsException) {
        DomainError(
          code = UserDomainErrorCodes.REGISTRATION_USERNAME_EXISTS,
          errorMessage = null
        )
      } else {
        DomainError(code = UserDomainErrorCodes.USER_QUERY_FAILED, errorMessage = it.message)
      }.invalidNel()
    }
  )

  override fun update(user: User): ValidatedNel<DomainError, Unit> = runCatching {
    val persistent = entityManager.find(UserEntity::class.java, user.id.value)
    if (persistent != null) {
      entityManager.merge(UserEntity(user))
      Unit
    } else {
      DomainError(code = UserDomainErrorCodes.USER_UNKNOWN, errorMessage = null).invalidNel()
    }
  }.fold(
    onSuccess = { Unit.valid() },
    onFailure = { DomainError(code = UserDomainErrorCodes.USER_QUERY_FAILED, errorMessage = it.message).invalidNel() }
  )

  override fun delete(id: UserId): ValidatedNel<DomainError, Unit> = runCatching {
    val persistent = entityManager.find(UserEntity::class.java, id.value)
    if (persistent != null) {
      entityManager.remove(persistent)
      Unit
    } else {
      DomainError(code = UserDomainErrorCodes.USER_UNKNOWN, errorMessage = null).invalidNel()
    }
  }.fold(
    onSuccess = { Unit.valid() },
    onFailure = { DomainError(code = UserDomainErrorCodes.USER_QUERY_FAILED, errorMessage = it.message).invalidNel() }
  )

  companion object : KLogging()
}
